package prolexpr.classify

import java.io._
import scala.collection.mutable

/**
//    Exceptions.checkNonNull(outputDir, "output working directory");
//    try {
//      Exceptions.checkFileDoesntExist(outputDir);
//      Exceptions.checkDirDoesntExist(outputDir);
  * DocumentAggregatorForLuceneQueries
  * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)             u
  */
object DocAggregator {
  val log = mkLogger(this.getClass.toString)

  def main(args: Array[String]) {
    if (args.length < 2) {
      throw new IllegalArgumentException("Incorrect arguments, need:\n" +
        "\toutput file\n" +
        "\tQUERIES\" input")
      //        + "\t\"CORPUS\" input")
    }

    log.info("Output:         " + args(0))
    log.info("Input, Queries: " + args(1))
    //    log.info("Input, Corpus:  " + args(2))

    val output = new File(args(0))
    if (output.exists()) {
      throw new IllegalStateException("FATAL Output file already exists!")
    }
    val w = new BufferedWriter(new FileWriter(output))

    val inputQueries = new File(args(1))
    if (!inputQueries.exists()) {
      throw new IllegalStateException("FATAL Input queries does not exist!")
    }

    //    val inputCorpus = new File(args(2))
    //    if (!inputCorpus.exists()) {
    //      throw new IllegalStateException("FATAL Input corpus does not exist!")
    //    }

    // process all files from each input argument
    //    val docIDs = new mutable.HashMap[String, mutable.HashSet[Sentence]]()

    val (inputter, aggregator) = TextProcessStructs.mkSentence2documentAggregator()

    log.info("processing input queries: " + inputQueries)
    var startTime = System.nanoTime()

    inputter(inputQueries)

    var endTime = System.nanoTime()
    log.info("processed " + inputQueries + " in " + (endTime - startTime) + " ns")

    // collect all of the document IDs
    startTime = System.nanoTime()

    log.info("aggregating document IDs...")

    val documents = aggregator()

    log.info("sorting and writing out sentences for each document")
    val dw = IO.documentWriter(w)
    var (nDocs, nSentences) = (0, 0)

    val doc2len = new mutable.HashMap[Int, Int]()

    documents.foreach(doc => {
      nDocs += 1
      nSentences += doc.sentences.size

      val prev = doc2len.get(doc.sentences.size) match {
        case Some(c) => c
        case None => 0
      }
      doc2len.put(doc.sentences.size, prev + 1)
      dw(doc)
    })
    //    log.info("getting " + docIDs.size + " documents from corpus")
    //    var di = 0
    //    TextProcessStructs.loadDocuments(inputCorpus).foreach(d => {
    //      // find all of the documents in the corpus that have one of these doc IDs
    //      // write out those documents to disk (at the ouput file loc)
    //      if (docIDs.contains(d.docID)) {
    //        di += 1
    //        dw(d)
    //      }
    //    })
    w.close()

    endTime = System.nanoTime()
    //    log.info("found " + di + " / " + docIDs.size + " documents in the corpus in " +
    //      (endTime - startTime) + " ns wrote to output file")
    log.info("wrote out " + nDocs + " documents (" + nSentences +
      " sentences) in " + (endTime - startTime) + " ns")

    val dist = doc2len.toList.sortBy(x => -x._1).map(x => {
      val (len, count) = (x._1, x._2)
      len + ": " + count
    }).mkString("\n")

    log.info("distribution: [${len}: ${count}]\n" + dist)

    log.info("done")
  }
}