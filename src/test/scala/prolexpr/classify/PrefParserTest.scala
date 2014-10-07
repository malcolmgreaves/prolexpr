package prolexpr.classify

import org.apache.log4j.Level


/**
 * Tests all Featurizer[SlotfillSentTarget, String] implementations in TextFeatures
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
class PrefParserTest {
  val log = mkLogger(this.getClass.toString, Level.INFO)

  //  @Test
  //  def tPrecRecF1EvalParse() = {
  //
  //    val lines = """14/05/04 00:14:06 evaluating model on test fold 1
  //                  14/05/04 00:14:08 [test fold 2] precision: 38.810000  recall: 60.470000  f1: 47.277210
  //                  14/05/04 00:14:09 [test fold 0] precision: 30.140000  recall: 50.000000  f1: 37.609184
  //                  14/05/04 00:14:09 [test fold 1] precision: 40.430000  recall: 43.180000  f1: 41.759775"""
  //      .stripMargin.split("\\n").map(_.trim).filter(_.size > 0)
  //
  //    Assert.assertEquals(None, PerfParser.Line(lines(0)))
  //
  //    val trials = lines.length - 1
  //    val matches = lines.slice(1, lines.length).map(PerfParser.Line.apply).count(_ != None)
  //    Assert.assertEquals(trials, matches)
  //  }
}
