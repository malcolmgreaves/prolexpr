import fileinput,string

def candidateSentence(name):
	return "candidateSentence(%s,Sentence,Answer)" % name

def candidateQueryCoref(name):
	return "candidateQueryCoref(%s,SentenceQ,SentenceA,QueryRef,Answer)" % name

def candidateAnswerCoref(name):
	return "candidateAnswerCoref(%s,SentenceQ,AnswerRef,SentenceA,Answer)" % name

def convName(name):
	return "w_%s" % string.replace(string.replace(name,",",""), " ","_")

def printProPPRQueries(name):
	name = convName(name)
	print candidateSentence(name)
	print candidateQueryCoref(name)
	print candidateAnswerCoref(name)

for line in fileinput.input():
	printProPPRQueries(line.strip())