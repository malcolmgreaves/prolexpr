package prolexpr.classify

import scala.Predef._
import java.io.File
import scala.collection.immutable.Queue
import scala.collection.mutable.ListBuffer
import scala.collection.mutable
import prolexpr.classify.IO.mkFileWalker

/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object TextProcessStructs {
  val log = mkLogger(getClass.getName)

  val stopwords = Set("the", "a", "an", "of", "in", "for", "by", "on")

  type POSTag = String
  type NETag = String

  case class Token(word: String, tagPOS: POSTag, tagNER: NETag) {
    override def toString = word + "/" + tagPOS + "/" + tagNER
  }

  case class Sentence(docsentID: DocSentID, tokens: List[Token]) {
    override def toString = docsentID + " : " + tokens

    def docID = docsentID.docID

    def sentIndex = docsentID.sentNum

    def slice(fromInclusive: Int, toExclusive: Int) = tokens.slice(fromInclusive, toExclusive)
  }

  type DocID = String

  case class DocSentID(docID: DocID, sentNum: Int) {
    override def toString = docID + sentenceIndexSym + sentNum.toString
  }


  def parseDocSentIDFrom(s: String): DocSentID = {
    val bits = s.trim().split(sentenceIndexSym)
    val d = DocSentID(bits(0), bits(1).toInt)
    d
  }

  val sentenceIndexSym = ".S"

  def extractDocID(docsentID: String): String = docsentID.substring(0, docsentID.lastIndexOf(sentenceIndexSym))

  def extractSentNum(docsentID: String): Int =
    docsentID.substring(docsentID.lastIndexOf(sentenceIndexSym) + sentenceIndexSym.size).toInt


  case class SentenceTarget(ti: Int, s: Sentence) {
    val token = s.tokens(ti)

    def docID = s.docID

    override def toString = token.toString + "[" + s.docsentID + "]"

    def slice(fromInclusive: Int, toExclusive: Int) = s.slice(fromInclusive, toExclusive)
  }

  case class Document(docID: String, sentences: List[Sentence]) {
    override def toString = "[" + docID + " | " + sentences.size + "]:\n" + sentences.mkString("\n")

    def toText = sentences.map((s: Sentence) =>
      s.tokens.map((t: Token) => t.word).mkString(" ")).mkString("\n")
  }

  def loadDocumentsWithSentenceLimit(limit: Int, topdir: File,
                                     extract: (Array[String]) => (DocSentID, NETag) = _maltParseFormatExtract,
                                     filter: (Token) => Boolean = (_) => true)(implicit codec: scala.io.Codec): Iterator[Document] = {
    val docIter = loadDocuments(topdir, extract, filter)

    new Iterator[Document] {

      var leftovers = Queue.empty[Document]

      def hasNext: Boolean = {
        leftovers.size > 0 || docIter.hasNext
      }

      def next(): Document = {
        if (leftovers.size > 0) {
          val x = leftovers.dequeue
          leftovers = x._2
          x._1 // document
        } else {
          val doc = docIter.next()
          if (doc.sentences.size < limit) {
            doc
          } else {
            (0 until doc.sentences.size - limit).foreach((start) => {
              val end = start + limit
              leftovers = leftovers.enqueue(new Document(doc.docID, doc.sentences.slice(start, end)))
            })
            next()
          }
        }
      }
    }

  }

  def loadDocuments(topdir: File, extract: (Array[String]) => (DocSentID, NETag) = _maltParseFormatExtract,
                    filter: (Token) => Boolean = (_) => true)(implicit codec: scala.io.Codec): Iterator[Document] = {

    val sentenceListFileIter = loadSentences(topdir, extract, filter)
    var lastSentenceList: List[Sentence] = List.empty[Sentence]
    var prevDocID: String = ""

    new Iterator[Document] {
      var nextDoc: Document = advance()

      def hasNext: Boolean = nextDoc != null

      def advance(): Document = {

        val sentences = new ListBuffer[Sentence]()
        if (lastSentenceList.size > 0) {
          sentences.appendAll(lastSentenceList)
          lastSentenceList = List.empty[Sentence]
        }

        while (sentenceListFileIter.hasNext) {
          val sentence = sentenceListFileIter.next()
          val docID = sentence.docID

          if (prevDocID.size != 0 && !prevDocID.equals(docID)) {
            val d = new Document(prevDocID, sentences.toList)
            lastSentenceList = List(sentence)
            prevDocID = docID
            return d
          }
          prevDocID = docID
          sentences.append(sentence)
        }
        if (sentences.size > 0) {
          new Document(prevDocID, sentences.toList)
        } else {
          null
        }
      }

      def next(): Document = {
        val d = new Document(nextDoc.docID, nextDoc.sentences)
        nextDoc = advance()
        d
      }
    }
  }

  def loadSentences(topdir: File, extract: (Array[String]) => (DocSentID, NETag) = _maltParseFormatExtract,
                    tFilter: (Token) => Boolean = (_) => true)(implicit codec: scala.io.Codec): Iterator[Sentence] = {

    val (fileWalker, nFiles) = mkFileWalker(topdir)
    val files = fileWalker.toList.sortBy(x => x.getName)
    assert(files.size == nFiles)
    val fileIters = files.map((fi) => loadSentencesIter(IO.lines(fi), extract, tFilter))

    new Iterator[Sentence] {
      var (fi, newone) = (0, true)

      def hasNext: Boolean = {
        if (fi >= nFiles) false
        else {
          if (!fileIters(fi).hasNext) {
            fi += 1
            newone = true
            hasNext
          } else {
            if (newone) {
              log.info("reading from [" + (fi + 1) + "/" + nFiles + "]: " + files(fi))
            }
            newone = false
            true
          }
        }
      }

      def next(): Sentence = fileIters(fi).next()
    }
  }


  def loadSentencesIter(lines: Iterator[String], extract: (Array[String]) => (DocSentID, NETag) = _maltParseFormatExtract,
                        tFilter: (Token) => Boolean = (_) => true)(implicit codec: scala.io.Codec): Iterator[Sentence] = {
    var prevDocsentID: DocSentID = null
    val s = new ListBuffer[Token]()

    new Iterator[Sentence] {

      var sentence: Sentence = advance()

      def hasNext: Boolean = sentence != null

      def advance(): Sentence = {
        // read in all sentences from file
        while (lines.hasNext) {
          val line = lines.next()

          if (line.length == 0 && prevDocsentID != null) {
            val sent = new Sentence(prevDocsentID, s.toList)
            s.clear()
            return sent
          }
          val bits = line.split("\\t")
          try {
            val sbits = bits(5).split(":")
            val (docsentID, ner) = extract(sbits)
            prevDocsentID = docsentID
            val tok = new Token(bits(1), bits(3), ner)

            if (tFilter(tok)) {
              s.append(tok)
            }
          } catch {
            case e: Exception => {
              log.error("[skip] cannot parse line: \"" + line + "\" prev docsentID: \"" +
                prevDocsentID + "\" bits [" + bits.length + "]: \"" + bits.deep.mkString + "\"")
            }
          }
        }

        if (s.size > 0) {
          val sent = new Sentence(prevDocsentID, s.toList)
          s.clear()
          sent
        } else {
          null
        }
      }

      def next(): Sentence = {
        val nextSentence = new Sentence(sentence.docsentID, sentence.tokens)
        sentence = advance()
        nextSentence
      }
    }
  }

  def makeDocsentID(s: String): DocSentID = {
    val bits = s.split("\\.S")
    DocSentID(bits(0), bits(1).toInt)
  }

  val _maltParseFormatExtract = (parts: Array[String]) => {
    if (parts.size <= 4)
      (makeDocsentID(parts(0)), parts(1))
    else if (parts.size == 5)
      (DocSentID(parts(0), parts(1).toInt), parts(2))
    else
      throw new RuntimeException("unknown format for NER tag & docsent ID (neither 4 nor 5 parts): " + parts)
  }: (DocSentID, NETag)


  final def isNoun(tag: POSTag) = tag.equals("NN") || tag.equals("NNP") || tag.equals("NNS") || tag.equals("NNPS")

  final def nonOther(ner: NETag) = !ner.equals("O")


  type Inputter = (File) => Unit
  type Aggregator = () => Iterable[Document]

  def mkSentence2documentAggregator(): (Inputter, Aggregator) = {

    val docId2sentence = new mutable.HashMap[String, mutable.HashSet[Sentence]]()

    val g: Aggregator = () => {
      for ((docID, sentSet) <- docId2sentence) yield {
        val sentences = sentSet.toList.sortBy(_.sentIndex)
        new Document(docID, sentences)
      }
    }: Iterable[Document]

    val inputter = (input: File) => {
      TextProcessStructs.loadSentences(input).foreach(s => {
        docId2sentence.get(s.docID) match {
          case Some(l) => {
            l.add(s)
          }
          case None => {
            val l = new mutable.HashSet[Sentence]()
            l.add(s)
            docId2sentence.put(s.docID, l)
          }
        }
      })
    }

    (inputter, g)
  }

}
