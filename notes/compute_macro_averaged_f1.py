import fileinput, string

def parse(l):
  if "[test fold " in l:
    if "f1:" in l:
      f1 = l.split("f1: ")[1]
      if f1 != "NaN":
        return float(f1)
      else:
        return 0.0
  return None

notNone = lambda l: l != None

def safeFloatDiv(num,denom):
	if denom == 0:
		return 0.0
	return num/float(denom)

def perRelationF1(lines):
	lastRelation = None
	accumF1 = 0.0
	nfolds = 0
	for l in lines:
		if "running multi_svm for relation: " in l:
			if lastRelation != None:
				print lastRelation+"\t"+str(safeFloatDiv(accumF1,nfolds))
			lastRelation = string.replace(l.split("running multi_svm for relation: ")[-1], "_featureized_data folds:", "")
			accumF1 = 0.0
			nfolds = 0
		else:
			possibleF1 = parse(l)
			if possibleF1 != None:
				accumF1 += possibleF1
				nfolds += 1
	if lastRelation != None:
		print lastRelation+"\t"+str(safeFloatDiv(accumF1,nfolds))
#####


lines = [l.strip() for l in fileinput.input()]

f1s = filter(notNone, map(parse, lines))

print "Micro averaged F1\t"+str(sum(f1s) / len(f1s))

perRelationF1(lines)