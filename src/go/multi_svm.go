package main

import (
	"bufio"
	"bytes"
	"flag"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path"
	"pipeln"
	"runtime"
	"strconv"
	"strings"
	"sync"
)

var (
	j = flag.Float64("j", 10.0, "Positive Misclassification Cost (-j svm light)")
	b = flag.Int("b", 1, "Use biased hyperplane, 0 or 1 (-b svm light)")
	m = flag.Int("m", 100, "Size of cache for kernel eval, in MB (-m svm light)")
	e = flag.Float64("e", 0.001, "Error termination criterion (epsilon, -e svm light)")

	labeledInput = flag.String("labeled", "", "( ',' sep.) File paths of lableed CV folds, SVM light fmt. training data")
	workingDir   = flag.String("working", "", "Path to (non-existant) working dir.")
	threads      = flag.Int("threads", 1, "# threads for parallel model training & evaluation")
)

type LogWriter struct{}

func (LogWriter) Write(p []byte) (n int, err error) {
	log.Printf(string(p))
	return len(p), nil
}

var lw = LogWriter{}

func main() {
	flag.Parse()

	if len(*labeledInput) == 0 || len(*workingDir) == 0 {
		log.Fatal("labeled input and working directory options MUST be specified !")
	}
	if _, err := os.Stat(*workingDir); os.IsExist(err) {
		log.Fatal(fmt.Sprintf("working dir cannot already exist! %s", *workingDir))
	}
	if err := os.Mkdir(*workingDir, 0777); err != nil {
		log.Fatal(fmt.Sprintf("could not create working directory: %s :: %v", *workingDir, err))
	}

	// need twice so we can get the actual set #
	runtime.GOMAXPROCS(*threads)
	*threads = runtime.GOMAXPROCS(*threads)

	// make the SVM configuration
	bias := true
	if *b == 0 {
		bias = false
	}
	// TODO kernel parsing
	kern := pipeln.LinearKern{}
	svmConfig := pipeln.MakeSVMConfig(*j, bias, *m, *e, kern)

	labeledFoldFis := strings.Split(*labeledInput, ",")

	log.Printf("Using these options for svm_light:                 %s", svmConfig)
	log.Printf("Learning & Evaluating on labeled data [%d folds]:  %s", len(labeledFoldFis), *labeledInput)
	log.Printf("# threads for parallel train / eval.:              %d", *threads)
	log.Printf("Working Dir [Models,Predictions,CV folds]:         %s", *workingDir)

	log.Printf("currently using LINEAR KERNEL...")

	rewrite := RewriteMaker(labeledFoldFis)
	santizie := func(s string) string {
		s = strings.Replace(s, "(MISSING)", "", -1)
		s = strings.Replace(s, "%", "", -1)
		s = strings.Replace(s, "!", "", -1)
		return strings.TrimSpace(s)
	}
	wg := sync.WaitGroup{}

	for fi, tfold := range labeledFoldFis {
		wg.Add(1)
		go func(foldIndex int, testFold string) {
			defer wg.Done()
			log.Printf("working on fold %d [%s]", foldIndex, testFold)
			// use working dir as temp space to combine the (k-1) other
			// folds into a single file for training
			trainName := rewrite(foldIndex)
			// train using svm_learn
			log.Printf("training model for test fold %d", foldIndex)
			modelFi := path.Join(*workingDir, fmt.Sprintf("model_for_testing_%d", foldIndex))
			l := pipeln.Learning{path.Join(*workingDir, trainName), modelFi}
			learnCMD := pipeln.ExecSVMLightLearn(svmConfig, l)
			learnCMD.Stderr = lw
			if err := learnCMD.Run(); err != nil {
				log.Printf("[test fold %d -- skip] error learning: %v",
					foldIndex, err)
			} else {
				// classify and evaluate using svm_classify
				log.Printf("evaluating model on test fold %d", foldIndex)
				predictionsFi := path.Join(*workingDir, fmt.Sprintf("predictions_for_testing_%d", foldIndex))
				cl := pipeln.Classification{testFold, modelFi, predictionsFi}
				classifyCMD := pipeln.ExecSVMLightClassify(cl)
				classifyCMD.Stderr = lw
				predictLogName := path.Join(*workingDir, fmt.Sprintf("predict_log_test-fold_%d", foldIndex))
				predictLog, err := os.Create(predictLogName)
				if err != nil {
					log.Printf("[test fold %d -- skip] error creating log for predictions: %v", err)
				} else {
					classifyCMD.Stdout = predictLog
					if err = classifyCMD.Run(); err != nil {
						log.Printf("[test fold %d -- skip] error classifying: %v", foldIndex, err)
					}
					predictLog.Close()
				}
				// evaluation
				evalCmd := exec.Command("tail", []string{"-n", "2", predictLogName}...)
				buff := new(bytes.Buffer)
				evalCmd.Stdout = buff
				evalCmd.Run()
				eval := strings.Split(buff.String(), "\n")
				precrec := strings.Split(strings.Split(eval[1], ": ")[1], "/")
				prec, err := strconv.ParseFloat(santizie(precrec[0]), 64)
				if err != nil {
					log.Printf("[test fold %d] error parsing precision: %v", foldIndex, err)
				}
				rec, err := strconv.ParseFloat(santizie(precrec[1]), 64)
				if err != nil {
					log.Printf("[test fold %d] error parsing recall: %v", foldIndex, err)
				}
				f1 := (2.0 * prec * rec) / (prec + rec)
				log.Printf("[test fold %d] precision: %f  recall: %f  f1: %f",
					foldIndex, prec, rec, f1)
			}
		}(fi, tfold)
	}
	wg.Wait()

	log.Printf("done")
}

func RewriteMaker(labeledFoldFis []string) func(int) string {
	return func(notThisFold int) string {
		trainName := fmt.Sprintf("training_for_test_%d", notThisFold)
		wFi, err := os.Create(path.Join(*workingDir, trainName))
		if err != nil {
			log.Fatal("couldn't create training file for testing fold %d: %v", notThisFold, err)
		}
		defer wFi.Close()

		for fi, fold := range labeledFoldFis {
			if fi != notThisFold {
				foldFi, err := os.Open(fold)
				if err != nil {
					log.Fatal("couldn't read from fold file: %s :: %v", fold, err)
				}
				foldR := bufio.NewScanner(foldFi)
				for foldR.Scan() { // strips ending "\n"
					wFi.WriteString(foldR.Text())
					wFi.WriteString("\n")
				}
				foldFi.Close()
			}
		}
		return trainName
	}
}
