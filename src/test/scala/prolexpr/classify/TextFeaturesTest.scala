package prolexpr.classify

import org.junit.{Assert, Test}
import java.io.StringReader
import prolexpr.classify.TextFeatures.{CompleteSlotfillerFeaturizer, SkipNgramFeatureizer, NgramFeatuerizer}
import java.util.regex.Pattern
import prolexpr.classify.TextProcessStructs._
import org.apache.log4j.Level
import prolexpr.classify.CandGen.Slotfill
import prolexpr.classify.TextProcessStructs.SentenceTarget
import prolexpr.classify.CandGen.SlotfillSent
import nak.data.FeatureObservation
import prolexpr.classify.TextProcessStructs.Sentence
import prolexpr.classify.TextProcessStructs.Token

/**
 * Tests all Featurizer[SlotfillSentTarget, String] implementations in TextFeatures
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
class TextFeaturesTest {
  val log = mkLogger(this.getClass.toString, Level.INFO)

  val sentenceText = """1	A	A	DT	DT	AFP_ENG_19950801.0001.S0:O:0:1	3	det	_	_
                       |2	Sino-US	Sino-US	JJ	JJ	AFP_ENG_19950801.0001.S0:O:2:9	3	amod	_	_
                       |3	summit	summit	NN	NN	AFP_ENG_19950801.0001.S0:O:10:16	6	nsubj	_	_
                       |4	would	would	MD	MD	AFP_ENG_19950801.0001.S0:O:17:22	6	aux	_	_
                       |5	be	be	VB	VB	AFP_ENG_19950801.0001.S0:O:23:25	6	cop	_	_
                       |6	impossible	impossible	JJ	JJ	AFP_ENG_19950801.0001.S0:O:26:36	27	nsubj	_	_
                       |7	until	until	IN	IN	AFP_ENG_19950801.0001.S0:O:37:42	9	mark	_	_
                       |8	Washington	Washington	NNP	NNP	AFP_ENG_19950801.0001.S0:LOCATION:43:53	9	nsubj	_	_
                       |9	corrects	corrects	VBZ	VBZ	AFP_ENG_19950801.0001.S0:O:54:62	27	dep	_	_
                       |10	the	the	DT	DT	AFP_ENG_19950801.0001.S0:O:63:66	12	det	_	_
                       |11	``	``	``	``	AFP_ENG_19950801.0001.S0:O:-1:1	12	punct	_	_
                       |12	mistake	mistake	NN	NN	AFP_ENG_19950801.0001.S0:O:68:75	27	dep	_	_
                       |13	''	''	''	''	AFP_ENG_19950801.0001.S0:O:-1:1	12	punct	_	_
                       |14	of	of	IN	IN	AFP_ENG_19950801.0001.S0:O:77:79	27	punct	_	_
                       |15	letting	letting	VBG	VBG	AFP_ENG_19950801.0001.S0:O:80:87	27	punct	_	_
                       |16	Taiwan	Taiwan	NNP	NNP	AFP_ENG_19950801.0001.S0:LOCATION:88:94	19	nn	_	_
                       |17	President	President	NNP	NNP	AFP_ENG_19950801.0001.S0:O:95:104	19	nn	_	_
                       |18	Lee Teng-hui	Lee Teng-hui	JJ	JJ	AFP_ENG_19950801.0001.S0:PERSON:105:117	19	amod	_	_
                       |19	visit	visit	NN	NN	AFP_ENG_19950801.0001.S0:O:118:123	27	nsubj	_	_
                       |20	the	the	DT	DT	AFP_ENG_19950801.0001.S0:O:124:127	21	det	_	_
                       |21	United States	United States	NNPS	NNPS	AFP_ENG_19950801.0001.S0:LOCATION:128:141	27	parataxis	_	_
                       |22	,	,	,	,	AFP_ENG_19950801.0001.S0:O:-1:0	21	punct	_	_
                       |23	a	a	DT	DT	AFP_ENG_19950801.0001.S0:O:44:45	26	det	_	_
                       |24	Chinese	Chinese	JJ	JJ	AFP_ENG_19950801.0001.S0:O:145:152	26	amod	_	_
                       |25	government	government	NN	NN	AFP_ENG_19950801.0001.S0:O:153:163	26	nn	_	_
                       |26	spokesman	spokesman	NN	NN	AFP_ENG_19950801.0001.S0:O:164:173	27	nsubj	_	_
                       |27	said	said	VBD	VBD	AFP_ENG_19950801.0001.S0:O:174:178	0	null	_	_
                       |28	Tuesday	Tuesday	NNP	NNP	AFP_ENG_19950801.0001.S0:DATE:179:186	27	tmod	_	_
                       |29	.	.	.	.	AFP_ENG_19950801.0001.S0:O:-1:0	27	punct	_	_""".stripMargin + "\n"
  val sentence = TextProcessStructs.loadSentencesIter(IO.lines(new StringReader(sentenceText))).toSeq.head
  val targets = CandGen.sentTargetMaker(sentence)
  val candidates = CandGen.withinSentenceCandidateMaker(sentence)

  val (leftNg, rightNg) = (new NgramFeatuerizer(TextFeatures.Left, 3), new NgramFeatuerizer(TextFeatures.Right, 3))
  val skipNgramFeatR = new SkipNgramFeatureizer(2, 2)
  val featR = new CompleteSlotfillerFeaturizer(2, 4)

  val featTest = (features: Seq[FeatureObservation[String]], n: Int) => {
    // ngram features *must* be unique!
    Assert.assertEquals(true, allUnique(features))
    // check expected # of features
    Assert.assertEquals(n, features.size)
  }

  val p = Pattern.compile("[LR]ngram\\([0-9]*\\)=")

  val getFeatureStrs = (x: Seq[FeatureObservation[String]]) =>
    x.map(f => p.matcher(f.feature).replaceAll("")).sorted

  def allUnique(features: Seq[FeatureObservation[String]]): Boolean = {
    features.toSet.size == features.size
  }

  @Test
  def checkNgramFeatures() = {
    val sb = new StringBuilder
    if (log.isDebugEnabled) sb.append("NgramFeatuerizer_Test\n")

    // both "summit" and "said"  are such that the length 3 ngram features for left and right,
    // respectively, should not be included because they would extend past the sentence boundary
    val selectedSF = candidates.filter(x =>
      x.query.token.word.equals("summit") && x.answer.token.word.equals("said")
    ).toSeq.head
    var feats = leftNg(selectedSF.query)
    featTest(feats, 2)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    feats = rightNg(selectedSF.answer)
    featTest(feats, 2)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    // "normal" target -- i.e. in the middleish of the sentence
    val usTarget = targets.filter(_.token.word.equals("United States")).toSeq.head
    feats = leftNg(usTarget)
    featTest(feats, 3)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    feats = rightNg(usTarget)
    featTest(feats, 3)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    if (log.isDebugEnabled) log.debug(sb.toString().trim)
  }

  @Test
  def checkSkipNgramFeatures() = {
    val sb = new StringBuilder
    if (log.isDebugEnabled) sb.append("SkipNgramFeatureizer_Test\n")

    val s = new Sentence(DocSentID("A", 0),
      "X Insurgents killed in ongoing fighting Y".split(" ").map(w => new Token(w, "", "")).toList)

    var selectedSF: Slotfill = new SlotfillSent(
      new SentenceTarget(0, s),
      new SentenceTarget(s.tokens.size - 1, s)
    )
    var feats = skipNgramFeatR(selectedSF)
    featTest(feats, 9)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    feats = new SkipNgramFeatureizer(2, 3).apply(selectedSF)
    featTest(feats, 10)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")


    selectedSF = candidates.filter(x =>
      x.query.token.word.equals("summit") && x.answer.token.word.equals("Taiwan")
    ).toSeq.head
    feats = skipNgramFeatR(selectedSF)
    featTest(feats, 30)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")


    selectedSF = candidates.filter(x =>
      x.query.token.word.equals("Taiwan") && x.answer.token.word.equals("summit")
    ).toSeq.head
    feats = skipNgramFeatR(selectedSF)
    featTest(feats, 30)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    if (log.isDebugEnabled) log.debug(sb.toString().trim)
  }

  @Test
  def checkComplete() = {
    val sb = new StringBuilder
    if (log.isDebugEnabled) sb.append("CompleteSlotfillerFeaturizer_Test\n")

    var selectedSF = candidates.filter(x =>
      x.query.token.word.equals("President") && x.answer.token.word.equals("Lee Teng-hui")
    ).toSeq.head
    var feats = featR(selectedSF)
    featTest(feats, 4)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    selectedSF = candidates.filter(x =>
      x.query.token.word.equals("Washington") && x.answer.token.word.equals("United States")
    ).toSeq.head
    feats = featR(selectedSF)
    featTest(feats, 49)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    selectedSF = candidates.filter(x =>
      x.query.token.word.equals("United States") && x.answer.token.word.equals("Washington")
    ).toSeq.head
    feats = featR(selectedSF)
    featTest(feats, 49)
    if (log.isDebugEnabled) sb.append(feats.mkString("\n") + "\n")

    if (log.isDebugEnabled) log.debug(sb.toString().trim)
  }

}