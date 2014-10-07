package prolexpr.classify

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.Predef._
import scala.collection.mutable
import org.apache.log4j.Level
import prolexpr.classify.TextProcessStructs.DocSentID
import prolexpr.classify.TextProcessStructs.Token
import scala.Some
import prolexpr.classify.TextProcessStructs.SentenceTarget
import prolexpr.classify.TextProcessStructs.Sentence
import prolexpr.classify.TextProcessStructs.Document
import java.io.BufferedReader

/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object CandGen {
  val log = mkLogger(this.getClass.getName.replace("$", ""), Level.DEBUG)

  type CandidateGenerator = Document => Seq[Slotfill]

  def sentenceBySentenceCandidateMaker(d: Document): Seq[Slotfill] = {
    val slotfillers = new ListBuffer[Slotfill]()
    d.sentences.map(withinSentenceCandidateMaker).foreach(slotfillers.append)
    slotfillers.toSeq
  }

  def withinSentenceCandidateMaker(s: Sentence): Seq[Slotfill] = {
    val targets = sentTargetMaker(s)
    val buff = new ArrayBuffer[Slotfill](targets.size * targets.size - targets.size)
    for (ti1 <- 0 until targets.size;
         ti2 <- 0 until targets.size) {
      val tok1 = s.tokens(ti1)
      val tok2 = s.tokens(ti2)
      if (ti1 != ti2 && TextProcessStructs.isNoun(tok1.tagPOS) &&
        TextProcessStructs.isNoun(tok2.tagPOS)) {
        buff.append(new SlotfillSent(targets(ti1), targets(ti2)))
      }
    }
    buff.toSeq
  }

  def sentTargetMaker(s: Sentence): Seq[SentenceTarget] =
    (0 until s.tokens.size).map(i => new SentenceTarget(i, s)).toSeq

  def candgenProPPRRule(doc2fn: Doc2CandGen, alsoUseSentBySentCandgen: Boolean = false) = {

    val getAndApplyPartCandGenFns = (d: Document) => doc2fn.get(d.docID) match {
      case Some(sfMakerFns) => {
        sfMakerFns.map(cg => cg(d)).filter(_ != null)
      }
      case None => Seq.empty[Slotfill]
    }

    if (alsoUseSentBySentCandgen) {
      (d: Document) => {
        val slotfillers = new mutable.HashSet[Slotfill]
        getAndApplyPartCandGenFns(d).foreach(slotfillers.add)
        // We ALSO perform sentence-by-sentence candidate generation for the entire document.
        // We use a Set so that we do not accidentily include duplicate candidates.
        sentenceBySentenceCandidateMaker(d).foreach(slotfillers.add)

        slotfillers.toSeq
      }
    } else {
      (d: Document) => {
        val slotfillers = new mutable.HashSet[Slotfill]
        getAndApplyPartCandGenFns(d).foreach(slotfillers.add)
        slotfillers.toSeq
      }
    }
  }: CandidateGenerator


  sealed trait Slotfill {
    def docID: String

    val inner: List[Token]

    def query: SentenceTarget

    def answer: SentenceTarget

    override def toString = "(" + query.toString + " , " + answer.toString + ")"
  }

  /**
   * Both the query and answer SentenceTargets must have the same document IDs.
   * Also both must come from the same sentence. If either of these two conditions
   * are not met, then construction throws an IllegalArgumentException
   */
  case class SlotfillSent(query: SentenceTarget, answer: SentenceTarget) extends Slotfill {
    if (!query.s.docsentID.equals(answer.s.docsentID))
      throw new IllegalArgumentException("Query and Answer must come from same sentence! Query:" +
        query.s.docID + " , answer: " + answer.s.docID)

    def docID: String = query.s.docID

    val inner = if (query.ti < answer.ti) {
      query.s.slice(query.ti + 1, answer.ti)
    } else {
      query.s.slice(answer.ti + 1, query.ti)
    }
  }

  abstract class SlotfillCoref(query: SentenceTarget, ref: SentenceTarget, answer: SentenceTarget) extends Slotfill {
    if (!query.s.docID.equals(answer.s.docID) || !query.s.docID.equals(ref.s.docID) ||
      !answer.s.docID.equals(ref.s.docID)) {
      throw new IllegalArgumentException("Query and Answer must come from same sentence! Query:" +
        query.s.docID + " , answer: " + answer.s.docID)
    }

    def docID: String = query.s.docID
  }

  case class SlotfillCorefQuery(query: SentenceTarget, ref: SentenceTarget, answer: SentenceTarget)
    extends SlotfillCoref(query, ref, answer) {
    if (!answer.s.docsentID.equals(ref.s.docsentID))
      throw new IllegalArgumentException("answer and referent must come from same sentence! answer:" +
        answer.s.docsentID + " , referent: " + ref.s.docsentID)

    val inner = if (answer.ti < ref.ti) {
      answer.s.slice(answer.ti + 1, ref.ti)
    } else {
      answer.s.slice(ref.ti + 1, answer.ti)
    }

    override def toString = super.toString + " AnsRef: " + ref
  }

  case class SlotfillCorefAnswer(query: SentenceTarget, ref: SentenceTarget, answer: SentenceTarget)
    extends SlotfillCoref(query, ref, answer) {
    if (!query.s.docsentID.equals(ref.s.docsentID))
      throw new IllegalArgumentException("query and referent must come from same sentence! query: " +
        query.s.docsentID + " , referent: " + ref.s.docsentID)

    val inner = if (ref.ti < query.ti) {
      query.s.slice(ref.ti + 1, query.ti)
    } else {
      query.s.slice(query.ti + 1, ref.ti)
    }

    override def toString = super.toString + " QueryRef: " + ref
  }

  case class SlotfillDualCoref(query: SentenceTarget, refs: Slotfill, answer: SentenceTarget) extends Slotfill {
    if (!query.s.docID.equals(refs.docID) || !answer.s.docID.equals(refs.docID)) {
      throw new IllegalArgumentException("Query, Referents, and Answer must have same doc IDs!" +
        "query: " + query.s.docID + " , referents: " + refs.docID + " , answer: " + answer.s.docID)
    }

    def docID = query.s.docID

    val inner = refs.inner
  }

  type DocID = String

  type Doc2CandGen = Map[DocID, List[PartialCandGen]]

  def pSentTarget(target: String, s: Sentence) = {
    var ii = 0
    var continue = true
    var st: SentenceTarget = null
    while (ii < s.tokens.size && continue) {

      if (s.tokens(ii).word.equals(target)) {
        st = SentenceTarget(ii, s)
        continue = false
      }
      ii += 1
    }
    st
  }: SentenceTarget

  abstract class PartialCandGen(val docID: DocID,
                                val query: String, val queryDocsentID: DocSentID,
                                val answer: String, val answerDocsentID: DocSentID) {
    if (!docID.equals(queryDocsentID.docID) ||
      !docID.equals(answerDocsentID.docID)) {
      throw new IllegalArgumentException("docID and either queryDocsentID or answerDocsentID do not agree! " +
        "docID: " + docID + " queryDocsentID: " + queryDocsentID + " answerDocsentID:" + answerDocsentID)
    }

    /*
      Returns a Slotfiler if the document is appropriate for this
      PartialCandidateGeneration function, null otherwise.
     */
    def apply(d: Document): Slotfill = {
      if (!docID.equals(d.docID)) null
      else apply_h(d)
    }

    def apply_h(d: Document): Slotfill

    override def toString = " (" + query + "::" + queryDocsentID + " , " + answer + "::" + answerDocsentID + ")"
  }

  class SentPartialGen(override val docID: DocID,
                       override val query: String, override val queryDocsentID: DocSentID,
                       override val answer: String, override val answerDocsentID: DocSentID)
    extends PartialCandGen(docID, query, queryDocsentID, answer, answerDocsentID) {

    def apply_h(d: Document): Slotfill = {
      // find the EXACT sentence...because the document might not be full
      var (qsent, asent): (Sentence, Sentence) = (null, null)
      d.sentences.foreach(s => {
        if (s.docsentID.equals(queryDocsentID)) {
          qsent = s
        } else if (s.docsentID.equals(answerDocsentID)) {
          asent = s
        }
      })
      if (qsent != null && asent != null) {
        val q = pSentTarget(query, qsent)
        if (q != null) {
          val a = pSentTarget(answer, asent)
          if (a != null) {
            return SlotfillSent(q, a)
          }
        }
      }
      null
    }
  }

  class AnswerCorefPartialGen(override val docID: DocID,
                              override val query: String, override val queryDocsentID: DocSentID,
                              override val answer: String, override val answerDocsentID: DocSentID,
                              val ref: String)
    extends PartialCandGen(docID, query, queryDocsentID, answer, answerDocsentID) {

    def apply_h(d: Document): Slotfill = {
      // find the EXACT sentence...because the document might not be full
      var (qsent, asent): (Sentence, Sentence) = (null, null)
      d.sentences.foreach(s => {
        if (s.docsentID.equals(queryDocsentID)) {
          qsent = s
        } else if (s.docsentID.equals(answerDocsentID)) {
          asent = s
        }
      })

      if (qsent != null && asent != null) {
        val q = pSentTarget(query, qsent)
        if (q != null) {
          val r = pSentTarget(ref, qsent)
          if (r != null) {
            val a = pSentTarget(answer, asent)
            if (a != null) {
              return SlotfillCorefAnswer(q, r, a)
            }
          }
        }
      }
      null
    }

    override def toString = super.toString + " ARef: " + ref
  }

  class QueryCorefPartialGen(override val docID: DocID,
                             override val query: String, override val queryDocsentID: DocSentID,
                             override val answer: String, override val answerDocsentID: DocSentID,
                             val ref: String)
    extends PartialCandGen(docID, query, queryDocsentID, answer, answerDocsentID) {

    def apply_h(d: Document): Slotfill = {
      // find the EXACT sentence...because the document might not be full
      var (qsent, asent): (Sentence, Sentence) = (null, null)
      d.sentences.foreach(s => {
        if (s.docsentID.equals(queryDocsentID)) {
          qsent = s
        } else if (s.docsentID.equals(answerDocsentID)) {
          asent = s
        }
      })

      if (qsent != null && asent != null) {
        val q = pSentTarget(query, qsent)
        if (q != null) {
          val r = pSentTarget(ref, asent)
          if (r != null) {
            val a = pSentTarget(answer, asent)
            if (a != null) {
              return SlotfillCorefQuery(q, r, a)
            }
          }
        }
      }
      null
    }

    override def toString = super.toString + " QRef: " + ref
  }

  def loadProPPRCandsPartFns(r: BufferedReader) = {

    var parser: (String) => (String, PartialCandGen) = (x) => throw new RuntimeException("uninitialized Slotfill parser")


    val queryExtractor = (l: String) => l.replace("w_", "").replace("_", " ")
    val wordExtractor = (l: String) => l.replace("c[w_", "").replace("]", "").replace("_", " ").replace(",", "")
    val idExtractor = (l: String) => l.replace("c[id_", "").replace("]", "").replace(",", "")

    val doc2fn = new mutable.HashMap[DocID, mutable.HashSet[PartialCandGen]]

    var skip = false
    IO.lines(r).foreach(line => {
      if (line.startsWith("#")) {
        // TODO: need to parse out this line to get a mapping from variable names (ie the numbers)
        // to the slots that we want! we're NOT under ANY CIRCUMSTANCES guarantied to be parsing
        // a fixed output format...............................................which blows......

        // get type of slotfiller and query
        skip = false

        val bits = line.split("\\t")
        val sbits = bits(1).split("\\(")
        val sftype = sbits(0)
        val variables = sbits(1).replace(")", "").split(",")
        val query = queryExtractor(variables(0))

        sftype match {
          case "candidateAnswerCoref" => {
            // variables are: Query,SentenceQ,AnswerRef,SentenceA,Answer
            val sentenceQvar = variables(1)
            val answerRefVar = variables(2)
            val sentenceAVar = variables(3)
            val answerVar = variables(4)

            parser = (l: String) => {
              var (queryDocsentID, answerDocsentID): (DocSentID, DocSentID) = (null, null)
              var (answerRef, answer) = ("", "")

              l.split(", ").foreach(b => {
                val bits = b.split("=")
                if (bits(0).equals(sentenceQvar)) {
                  queryDocsentID = TextProcessStructs.parseDocSentIDFrom(idExtractor(bits(1)))
                } else if (bits(0).equals(answerRefVar)) {
                  answerRef = wordExtractor(bits(1))
                } else if (bits(0).equals(sentenceAVar)) {
                  answerDocsentID = TextProcessStructs.parseDocSentIDFrom(idExtractor(bits(1)))
                } else if (bits(0).equals(answerVar)) {
                  answer = wordExtractor(bits(1))
                } else {
                  throw new IllegalArgumentException("unknown variable & value: \"" + b + "\"")
                }
              })

              if (answerRef.size == 0 || answer.size == 0) {
                throw new IllegalStateException("did not initialize answerRef or answer: " + l)
              }
              if (!queryDocsentID.docID.equals(answerDocsentID.docID)) {
                throw new IllegalStateException("query and answer Doc IDs don't match: " +
                  queryDocsentID + " , " + answerDocsentID)
              }

              val docID = queryDocsentID.docID

              (docID, new AnswerCorefPartialGen(docID, query, queryDocsentID,
                answer, answerDocsentID, answerRef))
            }
          }
          case "candidateQueryCoref" => {
            // variables are Query,SentenceQ,SentenceA,QueryRef,Answer
            val sentenceQvar = variables(1)
            val sentenceAVar = variables(2)
            val queryRefVar = variables(3)
            val answerVar = variables(4)

            parser = (l: String) => {
              var (queryDocsentID, answerDocsentID): (DocSentID, DocSentID) = (null, null)
              var (queryRef, answer) = ("", "")

              l.replace(",", "").split(" ").foreach(b => {
                val bits = b.split("=")
                if (bits(0).equals(sentenceQvar)) {
                  queryDocsentID = TextProcessStructs.parseDocSentIDFrom(idExtractor(bits(1)))
                } else if (bits(0).equals(queryRefVar)) {
                  queryRef = wordExtractor(bits(1))
                } else if (bits(0).equals(sentenceAVar)) {
                  answerDocsentID = TextProcessStructs.parseDocSentIDFrom(idExtractor(bits(1)))
                } else if (bits(0).equals(answerVar)) {
                  answer = wordExtractor(bits(1))
                } else {
                  throw new IllegalArgumentException("unknown variable & value: \"" + b + "\"")
                }
              })

              if (queryRef.size == 0 || answer.size == 0) {
                throw new IllegalStateException("did not initialize queryRef or answer: " + l)
              }
              if (!queryDocsentID.docID.equals(answerDocsentID.docID)) {
                throw new IllegalStateException("query and answer Doc IDs don't match: " +
                  queryDocsentID + " , " + answerDocsentID)
              }

              val docID = queryDocsentID.docID
              (docID, new QueryCorefPartialGen(docID, query, queryDocsentID,
                answer, answerDocsentID, queryRef))
            }
          }

          case "candidateSentence" => {
            // variables are Query,Sentence,Answer
            val sentenceVar = variables(1)
            val answerVar = variables(2)

            parser = (l: String) => {
              var docsentID: DocSentID = null
              var answer = ""

              l.replace(",", "").split(" ").foreach(b => {
                val bits = b.split("=")
                if (bits(0).equals(sentenceVar)) {
                  docsentID = TextProcessStructs.parseDocSentIDFrom(idExtractor(bits(1)))
                } else if (bits(0).equals(answerVar)) {
                  answer = wordExtractor(bits(1))
                } else {
                  throw new IllegalArgumentException("unknown variable & value: \"" + b + "\"")
                }
              })

              if (answer.size == 0 || docsentID == null) {
                throw new IllegalStateException("did not initialize answer or docsentID: " + l)
              }

              val docID = docsentID.docID
              val si = docsentID.sentNum
              (docID, new SentPartialGen(docID, query, docsentID, answer, docsentID))
            }
          }
          case "candidateDoubleCoref" => {
            log.debug("ignoring candidateDoubleCoref  reading & parsing")
            skip = true
          }
          case _ => throw new IllegalStateException("Unknown rule type:  \"" + sftype + "\"")
        }
      } else if (!skip) {
        val (docID, mkrfunc) = parser(line.trim().split("\t")(2))
        doc2fn.get(docID) match {
          case Some(fns) => fns.add(mkrfunc)
          case None => {
            val fns = new mutable.HashSet[PartialCandGen]()
            fns.add(mkrfunc)
            doc2fn.put(docID, fns)
          }
        }
      }
    }

    )
    doc2fn.toMap.map(x => {
      (x._1, x._2.toList)
    })
  }: Doc2CandGen

}