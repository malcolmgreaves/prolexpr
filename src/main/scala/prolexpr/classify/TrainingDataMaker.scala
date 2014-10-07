package prolexpr.classify

import nak.data.{Example, Featurizer}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import java.io._
import java.util.Properties
import scala.collection.mutable
import scala.sys.process.{ProcessBuilder, Process, ProcessIO}
import prolexpr.classify.TextFeatures.CompleteSlotfillerFeaturizer
import scala.Predef._
import prolexpr.classify.TextProcessStructs.{NETag, DocSentID, Token}
import scala.Some
import nak.data.FeatureObservation
import prolexpr.classify.CandGen.{CandidateGenerator, Slotfill}

/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object TrainingDataMaker {
  val log = mkLogger(this.getClass.getName.replace("$", ""))

  def main(args: Array[String]) = {
    if (args.length < 3) {
      log.fatal("Incorrect arguments, need:\n\t[0] output dir\n\t[1] .properties config\n\t[2] input dir")
      exit()
    }

    val (outputDir, propsFi, inputDir) = (args(0), args(1), args(2))
    log.info("output dir:              " + outputDir)
    log.info("properties:              " + propsFi)
    log.info("input dir:               " + inputDir)

    val props = new Properties()
    props.load(new FileInputStream(propsFi))

    val nofeaturegen = props.getProperty("nofeaturegen", "false").toBoolean
    if (nofeaturegen) {
      log.info("\"nofeaturegen=true\" " +
        "Skipping feature generation stage, assuming given output dir is sufficent for eval...")
    }

    val bigExec = props.getProperty("bigexec", "false").toBoolean

    if (!nofeaturegen) {
      if (new File(outputDir).exists()) {
        log.fatal("output dir already exists!")
        exit()
      }
    }

    val downsampleNotReleated = props.getProperty("downsampleNotReleated", "false").toBoolean

    val folds = props.getProperty("folds", "3").toInt
    if (folds < 1) {
      log.fatal("# cross validation folds (\"folds\") must be positive")
      exit()
    }

    // find candidates and generate features for them

    if (!nofeaturegen) {

      val answers = props.getProperty("proppr.answers", "")
      val alsoUseSentBySentCandgen = props.getProperty("alsoUseSentBySentCandgen", "true").toBoolean
      val candidateMaker = if (answers.size > 0) {
        log.info("loading ProPPR coref candidates for candidate generation: " + answers)
        if (alsoUseSentBySentCandgen) {
          log.info("also using standard within-doc, sentence-by-sentence candidate generation")
        }
        CandGen.candgenProPPRRule(CandGen.loadProPPRCandsPartFns(
          new BufferedReader(new FileReader(answers))), alsoUseSentBySentCandgen)
      } else {
        log.info("only using within-sentence candidate generation")
        CandGen.sentenceBySentenceCandidateMaker _
      }


      val labelOutFmt = IO.selectLabelOutputFormat(props.getProperty("outputformat", "SVMLightLabelOutputFormat"))
      log.info("Label output format:     " + labelOutFmt)
      val includePair = labelOutFmt.getClass == classOf[IO.PairOutputFormat]

      val ngram = props.getProperty("ngram", "2").toInt
      val skip = props.getProperty("skip", "4").toInt
      if (ngram < 1 || skip < 0) {
        log.fatal("ngram must be > 0 and skip must be non-negative")
        exit()
      }
      log.info("FEATUREIZING configuration:\n\t" +
        "ngram size (for skip + Left and Right word features):  " + ngram + "\n\t" +
        "skip ngram size (for inner features):                  " + skip)

      // load labels
      val labelsFi = props.getProperty("labels")
      val (labels, all) = Label.load(new File(labelsFi))
      all.foreach(r => log.info("loaded relation: " + r))

      //    defer making output dir until configuration loading goes according to plan
      if (!new File(outputDir).mkdirs()) {
        log.fatal("could not make output dir")
        exit()
      }

      //    featureize data and create labeled training data
      log.info("TRAINING DATA MAKER configuration:\n\t" +
        "filtering and down-sampling NOT_RELATED examples?:     " + downsampleNotReleated)

      val fmaker = new LabeledExampleFeatuerizer(
        new Context(labels, TextProcessStructs._maltParseFormatExtract, downsampleNotReleated, includePair),
        new CompleteSlotfillerFeaturizer(ngram, skip),
        candidateMaker
      )
      val featurize = fmaker.tc.downsampleNotReleated match {
        case true => fmaker.make _
        case false => fmaker.stream _
      }

      labelOutFmt.write(featurize(new File(inputDir)), outputDir)
    }

    // perform stratified cross validation on all generated and featurized candidates

    if (folds == 1) {
      log.info("not writing stratified cross-validation files since folds == 1")
    } else {
      log.info("writing stratified " + folds + " fold cross-validation files")
      log.info("large maching execution activation is " + bigExec)

      val toLog = (stream: InputStream) => scala.io.Source.fromInputStream(stream).getLines().foreach(log.info)
      val pio = new ProcessIO(_ => (), _ => (), toLog)

      val posCosts = Array(6.0, 10.0, 14.0)

      // TODO make action programmable from a cmd line option (and probabaly config file)
      val action = (label: String, relationWorkingDir: File, foldFis: IndexedSeq[File]) => {

        log.info("running multi_svm for relation: " + label + " folds:\n\t" + foldFis.mkString("\n\t"))

        // make a per +-post config working dir:
        // the learning & evaluation routine need to have space ot make output files
        val (workingDirs, multiSVMProcs) = posCosts.map((posCost) => {
          val working = new File(relationWorkingDir, "j=" + posCost + "_work")

          // run svm_light training (use go multithreaded wrapper)
          (working, Process("./multi_svm -labeled=" + foldFis.mkString(",") +
            " -working=" + working + " -threads=" + foldFis.length +
            " -j " + posCost))
        }: (File, ProcessBuilder)).unzip

        val exitcodes = bigExec match {
          // start each process async., then wait on all & get exit codes
          case true => multiSVMProcs.map((p: ProcessBuilder) => p.run(pio)).map(p => p.exitValue())
          // execute, wait for completion, and get exit code of each process sequentially
          case false => multiSVMProcs.map((p: ProcessBuilder) => p.run(pio).exitValue())
        }

        var ii = 0
        exitcodes.foreach(ecode => {
          if (ecode != 0) {
            log.error("multi_svm ended with error code: " + ecode + " working dir: " + workingDirs(ii))
          }
          ii += 1
        })

        workingDirs.foreach(workingDir => {
          // delete training files
          val workDirFiles = workingDir.listFiles()
          if (workDirFiles == null) {
            log.error("working dir has ZERO files: " + workingDir + "\nis it a dir?: " + workingDir.isDirectory)
          } else {
            workDirFiles.foreach((fi) => {
              if (fi != null && fi.getName.startsWith("training_for_test_")) {
                if (!fi.delete()) {
                  log.error("failed to delete intermediate training file: " + fi)
                }
              }
            })
          }
        })

        // delete fold files -- no longer need them
        val nFailures = foldFis.foreach(fi => {
          if (fi != null) {
            if (!fi.delete()) {
              log.error("failed to delete fold file: " + fi)
            }
          }
        })
      }

      CrossValidation.makeStratified(new File(outputDir), folds, action)

      log.info("done")
    }
  }

  object CrossValidation {

    def makeStratified(outputAsInputDir: File, // directory, contains all input files that we use
                       // hold many folds? the # of models to validate
                       kFolds: Int = 3,
                       // custom action to run on the fold files immediately
                       // afeter creation and before creation of the next label's files.
                       // by deafult, action is identity function
                       action: (String, File, IndexedSeq[File]) => Unit = (_, _, _) => {},
                       // which files to accept from the input directory?
                       fileSelector: (File) => Boolean = (s) => s.getName().endsWith("_data")) = {
      if (!outputAsInputDir.exists()) {
        throw new IOException("input: " + outputAsInputDir + " does not exist.")
      }
      if (outputAsInputDir.isFile()) {
        throw new IOException("need directory, not file: " + outputAsInputDir)
      }

      // group files by positive labeled and NOT_RELATED
      var notRelatedFi: File = null
      val labelFiles = new ListBuffer[File]()
      outputAsInputDir.listFiles.toIterator
        .filter((fi: File) => fi.isFile && fileSelector(fi))
        .foreach((fi: File) => {
        //
        if (fi.getName.contains(Label.NOT_RELATED)) {
          notRelatedFi = fi
        } else {
          labelFiles.append(fi)
        }
      })
      // iterates through NOT_RELATED examples

      val notRealtedLines = IO.lines(notRelatedFi).toSeq

      // make folds per positive labeled
      for (positiveLabeledFi <- labelFiles) {

        val relWorkDir = new File(positiveLabeledFi + "_wdir")
        if (!relWorkDir.mkdirs()) {
          log.error("Could not make working dir (" + positiveLabeledFi.getName + "): " + relWorkDir)
        } else {
          // k folds
          val foldFis = (0 until kFolds).map(k => new File(relWorkDir, "fold_" + k))
          // a writer for each fold
          val writers = foldFis.map(f => new BufferedWriter(new FileWriter(f)))

          // first, write the NOT_RELATED examples to each fold
          var li = 0
          for (notRelatedLine <- notRealtedLines) {
            writers(li % kFolds).write(notRelatedLine)
            writers(li % kFolds).write("\n")
            li += 1
          }

          // now write the positive labled examples
          li = 0
          for (positiveLabeledLine <- IO.lines(positiveLabeledFi)) {
            writers(li % kFolds).write(positiveLabeledLine)
            writers(li % kFolds).write("\n")
            li += 1
          }

          // close out and move on to next positive file
          writers.foreach((w) => w.close())

          // do custom action on all of these fold files, if applicable
          action(positiveLabeledFi.getName, relWorkDir, foldFis)
        }
      }
    }
  }

  object Label {
    val NOT_RELATED = "NOT_RELATED"

    // name -> response -> (+,-) relations
    type er = mutable.HashMap[String, mutable.HashMap[String, Multi]]

    class Multi() {
      val positive = new mutable.HashSet[String]()
      val negative = new mutable.HashSet[String]()

      override def toString: String = {
        "Positive: " + positive + "\nNegative: " + negative
      }
    }

    def load(fi: File): (er, Seq[String]) = {
      val name2responseRels = new mutable.HashMap[String, mutable.HashMap[String, Multi]]()

      // read through the file and populate the word to label mapping
      val labels = new mutable.HashSet[String]()
      IO.lines(fi).foreach((line) => {
        val bits = line.split("\\t")
        val relation = bits(0).intern()
        val posorneg = bits(1)
        val name = bits(2)
        val response = bits(3)
        if (!name2responseRels.contains(name)) {
          name2responseRels.put(name, new mutable.HashMap[String, Multi]())
        }
        if (!name2responseRels.get(name).get.contains(response)) {
          name2responseRels.get(name).get.put(response, new Multi())
        }
        if (posorneg == "+") {
          name2responseRels.get(name).get.get(response).get.positive.add(relation)
        } else {
          name2responseRels.get(name).get.get(response).get.negative.add(relation)
        }
        labels.add(relation)
      })

      (name2responseRels, labels.toSeq.sorted)
    }
  }

  // labeler assigns a label or NOT_RELATED to each candidate relation pair
  // (refered to as an "example")
  class Context(val labler: Label.er,
                // used in TextProcessStructs.load() -- extracts the document ID
                // sentence number from each record
                val extract: (Array[String]) => (DocSentID, NETag),
                // downsampling implies that we want to take a simple random sample of
                // the NOT_RELATED examples proportional to the # of positively labeled
                // examples
                val downsampleNotReleated: Boolean = false,
                // includePair means that we want to set the Example.id to be
                // the (query,answer) extraction pair. False means set it to ""
                val includePair: Boolean = false,
                // which tokens to ignore?. By default, ignores punctuation.
                val filter: (Token) => Boolean = (tok) => !tok.word.equals(tok.tagPOS),
                // given two chunks i and j should we consider them as a
                // candidate pair? By default, only exlcudes case when
                // the two tokens are the same.
                val indexSelector: (Int, Int) => Boolean = (i, j) => i != j) {

    def idFor(sf: Slotfill): String = {
      if (includePair) {
        sf.query + "\t" + sf.answer
      } else {
        ""
      }
    }

    type SFAcceptor = Slotfill => Boolean

    def accetableUnrelatedSF(sf: Slotfill) = // query & answer must have non Other (O) NER tag and be a noun
      TextProcessStructs.nonOther(sf.query.token.tagNER) &&
        TextProcessStructs.nonOther(sf.answer.token.tagNER) &&
        TextProcessStructs.isNoun(sf.query.token.tagPOS) &&
        TextProcessStructs.isNoun(sf.answer.token.tagPOS)

    def makeTrainingExamples(inputDir: File,
                             candidateMaker: CandidateGenerator = CandGen.sentenceBySentenceCandidateMaker,
                             acceptUnrelated: SFAcceptor = accetableUnrelatedSF) = {
      val trainingExamples = new ArrayBuffer[Example[String, Slotfill]]()
      val notRealted = new ArrayBuffer[Example[String, Slotfill]]()
      var (nNotRelated, nPositive) = (0, 0)

      // remove punctuation from tokens when computing features
      for (doc <- TextProcessStructs.loadDocuments(inputDir, extract, filter)) {

        candidateMaker(doc).foreach(sf => {

          var unrelated = false
          // labeling pair
          labler.get(sf.query.token.word) match {
            case Some(answers) => {
              answers.get(sf.answer.token.word) match {
                // positive
                case Some(labels) => {
                  labels.positive.foreach((relation) => {
                    trainingExamples.append(Example.apply(relation, sf, idFor(sf)))
                    nPositive += 1
                  })
                  // Commented code below explictly adds negative examples....but
                  // it creates a new label, which we don't like.
                  //
                  //                      labels.negative.foreach((relation) => trainingExamples.append(Example.apply(
                  //                        "NOT-" + relation, sf)))
                  //
                  // This version treats the negatives as NOT_RELATED, but this is not totally right. They
                  // are NOT-relation....At any rate adding this in makes performance drop like a rock.
                  //                      NOT_RELEATED, sf) ) )
                  //
                  // Same idea, but adds it to the not-related list for downsampling later. Bad performance.
                  //                      labels.negative.foreach((relation) => notRealted.append(Example.apply(
                  //                        NOT_RELATED, sf)))
                }
                // NOT_RELATED
                case None => unrelated = true
              }
            }
            case None => unrelated = true
          }
          if (unrelated && acceptUnrelated(sf)) {
            nNotRelated += 1
            if (downsampleNotReleated) {
              notRealted.append(Example.apply(Label.NOT_RELATED, sf, idFor(sf)))
            } else {
              trainingExamples.append(Example.apply(Label.NOT_RELATED, sf, idFor(sf)))
            }
          }
        })

        log.info(nNotRelated + " examples are " + Label.NOT_RELATED + ", " +
          nPositive + " have positive (+) labels")

        if (downsampleNotReleated) {
          val sampleprop = Math.min((trainingExamples.length.toDouble * 1.0) / notRealted.length.toDouble, 1.0)
          for (nr <- notRealted) {
            if (Math.random() < sampleprop) {
              trainingExamples.append(nr)
            }
          }
        }
        log.info("selected " + trainingExamples.length + " training examples")

      }
      trainingExamples.toSeq
    }: Seq[Example[String, Slotfill]]

    /**
     * NOTE: Does not perform down-sampling since it streams through data
     */
    def streamTrainingExamples(inputDir: File,
                               candidateMaker: CandidateGenerator = CandGen.sentenceBySentenceCandidateMaker,
                               acceptUnrelated: SFAcceptor = accetableUnrelatedSF) = {
      // remove punctuation from tokens when computing features
      for (doc <- TextProcessStructs.loadDocuments(inputDir, extract, filter)) yield {

        // each iteration returns all examples from the sentence
        val examplesFromDocument = new mutable.ArrayBuffer[Example[String, Slotfill]]()

        // construct features from the candidate pairs found in the document
        candidateMaker(doc).foreach(sf => {

          var unrelated = false
          labler.get(sf.query.token.word) match {

            case Some(answers) => {
              answers.get(sf.answer.token.word) match {

                case Some(labels) => {
                  labels.positive.foreach((relation) =>
                    examplesFromDocument.append(Example.apply(relation, sf, idFor(sf)))
                  )
                }
                case None => unrelated = true
              }
            }
            case None => unrelated = true
          }
          if (unrelated && acceptUnrelated(sf)) {
            examplesFromDocument.append(Example.apply(Label.NOT_RELATED, sf, idFor(sf)))
          }
        })


        examplesFromDocument.toSeq
      }
    }: Iterator[Seq[Example[String, Slotfill]]]
  }

  class LabeledExampleFeatuerizer(val tc: Context,
                                  val featR: Featurizer[Slotfill, String],
                                  val candidateMaker: CandidateGenerator) {

    def stream(inputDir: File) = {
      for (docExamples <- tc.streamTrainingExamples(inputDir, candidateMaker);
           example <- docExamples) yield {
        Example[String, Seq[FeatureObservation[String]]](example.label, featR.apply(example.features), example.id)
      }
    }

    def make(inputDir: File) =
      tc.makeTrainingExamples(inputDir, candidateMaker).map((ex) =>
        Example[String, Seq[FeatureObservation[String]]](ex.label, featR.apply(ex.features), ex.id)
      )
  }

}