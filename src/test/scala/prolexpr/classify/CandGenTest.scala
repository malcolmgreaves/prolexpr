package prolexpr.classify

import org.apache.log4j.Level
import java.io.{File, StringReader, BufferedReader}
import org.junit.{Assert, Test}
import prolexpr.classify.CandGen.{Slotfill, AnswerCorefPartialGen}

/**
 * Tests all Featurizer[SlotfillSentTarget, String] implementations in TextFeatures
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
class CandGenTest {
  val log = mkLogger(this.getClass.toString, Level.INFO)

  val teststr =
    "# proved 1	candidateAnswerCoref(w_Aerolineas_Argentinas,-10992,-10993,-10994,-10995)	2717 msec\n" +
      "1\t0.05081001826299269\t-10992=c[id_AFP_ENG_20071011.0360.S2], -10993=c[w_Marsans], -10994=c[id_AFP_ENG_20071011.0360.S0], -10995=c[w_Spanish_travel_group_Marsans]\n" +
      "2\t0.035314207420958664\t-10992=c[id_AFP_ENG_20070613.0602.S2], -10993=c[w_two], -10994=c[id_AFP_ENG_20070613.0602.S4], -10995=c[w_two]\n" +
      "3\t0.035314207420958664 \t-10992=c[id_AFP_ENG_20070613.0602.S2], -10993=c[w_Marsans], -10994=c[id_AFP_ENG_20070613.0602.S0], -10995=c[w_The_private_Spanish_travel_group_Marsans]\n" +
      "4\t0.03207159490633367\t-10992=c[id_XIN_ENG_20080112.0210.S1], -10993=c[w_Aerolineas_Argentinas], -10994=c[id_XIN_ENG_20080112.0210.S5], -10995=c[w_Aerolineas_Argentinas]\n" +
      "5\t0.03207159490633367\t-10992=c[id_APW_ENG_20081125.0680.S1], -10993=c[w_Grupo_Marsans], -10994=c[id_APW_ENG_20081125.0680.S4], -10995=c[w_Marsans]\n"


  @Test
  def tPartialCandGenCreation() = {
    val doc2fns = CandGen.loadProPPRCandsPartFns(new BufferedReader(new StringReader(teststr)))

    Assert.assertEquals(4, doc2fns.size)
    Assert.assertEquals(true, doc2fns.contains("AFP_ENG_20071011.0360"))
    Assert.assertEquals(true, doc2fns.contains("AFP_ENG_20070613.0602"))
    Assert.assertEquals(true, doc2fns.contains("XIN_ENG_20080112.0210"))
    Assert.assertEquals(true, doc2fns.contains("APW_ENG_20081125.0680"))

    Assert.assertEquals(2, doc2fns.get("AFP_ENG_20070613.0602").get.size)
    Assert.assertEquals(1, doc2fns.get("AFP_ENG_20071011.0360").get.size)
    Assert.assertEquals(1, doc2fns.get("XIN_ENG_20080112.0210").get.size)
    Assert.assertEquals(1, doc2fns.get("APW_ENG_20081125.0680").get.size)

    for ((docID, partCGSet) <- doc2fns) {
      partCGSet.foreach(cg => Assert.assertEquals(classOf[AnswerCorefPartialGen], cg.getClass))
    }
  }

  val exampleFi = new File("data/test/data/EXAMPLE.malt")

  @Test
  def tLoadDocsAndPartCandGenAndExe() = {
    val doc2fns = CandGen.loadProPPRCandsPartFns(new BufferedReader(new StringReader(teststr)))
    val candgen = CandGen.candgenProPPRRule(doc2fns)

    if (log.isDebugEnabled) {
      for ((docID, partCG) <- doc2fns)
        log.debug(docID + "\t[" + partCG.size + "]:\n\t" + partCG.mkString("\n\t"))
    }

    val exampleDocs = TextProcessStructs.loadDocuments(exampleFi).toSeq
    Assert.assertEquals(true, exampleDocs.map(_ => 1).fold(0)(_ + _) > 0)

    exampleDocs.foreach(d => {
      val sfs = candgen(d)
      val fInner = (sf: Slotfill) => sf.inner.filter(t => !t.word.equals(t.tagPOS))
      val s = sfs.map(sf => sf.toString + "\n\tInner[" + fInner(sf).size + "]: " +
        fInner(sf).mkString(",") + "\n")
      log.info("Generated " + sfs.size + " from: " + d.docID + "\n\t" + s.mkString("\n\t"))
    })

  }

  @Test
  def tProPPRCandGenInner() = {
    Assert.fail("test not implemented")
  }

}
