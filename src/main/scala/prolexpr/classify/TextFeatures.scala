package prolexpr.classify


import nak.data.{FeatureObservation, Featurizer}
import prolexpr.classify.TextProcessStructs.{SentenceTarget, Token}
import scala.collection.mutable.ArrayBuffer
import prolexpr.classify.CandGen.Slotfill

/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object TextFeatures {
  val log = mkLogger(this.getClass.getName)

  /**
   * Creates left and right Ngram features as well as skip-ngram features for a given
   * slot-fill instance. A convience class to aggregate all of the feautres used in
   * the slot-filling task.
   */
  class CompleteSlotfillerFeaturizer(val ngWidth: Int = 2, val kSkip: Int = 4,
                                     val isTarget: (Token) => Boolean = (_) => true,
                                     val tokView: (Token) => String = _mapFn)
    extends Featurizer[Slotfill, String] {

    // make the ngram (left-right "adjacent" features) and skip ngram (inner) featureizers
    val leftNg = new NgramFeatuerizer(Left, ngWidth)
    val rightNg = new NgramFeatuerizer(Right, ngWidth)
    val skipNg = new SkipNgramFeatureizer(ngWidth, kSkip)

    def localizeFeatureObs(x: String)(f: FeatureObservation[String]) =
      FeatureObservation(x + f.feature, f.magnitude)

    // separates the query (Q) and answer (A) features
    val _queryLocFeatObs = localizeFeatureObs("Q=") _
    val _answerLocFeatObs = localizeFeatureObs("A=") _

    def apply(sf: Slotfill): Seq[FeatureObservation[String]] = {
      if (isTarget(sf.query.token) && isTarget(sf.answer.token)) {
        val features = new ArrayBuffer[FeatureObservation[String]]()

        // left & right ngram features immediately surrounding the query and answer
        features.appendAll(leftNg.apply(sf.query).map(_queryLocFeatObs))
        features.appendAll(rightNg.apply(sf.answer).map(_answerLocFeatObs))

        // skip ngram features in-between the query and answer
        features.appendAll(skipNg.apply(sf))

        features.toSeq
      } else {
        List.empty[FeatureObservation[String]].toList
      }
    }

    override def toString() = {
      "skip ngram: " + skipNg.toString() + " ngram: " + ngWidth
    }
  }

  class SkipNgramFeatureizer(val ngWidth: Int = 2, val kSkip: Int = 4,
                             val isTarget: (Token) => Boolean = (_) => true,
                             val tokView: (Token) => String = _mapFn)
    extends Featurizer[Slotfill, String] {

    if (ngWidth < 1) throw new IllegalArgumentException("ngram width must be >= 1")
    if (kSkip < 0) throw new IllegalArgumentException("kSkip must be >= 0")

    val (_wildcard, _skip) = ("*", "S(" + kSkip + ")=")


    def apply(sf: Slotfill): Seq[FeatureObservation[String]] = {

      val inner = sf.inner

      if (inner.size == 0) {
        List.empty[FeatureObservation[String]]
      } else {
        val features = new ArrayBuffer[FeatureObservation[String]](50)

        val end = inner.size - ngWidth + 2
        (0 until end).foreach(i => {

          _selectKSkipGram(ngWidth, kSkip, inner.slice(i, end)).foreach(feat => {
            features.append(FeatureObservation[String](_skip + feat, 1.0))
          })

        })
        features.toList
      }
    }

    def _selectKSkipGram(n: Int, k: Int, tokens: Seq[Token]): Seq[String] = {
      val first = tokView(tokens(0))
      if (n <= 1) {
        List(first)
      } else {
        val grams = new ArrayBuffer[String](n * k)

        var j = 0
        while (j < Math.min(k + 1, tokens.size)) {

          val restOfTokens = tokens.slice(j + 1, tokens.size)
          if (restOfTokens.size > 0) {

            val subgrams = _selectKSkipGram(n - 1, k - j, restOfTokens)
            val firstComma = first + ","
            grams.appendAll(subgrams.map(gram => firstComma + gram))
          }

          j += 1
        }
        grams.toArray.toSeq
      }
    }
  }

  sealed trait Direction

  case object Left extends Direction

  case object Right extends Direction

  /**
   * Creates Ngram features to the left and right of a
   * @param width ngram size (e.g unigram (1), bigram (2), trigram (3), etc.)
   */
  class NgramFeatuerizer(val dir: Direction, val width: Int = 2,
                         val filt: (Token) => Boolean = (x) => !x.word.equals(x.tagPOS),
                         val tokView: (Token) => String = _mapFn)
    extends Featurizer[SentenceTarget, String] {

    def apply(sentTarg: SentenceTarget): Seq[FeatureObservation[String]] = {
      val features = new ArrayBuffer[FeatureObservation[String]]()
      var w = width
      while (w > 0) {
        val ngramFeat = partialApplyHelpers(w)(sentTarg)
        if (ngramFeat.size > 0) {
          features.append(FeatureObservation(ngramFeat))
        }
        w -= 1
      }
      features.toSeq
    }

    val partialApplyHelpers = (0 until width + 1).map(_apply_h)

    // helpoer function: does actuatl ngram construction
    def _apply_h(w: Int): (SentenceTarget => String) = {

      dir match {
        case Left =>
          (st: SentenceTarget) => {
            // skip over the token "ti"
            // grab the ti-wwidth tokens to the left
            val leftMostBoundary = st.ti - w
            if (leftMostBoundary >= 0) {
              st.s.tokens.slice(leftMostBoundary, st.ti).map(tokView).mkString(",")
            } else {
              ""
            }
          }: String
        case Right =>
          (st: SentenceTarget) => {
            // and right of the target token ti
            val rightMostBoundary = st.ti + w + 1
            if (rightMostBoundary <= st.s.tokens.size) {
              st.s.tokens.slice(st.ti + 1, rightMostBoundary).map(tokView).mkString(",")
            } else {
              ""
            }
          }: String
      }
    }

    override def toString() = "Ngram width: " + width
  }

  // even helpers need helpers!
  val _mapFn = (t: Token) => t.word.toLowerCase

}