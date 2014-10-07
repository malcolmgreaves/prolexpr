package prolexpr.classify

import java.util.Properties
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import edu.stanford.nlp.ling.CoreAnnotations._
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation
import edu.stanford.nlp.dcoref.CorefChain
import java.util
import scala.collection.JavaConverters._
import java.io._
import akka.actor._
import prolexpr.classify.TextProcessStructs.Document
import prolexpr.classify.Sync.WaitGroup
import scala.collection.mutable.ListBuffer

/**
 * y
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object MainCoreferenceResolution {
  val log = mkLogger(this.getClass.toString)

  def main(args: Array[String]) {
    if (args.length < 3) {
      throw new IllegalArgumentException("Incorrect arguments, need:\n" +
        "configuration file\n" +
        "output directory\n" +
        "\"input\" (input is a space-delim list of files and directories for processing)\n")
    }

    log.info("Configuraiton: " + args(0))
    log.info("Output:        " + args(1))
    log.info("Input [" + args.slice(2, args.length).size + "]:     " + args.slice(2, args.length).mkString(","))

    val output = new File(args(1))
    if (!output.mkdirs()) {
      throw new IllegalStateException("FATAL Failed to make output directory!! Does it already exist?")
    }

    val inputs = args.slice(2, args.length).map(f => {
      val inputFi = new File(f)
      if (!inputFi.exists()) {
        throw new IllegalStateException("FATAL input does not exist!: " + f)
      }
      inputFi
    })

    val props = new Properties()
    props.load(new FileInputStream(args(0)))

    // initialize actor system for concurrent coref. res.
    val nThreads = props.getProperty("nthreads", "1").toInt
    log.info("# threads for parallel Coreference Resolution: " + nThreads)
    val docSentLimit = props.getProperty("docSentLimit", "50").toInt
    log.info("Maximum # of sentences per document (sliding window for larger): " + docSentLimit)
    val system = ActorSystem("CorefRes")
    val master = new Master(system, nThreads, docSentLimit)

    // process all files from each input argument
    var i = 0
    for (input <- inputs) {
      i += 1
      log.info("processing input [" + i + " / " + inputs.size + "]: " + input)

      val startTime = System.nanoTime()

      // run the coreference asynchronously
      // # of parallel jobs is limited by nThreads
      master(DoCoref(input, output))

      val endTime = System.nanoTime()
      log.info("processed " + input + " in " + (endTime - startTime) + " ns")
    }

    log.info("Completed all work, shutting down system...")
    system.shutdown()
    log.info("done")
  }

  //
  // Code to interface with Stanford CoreNLP pipeline and coreference
  // resolution.
  //

  /**
   * mkNLPPipeline returns a StanfordCoreNLP object with these properties:
   * "annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref"
   * which are necessary to perform coreference resolution
   */
  def mkNLPPipeline() = {
    val props = new Properties()
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref")
    new StanfordCoreNLP(props)
  }

  /*
    mkStrAnnotator takes in a CoreNLP pipeline constructor, returns a function
    that annotates a piece of text.
   */
  def mkStrAnnotator(mk: () => StanfordCoreNLP = mkNLPPipeline): (String) => Annotation = {
    // make Stanford CoreNLP pipeline (i.e. do you want coref?
    // kust POS tagging? just NER? all of 'em?
    val pipeline = mk()

    // func annotates text
    (text: String) => {
      val document = new Annotation(text)
      pipeline.annotate(document)
      document
    }: Annotation
  }


  /*
    coref returns the corefrence resolution information from an annotation.
   */
  def coref(a: Annotation): util.Map[Integer, CorefChain] = {
    a.get(classOf[CorefChainAnnotation])
  }

  /*
    taggedText extracts the token text, POS tag, and NE tag froch each word
    in (one or more) annotated sentences.
   */
  def taggedText(a: Annotation): Seq[Seq[TextProcessStructs.Token]] = {
    a.get(classOf[SentencesAnnotation]).asScala.map((cm) => {
      cm.get(classOf[TokensAnnotation]).asScala.map((cl) => {
        new TextProcessStructs.Token(
          cl.get(classOf[TextAnnotation]),
          cl.get(classOf[PartOfSpeechAnnotation]),
          cl.get(classOf[NamedEntityTagAnnotation])
        )
      })
    })
  }

  /**
   * UNIMPLEMENTED !!
   * THROWS RUNTIME EXCEPTION
   */
  def mkMaltParsedNLPPipeline() = throw new RuntimeException()

  //
  // Actual corefrence-resolution concurrent processing code.
  // Uses Akka actors.
  //

  sealed trait Msg

  // Document to process and output writer
  case class Work(docs: Seq[Document], w: BufferedWriter) extends Msg

  // Result of processing document and output writer
  case class Result(corefFmtStr: String, w: BufferedWriter) extends Msg

  /*
    Worker is an actor that performs coref res on a document (wrapped in
    a Work message) and "returns" the coreference output (wrapped as a Result
    message).
   */
  class Worker(name: String, pipeln: StanfordCoreNLP) extends Actor {

    val fmtWord = (word: String) => "w_" + word.replaceAll(" ", "_")
    val fmtDocID = (docID: String) => "id_" + docID

    override def receive: Actor.Receive = {

      case Work(docs, w) => {
        val (sb, interSB) = (new StringBuilder, new StringBuilder)

        if (log.isDebugEnabled) {
          interSB.append("Worker (" + name + ") received [" + docs.size + " documents] work:\t")
          docs.foreach(d => sb.append(d.docID + " [" + d.sentences.size + " sentences],  "))
          log.debug(sb.toString())
          sb.setLength(0)
        } else if (log.isInfoEnabled) {
          log.info("Worker (" + name + ") received [" + docs.size + " documents]")
        }

        docs.foreach(d => {
          val formattedDocID = fmtDocID(d.docID)

          val annotation = new Annotation(d.toText)
          pipeln.annotate(annotation)
          val corefd = coref(annotation)

          // iterate over all referent lists that have at least 2 members
          // AND format the co-reference metion pairs

          val corefFmtText = corefd.entrySet().asScala
            .filter((e: util.Map.Entry[Integer, CorefChain]) => e.getValue.getMentionsInTextualOrder.size() > 1)
            .map((e: util.Map.Entry[Integer, CorefChain]) => {
            // format each list of coreferent mention pairs
            val corefMentions = e.getValue.getMentionsInTextualOrder.asScala
            interSB.setLength(0)

            for (ii <- 0 until corefMentions.length;
                 jj <- 0 until corefMentions.length) {
              if (ii != jj) {
                val (mentionII, mentionJJ) = (corefMentions(ii), corefMentions(jj))

                // FORMAT word,docsentID,word,docsentID
                sb.append("stanfordCoreference\t" +
                  fmtWord(mentionII.mentionSpan) + "\t" + formattedDocID + ".S" + (mentionII.sentNum - 1) + "\t" +
                  fmtWord(mentionJJ.mentionSpan) + "\t" + formattedDocID + ".S" + (mentionJJ.sentNum - 1) + "\n")
              }
            }
            interSB.toString()
          }).mkString("")

          sb.append(corefFmtText)
        })


        val corefFmtText = sb.toString()
        if (log.isDebugEnabled) {
          interSB.setLength(0)
          interSB.append("Worker (" + name + ") processed [" + docs.size + " documents]:\t")
          docs.foreach(d => interSB.append(d.docID + " [" + d.sentences.size + " sentences],  "))
          log.debug(interSB.toString())
        } else if (log.isInfoEnabled) {
          log.info("Worker (" + name + ") processed [" + docs.size + " documents]")
        }

        sender ! Result(corefFmtText, w)
      }
    }
  }


  // Message to perform corference resolution on an input {file,directory},
  // writing output to the specified directory.
  case class DoCoref(input: File, output: File) extends Msg

  /**
   * Master wraps the concurrent coref. res. processing. It is threadsafe.
   */
  class Master(system: ActorSystem, nWorkers: Int = 1, docSentLimit: Int = 50) {

    // CoreNLP object is threadsafe
    //    val pipeln = mkNLPPipeline()

    // schedules work in a RoundRobin fashion
    //    val workerRouter = system.actorOf(Props[Actor](new Worker())
    //      .withRouter(RoundRobinRouter(nWorkers)),
    //      name = "WorkerRouter")
    val workers = (0 until nWorkers).map(id => {
      val pipeln = mkNLPPipeline()
      system.actorOf(Props[Worker](new Worker(id.toString, pipeln)))
    })

    // shared, thread safe synchronization point -- limits number of
    // current processes
    val wg = new WaitGroup()

    // actor that wraps the work scheduler --
    //    passes work requests to scheduler
    //    receives results and synchronizes output writing using WaitGroup
    val scheduler = system.actorOf(Props[Actor](new Actor() {

      // worker index
      var wi = 0

      def receive: Actor.Receive = {

        case Result(a, w) => {
          wg.done()
          w.write(a)
        }
        case Work(doc, w) => {
          workers(wi % nWorkers) ! Work(doc, w)
          wi += 1
        }
      }
    }))

    def apply(m: Msg) = m match {

      case DoCoref(input, output) => {
        val o = new File(output, input.getName)
        val w = new BufferedWriter(new FileWriter(o))
        val docIter = TextProcessStructs.loadDocuments(input)

        val loadBalDocWork = new Iterator[Work] {

          def hasNext: Boolean = work != null

          def next(): Work = {
            val wrk = new Work(work.docs, work.w)
            work = advance()
            wrk
          }

          var work: Work = advance()

          def advance(): Work = {
            val docs = new ListBuffer[Document]()
            val nSentences = () => docs.map(d => d.sentences.size).sum

            while (nSentences() < docSentLimit && docIter.hasNext) {
              val doc = docIter.next()
              if (doc.sentences.size == 0) {
                log.warn("ZERO LENGTH DOCUMENT: " + doc.toString.trim)
              } else if (doc.sentences.size == 1) {
                if (log.isDebugEnabled)
                  log.debug("Document: " + doc.docID + " has only 1 sentence, not using for coref. res.")
              } else if (doc.sentences.size > docSentLimit) {
                log.info("Skipping document: " + doc.docID + " because it is too long (" +
                  doc.sentences.size + " sentences when max is " + docSentLimit + ")")
              } else {
                docs.append(doc)
              }
            }

            if (docs.size > 0) {
              Work(docs, w)
            } else {
              null
            }
          }
        }

        val check = 500
        var (d, i) = (0, 0)

        val sb = new StringBuilder

        loadBalDocWork.foreach(w => {
          i += 1
          d += w.docs.map(d => d.sentences.size).sum

          if (log.isInfoEnabled) {
            sb.append("Master submitted [" + w.docs.size + " documents]\t")
            w.docs.foreach(doc => sb.append(doc.docID + " [" + doc.sentences.size + " sentences],  "))
            log.info(sb.toString())
            sb.setLength(0)
          }


          scheduler ! w
          wg.add()
          wg.waitUntilSizeIsLessThan(nWorkers)

          if (i % check == 0) log.info("STATUS\tsubmitted ** " + d + " ** documents for processing thus far")
        })

        wg.waitUntilCompletion()
        w.close()
        log.info("done, processed " + d + " documents, wrote to: " + o)
      }

      case _ => log.error("skipping unknown message: " + m)
    }


  }

}