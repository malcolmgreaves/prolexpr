package prolexpr.classify

import nak.core.{FeaturizedClassifier, LiblinearClassifier}
import nak.liblinear._
import java.io.{FileInputStream, FileWriter, BufferedWriter, File}
import nak.data.{Featurizer, Example}
import nak.NakContext
import nak.liblinear.LiblinearConfig
import nak.util.{ConfusionMatrix, CrossValidation}
import akka.actor.{ActorSystem, Props, ActorRef, Actor}
import akka.routing.RoundRobinRouter
import java.util.Properties
import prolexpr.classify.TextFeatures.CompleteSlotfillerFeaturizer
import prolexpr.classify.CandGen.Slotfill

/**
 *
 * Author: Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object ProcessedTextClassifier {
  val log = mkLogger(getClass.getName)

  case class Trainer(featR: Featurizer[Slotfill, String]) {
    def apply(examples: Seq[Example[String, Slotfill]], config: LiblinearConfig, outputDir: String) = {
      log.info("configuration: " + config)
      log.info("training classifier")
      val classifier = NakContext.trainClassifier(config, featR, examples)

      val savedClassifier = new File(outputDir, "saved_classifier-" + config.toString)
      log.info("saving learned classifier to: " + savedClassifier)
      NakContext.saveClassifier(classifier, savedClassifier.toString)

      val savedConfig = new File(outputDir, "saved_config-" + config.toString)
      log.info("saving training configuration to: " + savedConfig)
      val w1 = new BufferedWriter(new FileWriter(savedConfig))
      w1.write(config.toString)
      w1.close()

      new LiblinearClassifier with FeaturizedClassifier[String, Slotfill] {
        val model = classifier.asInstanceOf[LiblinearClassifier].model
        val lmap = classifier.asInstanceOf[LiblinearClassifier].lmap
        val fmap = classifier.asInstanceOf[LiblinearClassifier].fmap
        val featurizer = classifier.featurizer

        override def apply(context: Array[(Int, Double)]): Array[Double] = {
          val ctxt = context.map(c => new FeatureNode(c._1, c._2).asInstanceOf[Feature])
          val labelScores = Array.fill(numLabels)(0.0)
          if (model.isProbabilityModel) {
            Linear.predictProbability(model, ctxt, labelScores)
          } else {
            Linear.predictValues(model, ctxt, labelScores)
          }
          labelScores
        }
      }
    }: FeaturizedClassifier[String, Slotfill]
  }

  def main(args: Array[String]) {
    if (args.length < 3) {
      log.fatal("Incorrect arguments, need:" + "\n\t[0] output dir" +
        "\n\t[1] properties config file\n\t[2] input dir")
      exit()
    }

    val (outputDir, propsFi, inputDir) = (args(0), args(1), args(2))
    log.info("output dir:   " + outputDir)
    log.info("properies:    " + propsFi)
    log.info("input dir:    " + inputDir)

    if (new File(outputDir).exists()) {
      log.fatal("output dir already exists!")
      exit()
    }

    val props = new Properties()
    props.load(new FileInputStream(propsFi))

    val nThreadsWorkers = props.getProperty("nthreads", "1").toInt

    val solvers = props.getProperty("solvers", "MCSVM_CS,L2R_LR").split(",").map((x) => SolverType.valueOf(x))
    val costs = props.getProperty("costs", "1.0").split(",").map(_.toDouble)
    val epsilons = props.getProperty("epsilons", "0.0003,0.03,3.0").split(",").map(_.toDouble)
    val verbose = props.getProperty("verbose", "false").toBoolean

    val downsampleNotReleated = props.getProperty("downsampleNotReleated", "true").toBoolean

    val folds = props.getProperty("folds", "3").toInt
    if (folds < 1) {
      log.fatal("# cross validation folds (\"folds\") must be positive")
      exit()
    }

    val ngram = props.getProperty("ngram", "2").toInt
    val skip = props.getProperty("skip", "4").toInt
    if (ngram < 1 || skip < 0) {
      log.fatal("ngram must be > 0 and skip must be non-negative")
      exit()
    }
    log.info("FEATUREIZING configuration:\n\t" +
      "ngram size (for skip + Left and Right word features):  " + ngram + "\n\t" +
      "skip ngram size (for inner features):                  " + skip + "\n\t")

    // load labels
    val labelsFi = props.getProperty("labels")
    log.info("Loading labels from:         " + labelsFi)
    val (labels, _) = TrainingDataMaker.Label.load(new File(labelsFi))

    // defer making output dir until configuration loading goes according to plan
    if (!new File(outputDir).mkdirs()) {
      log.fatal("could not make output dir")
      exit()
    }

    // featureize data and create labeled training data
    val ngFeat = new CompleteSlotfillerFeaturizer(ngram, skip)

    log.info("TRAINING DATA MAKER configuration:\n\t" +
      "filtering and down-sampling NOT_RELATED examples?:     " + downsampleNotReleated)
    val trainContext = new TrainingDataMaker.Context(labels,
      TextProcessStructs._maltParseFormatExtract, downsampleNotReleated)
    val trainingExamples = trainContext.makeTrainingExamples(new File(inputDir))

    val trainer = new Trainer(ngFeat)
    // train classifiers using cross validation
    log.info("# CV folds:        " + folds)
    log.info("solvers:           " + "[" + solvers.deep.mkString(", ") + "]")
    log.info("costs:             " + "[" + costs.deep.mkString(", ") + "]")
    log.info("epsilons:          " + "[" + epsilons.deep.mkString(", ") + "]")
    log.info("# worker threads:  " + nThreadsWorkers)

    val system = ActorSystem()
    val listener = system.actorOf(Props[Listener], name = "Listener")
    val master = system.actorOf(Props[Actor](
      new CVMaster(nThreadsWorkers, listener, trainingExamples, trainer)))

    master ! CVEvaluate(solvers, costs, epsilons, folds, outputDir)

    log.info("done")
  }


  //

  sealed trait CVMsg

  case class CVWork(config: LiblinearConfig, folds: Int, outputDir: String,
                    id: Int, maxID: Int) extends CVMsg

  case class CVResult(cf: ConfusionMatrix[String, Slotfill], msg: String,
                      id: Int, maxID: Int) extends CVMsg

  class CVWorker(trainExamples: Seq[Example[String, Slotfill]],
                 trainer: Trainer) extends Actor {
    override def receive = {
      case CVWork(config, folds, outputDir, id, maxID) => {
        val start = System.currentTimeMillis()

        val cf = CrossValidation.crossValidation(trainExamples, folds)(
          (examples) => trainer.apply(examples.toSeq, config, outputDir))

        var w = new BufferedWriter(new FileWriter(new File(outputDir,
          "training_error_confusion_matrix-" + config.toString)))
        w.write(cf.toString())
        w.close()

        w = new BufferedWriter(new FileWriter(new File(outputDir,
          "detailed_output-" + config.toString)))
        w.write(cf.detailedOutput)
        w.close()

        sender ! CVResult(cf, config.toString + "\tDURATION: " +
          (System.currentTimeMillis() - start) + " ms\tMEASUREMENTS:\n" + cf.measurements,
          id, maxID)
      }
    }
  }

  case class CVEvaluate(solverTypes: Seq[SolverType], costs: Seq[Double],
                        epsilons: Seq[Double], folds: Int, outputDir: String) extends CVMsg

  class CVMaster(nWorkers: Int, listener: ActorRef,
                 trainExamples: Seq[Example[String, Slotfill]],
                 trainer: Trainer) extends Actor {

    val workerRouter = context.actorOf(
      Props[Actor](new CVWorker(trainExamples, trainer)).withRouter(RoundRobinRouter(nWorkers)),
      name = "WorkerRouter")

    override def receive = {
      case CVEvaluate(solvers, costs, epsilons, folds, outputDir) => {
        var id = 0
        val maxID = solvers.size * costs.size * epsilons.size
        for (solverType <- solvers;
             cost <- costs;
             epsilon <- epsilons) {
          id += 1
          val config = LiblinearConfig.apply(solverType, cost, epsilon, false)
          workerRouter ! CVWork(config, folds, outputDir, id, maxID)
        }
      }

      case CVResult(cf, msg, id, maxID) => {
        log.info(msg)
        if (id == maxID) {
          listener ! Done
        }
      }
    }
  }

  case object Done extends CVMsg

  class Listener extends Actor {
    override def receive = {
      case Done => {
        log.info("completed work, shutting down...")
        context.system.shutdown()
      }
    }
  }


}
