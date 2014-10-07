import java.io.File
import prolexpr.classify.IO
import prolexpr.classify.TrainingDataMaker.Label
import scala.collection.mutable.ListBuffer
import scala.xml.Elem

/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */

object MakeXmlQueries {

  def main(args: Array[String]) {
    val qnames = IO.lines(new File("data/queries/kbp/KBP2012/query_names")).map(_.trim()).filter(_.size > 0).toSet
    val (labeler, _) = Label.load(new File("data/TAC-KBP-2010+2012_data.train"))

    val newXMLQueries = new ListBuffer[Elem]()
    var ii = 0
    var rej = 0
    for ((query, _) <- labeler) {
      if (!qnames(query)) {
        ii += 1
        newXMLQueries.append(formatXML("TrainDataQ_" + ii, query))
      } else {
        rej += 1
      }
    }



    val out = new File("./new_queries.xml")
    val queries = finalXML(newXMLQueries)
    println(newXMLQueries.size + " new queries, rejected " + rej + " because they're already in the old query set")
    IO.dump(out, "<?xml version='1.0' encoding='UTF-8'?>\n" + queries + "\n")
  }

  val defaultslotval = "null"

  def formatXML(id: String, name: String, docid: String = defaultslotval, beg: Int = 0,
                end: Int = 0, enttype: String = defaultslotval,
                nodeid: String = defaultslotval, ignore: String = defaultslotval) = {
    <query id={id}>
      <name>
        {name}
      </name>
      <docid>
        {docid}
      </docid>
      <beg>
        {beg}
      </beg>
      <end>
        {end}
      </end>
      <enttype>
        {enttype}
      </enttype>
      <nodeid>
        {nodeid}
      </nodeid>
      <ignore>
        {ignore}
      </ignore>
    </query>
  }

  def finalXML(newXMLQueries: Seq[Elem]) = {
    <kbpslotfill>
      {newXMLQueries}
    </kbpslotfill>
  }
}