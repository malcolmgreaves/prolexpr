package prolexpr.classify

import org.junit._
import prolexpr.classify.IO._
import java.io.File

/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
class IOTest {
  @Test
  def fileloadTest() = {
    val topdir = new File("/home/malcolm/out/kbp/docsearch/OLD_queries")
    val (fileWalker, nFiles) = mkFileWalker(topdir)
    Assert.assertEquals(80, nFiles)
    fileWalker.toList.foreach(f => Assert.assertEquals(true, f.getName.startsWith("SF_ENG_")))
  }

}
