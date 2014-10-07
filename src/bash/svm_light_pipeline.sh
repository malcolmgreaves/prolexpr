#!/bin/bash

## declare an array variable
declare -a pos_misclass_costs=("5.0" "10.0" "15.0")
declare -a trains=("TRAIN_1","TRAIN_2","TRAIN_3")
declare -a tests=("FOLD_1","FOLD_2","FOLD_3")

## now loop through the above array
for P_COST in "${pos_misclass_costs[@]}";do
	for TRAIN in "${trains[@]}"; do
		echo "[+ : $P_COST]   Training: $TRAIN"
		echo "time svm_learn -j $P_COST $TRAIN $MODEL > $LOG_TRAIN"
		echo "Evaluating:  $TEST  predicting to: $PREDICT"
		echo "time svm_classify $TEST $MODEL $PREDICT > $LOG_TEST"
		# echo "+ cost: $P_COST `tail -n 1 $LOG_TEST`  F1: `tail -n 1 $LOG_TEST | python f1.py`"
	done
done
