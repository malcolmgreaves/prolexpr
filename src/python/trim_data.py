import sys, os, string

def process(line):
	bits = line.strip().split("\t")
	relation = bits[0].split("(")[0]
	querytext = bits[0].split(",X)")[0].split("%s("%relation)[0]

	replstr = "%s(%s," % (relation,querytext)
	pos,neg = set(),set()

	for bit in bits[1:]:
		if "+" == bit[0]:
			pos.add(string.replace(bit[1:], replstr, "")[0:-1])
		elif "-" == bit[0]:
			neg.add(string.replace(bit[1:], replstr, "")[0:-1])
		else:
			print "ERROR: no +/- at beginning of example: \"%s\" on line \"%s\"" % (bit,line)

	return "%s\t%s\t%s" % (bits[0], "\t".join(pos), "\t".join(neg)) 

if __name__=="__main__":
	if len(sys.argv) != 3:
		print "ERROR: need  [0] INPUT  and  [1] OUTPUT  DIR as arguments"
		exit()

	inputfi = sys.argv[1]
	outputfi = sys.argv[2]
	print "Input Dir:          %s" % inputfi
	print "Output Dir:         %s" % outputfi

	os.mkdir(outputfi)

	print "--- Reading & Trimming"
	for fi in os.listdir(inputfi):
		print "\t -- %s" % fi
		out = open(os.path.join(outputfi,fi),'wt')
		for line in open(os.path.join(inputfi,fi)):
			out.write("%s\n" % process(line))
		out.close()

	print "--- done"