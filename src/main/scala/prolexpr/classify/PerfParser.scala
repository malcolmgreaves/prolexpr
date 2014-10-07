import scala.util.matching.Regex
import java.io.File
import scala.collection.mutable.ListBuffer
import scala.io.Source

object PerfParser {

  def main(args: Array[String]) = {
    val (prec, rec, f1, perRel) = apply(Source.fromFile(new File(args(0))).getLines())


    println("Type\tPrecision\tRecall\tF1")
    println("Macro-Averaged\t" + prec + "\t" + rec + "\t" + f1)
    for ((rel, p, r, f1) <- perRel) {
      println(rel + "\t" + p + "\t" + r + "\t" + f1)
    }
  }

  type Precision = Double
  type Recall = Double
  type F1 = Double
  type Relation = String

  def apply(lines: Iterator[String]): (Precision, Recall, F1, (Seq[(Relation, Precision, Recall, F1)])) = {
    val buf = new ListBuffer[(Relation, Precision, Recall, F1)]

    var lastRelation: String = null
    var accumPrec: Precision = 0.0
    var accumRec: Recall = 0.0
    var accumF1: F1 = 0.0
    var nfolds = 0

    lines.foreach(l => {

      getRealtion(l) match {

        case Some(relation) => {
          if (lastRelation != null) {
            buf.append((lastRelation,
              safeDiv(accumPrec, nfolds), safeDiv(accumRec, nfolds), safeDiv(accumF1, nfolds)))
            accumPrec = 0.0
            accumRec = 0.0
            accumF1 = 0.0
            nfolds = 0
          }
          lastRelation = relation
        }

        case None => {
          Line(l) match {

            case Some((prec, rec, f1)) => {
              accumPrec += prec
              accumRec += rec
              accumF1 += f1
              nfolds += 1
            }

            case None => {}
          }
        }
      }
    })

    (macroAvg(buf.map(x => x._2)),
      macroAvg(buf.map(x => x._3)),
      macroAvg(buf.map(x => x._4)),
      buf.toSeq)
  }

  def macroAvg(prefs: Seq[Double]) = prefs.fold(0.0)(_ + _) / prefs.size.toDouble

  def getRealtion(l: String): Option[Relation] = {
    val bits = l.split("running multi_svm for relation: ")
    if (bits.size == 1)
      None
    else
      Some(bits(bits.size - 1).replace("_featureized_data folds:", ""))
  }

  def safeDiv(num: Double, denom: Double): Double = {
    if (denom == 0.0) 0.0
    else num / denom
  }


  object Line {
    val regPrec = "precision: [\\.0-9]*".r
    val regRec = "recall: [\\.0-9]*".r
    val regF1 = "f1: [\\.0-9]*".r

    def _get(reg: Regex, line: String): Option[Double] = {
      reg.findFirstIn(line) match {
        case Some(v) =>
          try {
            Some(v.split(" ")(1).toDouble)
          } catch {
            case e: Exception => None
          }
        case None => None
      }
    }


    def apply(line: String): Option[(Precision, Recall, F1)] = {
      val (prec, rec, f1) = (_get(regPrec, line), _get(regRec, line), _get(regF1, line))
      prec match {
        case Some(p) => {
          rec match {
            case Some(r) => f1 match {
              case Some(f) => Some(p, r, f)
              case _ => None
            }
            case _ => None
          }
        }
        case _ => None
      }
    }
  }

}