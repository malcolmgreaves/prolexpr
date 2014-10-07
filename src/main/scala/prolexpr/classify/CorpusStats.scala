package prolexpr.classify

import java.io.File
import scala.collection.mutable

/**
 * @author Malcolm W. Greaves (greparallelaves.malcolm@gmail.com)
 */
object CorpusStats {
  val log = mkLogger(this.getClass.toString)

  def mkDF() = {
    val df = new java.text.DecimalFormat("###,###.###")
    (n: Int) => df.format(n)
  }


  def main(args: Array[String]) {
    if (args.length < 1) {
      throw new IllegalArgumentException("NEED input argument(s)")
    }

    var (ai, nChunks, nSentences, nDocs) = (1, 0, 0, 0)
    val doclen2count = new mutable.HashMap[Int, Int]()
    val startTime = System.nanoTime()

    args.foreach((arg) => {
      log.info("Processing argument [" + ai + "/" + args.length + "]: " + arg)
      ai += 1
      TextProcessStructs.loadDocuments(new File(arg)).foreach((doc) => {
        doc.sentences.foreach((s) => {
          nChunks += s.tokens.size
        })

        val prevCount = doclen2count.get(doc.sentences.size) match {
          case Some(c) => c
          case None => 0
        }
        doclen2count.put(doc.sentences.size, prevCount + 1)

        nSentences += doc.sentences.size
        nDocs += 1
      })
    })

    val endTime = System.nanoTime()

    val df = mkDF()
    log.info(df(nChunks) + " chunks in " + df(nSentences) + " sentences in " + df(nDocs) + " documents")

    val len2counts = doclen2count.toList
    val totalCount = doclen2count.map(x => x._2).sum.toDouble
    log.info("Distribution of document lengths: ${# SENTENCES}\t${FREQUENCY}\t${PROPORTION}\n" +
      len2counts.sortBy(x => -x._1).map(x =>
        x._1 + "\t" + x._2 + "\t" + (x._2 / totalCount)
      ).mkString("\n"))

    log.info("processed in " + (endTime - startTime) + " ns")
    // tab delim format is written to stdout, human readable format is written to stderr
    println(nChunks + "\t" + nSentences + "\t" + nDocs)
  }

}