package prolexpr.classify


/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object Runner {

  def main(args: Array[String]) {
    val ms = Array("MainCoreferenceResolution", "ProcessedTextClassifier")
      .map((x) => "prolexpr.classify." + x)
    println("main classes:\n\t" + ms.deep.mkString("\n\t"))
  }

}
