import sys, string, collections
import xml.etree.ElementTree as ET

__table = string.maketrans("","")
__most_punctuation = string.replace(string.punctuation, "-","")
__most_punctuation = string.replace(__most_punctuation, ".","")

if __name__=="__main__":
	if len(sys.argv) != 4:
		print "ERROR: need  [0] INPUT  and  [1] OUTPUT  [2] QUERIES  files as arguments"
		exit()

	inputfi = sys.argv[1]
	outputfi = sys.argv[2]
	queries = sys.argv[3]
	print "Input:          %s" % inputfi
	print "Output:         %s" % outputfi
	print "Queries (xml):  %s" % queries

	outputfi = open(outputfi,'wt')

	print "--- processing XML queries"
	root = ET.parse(queries).getroot()
	name2id = {}
	for child in root:
		ID = child.attrib.get('id')
		for x_attrib in child:
			if x_attrib.tag == "name":
				name2id[x_attrib.text] = ID
				break

	print "--- converting data into answers list (for querrying - RTextProcess)"
	def format(part):
		return string.replace(part[2:], "_", " ")

	def puncrm(part):
		return part.translate(__table, __most_punctuation)

	def extract(part,right=True):
		if right:
			return part.split("(")[1].split(",")[1][0:-1]
		return part.split("(")[1].split(",X)")[0]

	mapF = lambda x: puncrm(format(extract(x))).strip()
	def filterF(x):
		x = x.strip()
		if len(x) == 0:
			return False
		elif len(x.translate(__table,string.punctuation).strip()) == 0:
			return False
		return True

	querytext2answers = collections.defaultdict(set)

	for line in open(inputfi):
		bits = line.strip().split("\t")
		querytext = format(extract(bits[0],False))
		s = querytext2answers[querytext]
		
		for answer in filter(filterF, map(mapF, bits[1:])):
			s.add(answer)

	for querytext in querytext2answers:
		if querytext not in name2id:
			print "*** ERROR: \"%s\" doesn't have an ID !!" % querytext
		else:
			ID = name2id[querytext]
			outputfi.write("%s\t%s\t" % (ID,querytext))
			outputfi.write("%s\n" % "\t".join(querytext2answers[querytext]))

	outputfi.close()

	print "--- done"