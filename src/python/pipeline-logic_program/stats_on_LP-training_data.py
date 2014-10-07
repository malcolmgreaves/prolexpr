import sys, string, collections
import xml.etree.ElementTree as ET

class Count:
	def __init__(self,pos=0,neg=0):
		self.pos = pos
		self.neg = neg

	def __str__(self):
		return "+ %d : - %d" % (self.pos, self.neg)

if __name__=="__main__":
	if len(sys.argv) != 3:
		print "ERROR: need  [0] INPUT  and  [1] OUTPUT  files as arguments"
		exit()

	inputfi = sys.argv[1]
	outputfi = sys.argv[2]
	print "Input:           %s" % inputfi
	print "Output *stat*:   %s" % outputfi
	outputfi = open(outputfi,'wt')

	rel2counts = collections.defaultdict(Count)

	print "--- Calcuating stats"
	for l in open(inputfi):
		relation = l.split("\t")[0].split("(")[0]
		count = rel2counts[relation]
		for c in l:
			if c == "+":


	outputfi.close()
	print "--- done"