package pipeln

//
//
//

import (
	"fmt"
	"strings"
	"testing"
)

func TestExec(t *testing.T) {
	PosMistakeCost := 10.0
	UseBias := true
	CacheSize := 500 // MB
	EpsilonTerm := 0.00001
	Kern := PolynomialKern{3.0, 0.0, 2}
	c := MakeSVMConfig(PosMistakeCost, UseBias, CacheSize, EpsilonTerm, Kern)

	TrainFi := "InputTrainFile"
	ModelFi := "[Input/Output]ModelFi"
	l := Learning{TrainFi, ModelFi}

	TestFi := "InputTestFile"
	PredictionFi := "OuputPredictionsFile"
	cl := Classification{TestFi, ModelFi, PredictionFi}

	fmt.Printf("LEARN::\n\t%+v\n", strings.Join(ExecSVMLightLearn(c, l).Args, " "))
	fmt.Printf("CLASSIFY::\n\t%+v\n", strings.Join(ExecSVMLightClassify(c, cl).Args, " "))
}

func TestConfig(t *testing.T) {
	PosMistakeCost := 10.0
	UseBias := true
	CacheSize := 500 // MB
	EpsilonTerm := 0.00001
	for _, Kern := range Kernels() {
		c := MakeSVMConfig(PosMistakeCost, UseBias, CacheSize, EpsilonTerm, Kern)
		fmt.Printf("%s\n", c)
	}
}

func Kernels() []Kernel {
	return []Kernel{LinearKern{}, PolynomialKern{3.0, 0.0, 2},
		RBFKern{1.5}, SigmoidTanhKern{4.2, -5}}
}

func TestKernel(t *testing.T) {
	have := []bool{false, false, false, false}
	for _, k := range Kernels() {
		switch k.(type) {
		case LinearKern:
			have[0] = true
		case RBFKern:
			have[1] = true
		case PolynomialKern:
			have[2] = true
		case SigmoidTanhKern:
			have[3] = true
		}
	}
	for _, h := range have {
		if !h {
			t.Fail()
		}
	}
}

// force type-checking

var (
	_ Kernel     = LinearKern{}
	_ Kernel     = RBFKern{}
	_ Kernel     = PolynomialKern{}
	_ Kernel     = SigmoidTanhKern{}
	_ OptionArgs = SVMConfig{}
	_ OptionArgs = simpleOptArgs([]string{})
	_ OptionArgs = Learning{}
	_ OptionArgs = Classification{}
)
