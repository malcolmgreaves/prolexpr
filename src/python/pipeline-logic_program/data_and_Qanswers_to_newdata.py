import sys,string

def getPosNeg(data):
	name2posorneg = {}
	for l in open(data):
		bits = l.strip().split("\t")
		match = bits[0].split("X)")[0]
		name2posorneg[match] = {}
		name2posorneg[match][True] = set()
		name2posorneg[match][False] = set()


		for b in bits[1:]:
			if b[0] == "+":
				name2posorneg[match][True].add(string.replace(b[1:-1], match, ""))
			elif b[0] == "-":
				name2posorneg[match][False].add(string.replace(b[1:-1], match, ""))
			else:
				print "[skip] MALFORMED part: \"%s\"\n from LINE: \"%s\"" % (b, l)

	return name2posorneg

def samplePosAndNeg(name2posorneg,answers):
	sample = {}
	name = ""
	for l in open(answers):
		if l[0] == "#":
			for n in name2posorneg:
				if n in l:
					using = name2posorneg[n]
					sample[n] = {}
					sample[n][True] = []
					sample[n][False] = []
					name = n
					break
		else:
			answer = l.strip().split("\t")[-1].split("=")[-1][2:-1]
			if answer in using[True]:
				sample[name][True].append(answer)
			else:
				sample[name][False].append(answer)

	return sample

if __name__=="__main__":
	data = sys.argv[1]
	answers = sys.argv[2]
	newdata = sys.argv[3]
	print "Data:             %s" % data
	print "Answers:          %s" % answers
	print "New Data [write]: %s" % newdata

	name2posorneg = getPosNeg(data)
	sample = samplePosAndNeg(name2posorneg,answers)
	newdata = open(newdata,'wt')
	for name in sample:
		if len(sample[name][True]) == 0 or len(sample[name][False]) == 0:
			print "Need at least 1 positive and 1 negative example for %s, not %d and %d (respectively)" % (name, len(sample[name][True]),len(sample[name][False]))
		else:
			print "sampling: %s",name
			print len(sample[name][True]),"positive examples"
			print len(sample[name][False]),"negative examples",
			
			newdata.write("%sX)" % name)
			
			for p in sample[name][True]:
				newdata.write("\t+%s%s)" % (name, p))

			negs = sample[name][False]
			sampled = 0
			for exp in xrange(len(negs)):
				index = exp**2
				if index < len(negs):
					newdata.write("\t-%s%s)" % (name,negs[index]))
					sampled += 1
				else:
					break
			print "...sampled %d negative examples" % sampled
			newdata.write("\n")

	newdata.close()
	print "done"