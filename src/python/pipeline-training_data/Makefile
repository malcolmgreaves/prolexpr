
KBP2010:
	python annotation_2_lp-training.py 2010 ${HOME}/data/kbp/TAC-KBP-2010/TAC_2010_KBP_Evaluation_Slot_Filling_Annotation/data/tac_2010_kbp_evaluation_slot_filling_annotation.tab ${HOME}/data/kbp/TAC-KBP-2010/TAC_2010_KBP_Evaluation_Slot_Filling_Annotation/data/tac_2010_kbp_evaluation_slot_filling_queries.xml ${HOME}/data/kbp/TAC-KBP-2010/TAC_2010_KBP_Assessment_Results_V1.1/data/ ${HOME}/data/kbp/TAC-KBP-2010/TAC-KBP-2010_allfacts.train true false


KBP2012:
	python annotation_2_lp-training.py 2012 ${HOME}/data/kbp/TAC-KBP-2012/TAC_2012_KBP_English_Regular_Slot_Filling_Evaluation_Annotations/data/tac_2012_kbp_english_regular_slot_filling_evaluation_annotations.tab ${HOME}/data/kbp/TAC-KBP-2012/TAC_2012_KBP_English_Regular_Slot_Filling_Evaluation_Annotations/data/tac_2012_kbp_english_regular_slot_filling_evaluation_queries.xml ${HOME}/data/kbp/TAC-KBP-2012/TAC_2012_KBP_English_Regular_Slot_Filling_Assessment_Results_V1.2/data ${HOME}/data/kbp/TAC-KBP-2012/TAC-KBP-2012_allfacts.train true false


all: KBP2010 KBP2012
	sort -k1 -u ${HOME}/data/kbp/TAC-KBP-2010/TAC-KBP-2010_allfacts.train ${HOME}/data/kbp/TAC-KBP-2012/TAC-KBP-2012_allfacts.train > ${HOME}/data/kbp/TAC-KBP-2010+2012_data.train
	wc -l ${HOME}/data/kbp/TAC-KBP-2010+2012_data.train