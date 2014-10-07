import fileinput, collections,sys

def parse(l):
	bits = l.strip().split("\t")
	if bits[1] == "+":
		isPosIndex = 0
	else:
		isPosIndex = 1
	return bits[0],isPosIndex,(bits[2],bits[3])


rel2pos2examples = collections.defaultdict(lambda:[set(),set()])

for l in fileinput.input():
	rel,isPosIndex,(query,answer) = parse(l)
	qa = "%s\t%s" % (query,answer)
	rel2pos2examples[rel][isPosIndex].add(qa)

noprint = 0
for rel in rel2pos2examples:
	for qa in rel2pos2examples[rel][0]:
		print "%s\t+\t%s" % (rel,qa)
	for qa in rel2pos2examples[rel][1]:
		if qa not in rel2pos2examples[rel][0]:
			print "%s\t-\t%s" % (rel,qa)
		else:
			noprint += 1

sys.stderr.write("Labeled %d incosistent examples as positive\n" % noprint)