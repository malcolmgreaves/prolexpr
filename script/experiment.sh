#!/bin/bash

CP=".:bin/:lib/*:conf/"
DIR=$1
shift
DATA=$1
shift

if [ -z "$DIR" -o -z "$DATA" ]
then
    echo Usage:
    echo     $0 projectDirectory datafile [options to pass]
    exit 1
fi

ARGS=$DIR/programFiles.arg

if [ ! -f $ARGS ]
then
    echo ---
    echo --- Compiling $DIR first:
    echo ---
    echo ${0%experiment.sh}compile.sh $DIR
    ${0%experiment.sh}compile.sh $DIR
    if [ $? -ne 0 ]
    then
	exit 1
    fi
fi

PROGRAM=`cat $DIR/programFiles.arg`

echo ---
echo --- Cooking $DATA:
echo ---
JAVA_ARGS="-server -Xmx14G"
PROVE="--prover dpr:1E-6"
THREADS="--threads 7"
echo java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.MultithreadedExampleCooker --programFiles ${PROGRAM%:} --data $DATA --output ${DATA%.data}.cooked $PROVE $THREADS
java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.MultithreadedExampleCooker --programFiles ${PROGRAM%:} --data $DATA --output ${DATA%.data}.cooked $PROVE $THREADS
if [ $? -ne 0 ]
then
	echo "*** cooking FAILED ! ***"
	exit 1
fi

echo ---
echo --- Trainng $DATA
echo ---
EPOCHS="--epochs 10"
PARAMS="${DATA%.data}.params"
echo java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.trove.Trainer ${DATA%.data}.cooked $PARAMS $EPOCHS
java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.trove.Trainer ${DATA%.data}.cooked $PARAMS $EPOCHS
if [ $? -ne 0 ]
then
	echo "*** training FAILED ! ***"
	exit 1
fi

echo ---
echo --- Testing $DATA
echo ---
echo java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.Tester --programFiles ${PROGRAM%:} --test $DATA --params $PARAMS
java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.Tester --programFiles ${PROGRAM%:} --test $DATA --params $PARAMS
if [ $? -ne 0 ]
then
	echo "*** testing FAILED ! ***"
	exit 1
fi

echo ---
echo --- Querrying $DATA
echo ---
echo java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.QueryAnswerer --programFiles ${PROGRAM%:} $PROVE --queries ${DATA%.data}.queries --output ${DATA%.data}.answers --params $PARAMS
java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.QueryAnswerer --programFiles ${PROGRAM%:} $PROVE --queries ${DATA%.data}.queries --output ${DATA%.data}.answers --params $PARAMS
if [ $? -ne 0 ]
then
	echo "*** querrying FAILED ! ***"
	exit 1
fi

echo -------------------------------------
echo --- Experiment :: Done with $DATA ---
echo -------------------------------------

