#!/bin/bash

CP=".:bin/:lib/*:conf/"
DIR=$1
shift
QUERY=$1
shift

if [ -z "$DIR" -o -z "$QUERY" ]
then
    echo Usage:
    echo     $0 projectDirectory queryfile [options to pass]
    exit 1
fi

ARGS=$DIR/programFiles.arg

if [ ! -f $ARGS ]
then
    echo ---
    echo --- Compiling $DIR first:
    echo ---
    echo ./scripts/compile.sh $DIR
    ./scripts/compile.sh $DIR
    if [ $? -ne 0 ]
    then
	exit 1
    fi
fi

PROGRAM=`cat $DIR/programFiles.arg`
if [ -z "$PROVE" ] 
then
	PROVE="--prover dpr:1E-8"
fi

if [ -z "$THREADS" ]
then
	THREADS="--threads 4"
fi
if [ -z "$JAVA_ARGS" ]
then
	JAVA_ARGS="-Xmx13G -server"
fi

echo ---
echo --- Querrying $QUERY
echo ---
echo java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.QueryAnswerer $THREADS --programFiles ${PROGRAM%:} $PROVE --queries ${QUERY%.queries}.queries --output ${QUERY%.queries}.answers
java $JAVA_ARGS -cp $CP edu.cmu.ml.praprolog.QueryAnswerer --programFiles ${PROGRAM%:} $PROVE --queries ${QUERY%.queries}.queries --output ${QUERY%.queries}.answers
if [ $? -ne 0 ]
then
	echo "*** querrying FAILED ! ***"
	exit 1
fi

echo -------------------------------------
echo --- Experiment :: Done with $QUERY ---
echo -------------------------------------
