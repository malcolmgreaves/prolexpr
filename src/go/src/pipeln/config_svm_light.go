package pipeln

//
//
//

import (
	"fmt"
	"strconv"
	"strings"
)

type OptionArgs interface {
	Args() []string
}

type simpleOptArgs []string

func (x simpleOptArgs) Args() []string { return x }

func JoinArgs(many ...OptionArgs) OptionArgs {
	n := len(many)
	for _, a := range many {
		n += len(a.Args())
	}
	args := make([]string, 0, n)
	for _, a := range many {
		args = append(args, a.Args()...)
	}
	return simpleOptArgs(args)
}

//
// Holds training file and output model file
//

type Learning struct {
	TrainFi, ModelFi string
}

func (l Learning) Args() []string {
	return []string{l.TrainFi, l.ModelFi}
}

//
// Holds testing file, (previously trained) model file, and output
// predictions file
//

type Classification struct {
	TestFi, ModelFi, PredictFi string
}

func (c Classification) Args() []string {
	return []string{c.TestFi, c.ModelFi, c.PredictFi}
}

//
// An entire SVM light configuration, sans the Learning
// or Classification arguments
//

type SVMConfig struct {
	PosMistakeCost float64
	UseBias        bool
	CacheSize      int
	EpsilonTerm    float64
	Kern           Kernel
}

func MakeSVMConfig(PosMistakeCost float64, UseBias bool, CacheSize int,
	EpsilonTerm float64, Kern Kernel) SVMConfig {

	return SVMConfig{PosMistakeCost, UseBias, CacheSize, EpsilonTerm,
		// Kernels handle their own arguments
		Kern,
	}
}

func (c SVMConfig) Args() []string {
	kernArgs := c.Kern.Args()
	args := make([]string, 0, len(kernArgs)+8)

	// add SVM light arguments
	b := 0
	if c.UseBias {
		b = 1
	}
	args = append(args, "-j", strconv.FormatFloat(c.PosMistakeCost, 'f', -1, 64))
	args = append(args, "-b", strconv.Itoa(b))
	args = append(args, "-m", strconv.Itoa(c.CacheSize))
	args = append(args, "-e", strconv.FormatFloat(c.EpsilonTerm, 'f', -1, 64))
	args = append(args, "-z", "c")

	// add kernal arguments
	for _, arg := range kernArgs {
		args = append(args, arg)
	}

	return args
}

func (c SVMConfig) String() string {
	return strings.Join(c.Args(), " ")
}

//
// Encapsulates kernal function options
//
type Kernel interface {
	OptionArgs
}

type LinearKern struct{}

func (LinearKern) Args() []string {
	return []string{"-t", "0"}
}

type PolynomialKern struct {
	Multiplier float64
	Offset     float64
	Degree     float64
}

func (k PolynomialKern) Args() []string {
	return []string{"-t", "1", "-d", fmt.Sprintf("%v", k.Degree), "-s",
		fmt.Sprintf("%v", k.Multiplier), "-r", fmt.Sprintf("%v", k.Offset)}
}

type RBFKern struct {
	Gamma float64
}

func (k RBFKern) Args() []string {
	return []string{"-t", "2", "-g", fmt.Sprintf("%v", k.Gamma)}
}

type SigmoidTanhKern struct {
	Multiplier float64
	Offset     float64
}

func (k SigmoidTanhKern) Args() []string {
	return []string{"-t", "3", "-s", fmt.Sprintf("%v", k.Multiplier),
		"-r", fmt.Sprintf("%v", k.Offset)}
}
