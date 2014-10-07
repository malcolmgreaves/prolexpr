package prolexpr.classify

import nak.data.Example
import scala.collection.mutable
import java.io._
import nak.data.FeatureObservation
import scala.Some
import prolexpr.classify.TextProcessStructs.Document
import java.util.zip.{GZIPOutputStream, GZIPInputStream}

/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object IO {
  val log = mkLogger("IO")

  def documentWriter(w: BufferedWriter) = (d: Document) => {
    d.sentences.foreach(s => {
      var ti = 0
      s.tokens.foreach(t => {
        ti += 1
        w.write(ti + "\t" + t.word + "\t" + t.word + "\t" + t.tagPOS + "\t" + t.tagPOS + "\t" + s.docsentID + ":" + t.tagNER + "\n")
      })
      w.write("\n")
    })
  }

  @throws[IllegalArgumentException]
  def selectLabelOutputFormat(name: String): LabelOutputFormat[_, _] = {
    if (name.equals("SVMLightLabelOutputFormat") || name.equalsIgnoreCase("svmlight")) {
      new SVMLightLabelOutputFormat()
    } else if (name.equals("SVMMulticlassLabelOutputFormat") || name.equalsIgnoreCase("svmmulticlass")) {
      new SVMMulticlassLabelOutputFormat()
    } else if (name.equalsIgnoreCase("pair") || name.equalsIgnoreCase("PairOutputFormat")) {
      new PairOutputFormat()
    } else {
      throw new IllegalArgumentException("unknown label output format: " + name)
    }
  }

  class PairOutputFormat extends LabelOutputFormat[String, String] {
    def setupFeatureIDLookup(): ((FeatureObservation[String]) => String, (String) => Unit) = {
      ((x) => x.feature, (x) => {})
    }

    def setupClassIDLookup(): ((Example[String, Seq[FeatureObservation[String]]]) => String, (String) => Unit) = {
      ((ex) => ex.id, (x) => {})
    }

    def setupWriterLookup(outputDir: String) = IO.setupWriterLookup("_pair_data", outputDir, doGzip = true)

    def writeFormat(w: BufferedWriter, pair: String, features: Seq[(String, Double)]): Unit = {
      w.write(pair + "\t" + features.map((x) => x._1 + " " + x._2).mkString("\t") + "\n")
    }
  }

  abstract class LabelOutputFormat[C, F <% Ordered[F]] {
    def write(trainingExamples: TraversableOnce[Example[String, Seq[FeatureObservation[String]]]], outputDir: String) = {

      val (featureIDLookup, fcloser) = setupFeatureIDLookup()
      val (classIDLookup, ccloser) = setupClassIDLookup()
      val (writerLookup, wcloser) = setupWriterLookup(outputDir)

      val featFreq = collection.mutable.Map.empty[F, Double] withDefaultValue 0.0

      trainingExamples.foreach((ex) => {
        if (ex.features.size > 0) {
          // stores the feature - magnitude pairings
          featFreq.clear()
          // compute an index for each label
          ex.features.map((feat) => (featureIDLookup(feat), feat.magnitude))
            // while aggregating duplicate features
            .foreach(f => featFreq(f._1) += f._2)
          // lastly, features must be in increasing order on feature ID
          val features = featFreq.toSeq.sortBy(x => x._1)

          val classID = classIDLookup(ex)

          writeFormat(writerLookup(ex.label), classID, features)
        }
      })

      wcloser()
      fcloser(outputDir)
      ccloser(outputDir)
    }

    def setupClassIDLookup(): ((Example[String, Seq[FeatureObservation[String]]]) => C, (String) => Unit)

    def setupFeatureIDLookup(): ((FeatureObservation[String]) => F, (String) => Unit)

    def setupWriterLookup(outputDir: String): ((String) => BufferedWriter, () => Unit)

    def writeFormat(w: BufferedWriter, classID: C, features: Seq[(F, Double)])

    override def toString: String = getClass.getName

    def mapCloser(m: mutable.HashMap[_, _], outputDir: String, fileName: String) = {
      val w = new BufferedWriter(new FileWriter(new File(outputDir, fileName)))
      m.foreach((fi) => {
        w.write(fi._1 + "\t" + fi._2 + "\n")
      })
      w.close()
      log.info("done writing " + fileName)
    }
  }

  trait SVMLabelOutputFormat extends LabelOutputFormat[Int, Long] {
    def setupFeatureIDLookup() = {
      // maps each individual feature to a unique integer
      val features = new mutable.HashMap[String, Long]()
      var nextFeatureID = 0L

      val lookup = (feat: FeatureObservation[String]) => {
        features.get(feat.feature) match {
          case Some(featureID) => featureID
          case None => {
            nextFeatureID += 1
            features.put(feat.feature, nextFeatureID)
            nextFeatureID
          }
        }
      }

      (lookup, super.mapCloser(features, _, "feature_index"))
    }

    def writeFormat(w: BufferedWriter, classID: Int, features: Seq[(Long, Double)]): Unit = {
      w.write(classID.toString)
      var ii = 0
      while (ii < features.length) {
        val f = features(ii)
        w.write(" " + f._1 + ":" + f._2)
        ii += 1
      }
      w.write("\n")
    }
  }

  class SVMLightLabelOutputFormat extends SVMLabelOutputFormat {
    def setupClassIDLookup() = {
      val labelDetermine = (e: Example[String, Seq[FeatureObservation[String]]]) => {
        if (e.label.equals(TrainingDataMaker.Label.NOT_RELATED)) {
          -1
        } else {
          +1
        }
      }: Int

      (labelDetermine, (String) => {
        log.debug("ignoring request to close out and write the labels for a binary classifier")
      })
    }

    def setupWriterLookup(outputDir: String) = IO.setupWriterLookup("_featureized_data", outputDir)
  }

  def setupWriterLookup(suffix: String, outputDir: String, doGzip: Boolean = false) = {
    val label2writer = new mutable.HashMap[String, BufferedWriter]()

    val writer = (label: String) => if (doGzip) {
      new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
        new FileOutputStream(new File(outputDir, label + suffix)))))
    } else {
      new BufferedWriter(new FileWriter(new File(outputDir, label + suffix)))
    }

    val selector = (label: String) => {
      label2writer.get(label) match {
        case Some(w) => w
        case None => {
          val w = writer(label)
          label2writer.put(label, w)
          w
        }
      }
    }: BufferedWriter

    val closer = () => {
      label2writer.foreach((lw) => {
        lw._2.close()
        log.info("completed writing training data for label:  " + lw._1)
      })
    }

    (selector, closer)
  }

  class SVMMulticlassLabelOutputFormat extends SVMLabelOutputFormat {
    def setupClassIDLookup() = {
      val classes = new mutable.HashMap[String, Int]()
      var nextLabelID = 0
      val lookup = (e: Example[String, Seq[FeatureObservation[String]]]) => {
        classes.get(e.label) match {
          case Some(classID) => classID
          case None => {
            nextLabelID += 1
            classes.put(e.label, nextLabelID)
            nextLabelID
          }
        }
      }: Int

      (lookup, super.mapCloser(classes, _, "classes_index"))
    }

    def setupWriterLookup(outputDir: String) = {
      val w = new BufferedWriter(new FileWriter(new File(outputDir, "labeled_featureized_data")))
      ((_) => w, w.close)
    }
  }

  def openWriter(locationOrStdout: String): BufferedWriter = {
    if (locationOrStdout.equalsIgnoreCase("stdout")) {
      log.info("writing to STDOUT")
      new BufferedWriter(new OutputStreamWriter(System.out))
    } else {
      log.info("writing to: " + locationOrStdout)
      new BufferedWriter(new FileWriter(locationOrStdout))
    }
  }

  def mkFileWalker = (topdir: File) => {
    val fileWalker = {
      if (!topdir.exists()) {
        throw new IOException("input does not exist: " + topdir)
      }
      if (topdir.isFile) {
        Array(topdir).toIterator
      } else {
        topdir.listFiles.toIterator.filter(_.isFile).filter(_ != null)
      }
    }
    var fi = 0
    val nFiles = {
      if (topdir.isFile) {
        1
      } else {
        topdir.listFiles.toIterator.filter(_.isFile).filter(_ != null).toList.size
      }
    }
    (fileWalker, nFiles)
  }


  def lines(fi: File): Iterator[String] = {
    val r = if (fi.getName.endsWith(".gz")) {
      new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(fi))))
    } else {
      new BufferedReader(new FileReader(fi))
    }
    lines(r)
  }

  def lines(r: BufferedReader): Iterator[String] = {
    new BufferedIterator[String] {
      var line = advance()
      var set = false

      def advance() = r.readLine()

      def hasNext: Boolean = {
        val has = line != null
        if (!has) r.close()
        has
      }

      def next(): String = {
        val l = new String(line)
        line = advance()
        l
      }

      def head: String = new String(line)

    }
  }

  def lines(r: Reader): Iterator[String] = lines(new BufferedReader(r))

  def dump(fi: File, l: String) = {
    val w = new BufferedWriter(new FileWriter(fi))
    w.write(l)
    w.close()
  }

  def dump(fi: File, l: TraversableOnce[String]) = {
    val w = new BufferedWriter(new FileWriter(fi))
    l.foreach(x => w.write(x))
    w.close()
  }
}