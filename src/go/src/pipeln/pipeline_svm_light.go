package pipeln

import (
	"os/exec"
)

const (
	SVM_LEARN    = "svm_learn"
	SVM_CLASSIFY = "svm_classify"
)

func ExecSVMLightLearn(c SVMConfig, l Learning) *exec.Cmd {
	return exec.Command(SVM_LEARN, JoinArgs(c, l).Args()...)
}

func ExecSVMLightClassify(cl Classification) *exec.Cmd {
	return exec.Command(SVM_CLASSIFY, cl.Args()...)
}
