import sys,string,os
import xml.etree.ElementTree as ET

def rel2predicate(r):
	return string.replace(r,":","_")

class Annotation():
	def __init__(self,ID,relation,docid,response,judgement,name=""):
		self.ID = ID
		self.Relation = rel2predicate(relation)
		self.DocID = docid
		self.Response = response
		self.Judgement = int(judgement)
		self.Name = name

	def __str__(self):
		return "%s for %s is \"%s\" [%s] judged as %d" % (self.ID, self.Relation, self.Response, self.DocID, self.Judgement)

class Query():
	def __init__(self,ID,name="",docid="",enttype=""):
		self.ID = ID
		self.Name = name
		self.DocID = docid
		self.EntType = enttype

	def __str__(self):
		return "%s [%s] from %s is a %s" % (self.Name, self.ID, self.DocId, self.EntType)

class Assessment():
	def __init__(self,ID,relation,docid,judgement,response):
		self.ID = ID
		self.Relation = rel2predicate(relation)
		self.DocID = docid
		self.Judgement = int(judgement)
		self.Response = response

	def __str__(self):
		return "answered %s [%s] with \"%s\" from %s, judged as %d" % (self.ID, self.Relation, self.Response, self.DocID, self.Judgement)

def select_processors(format):
	format = int(format)

	if format == 2010 or format == 2011:
		def processAnnotation(line):
			bits = line.split("\t")
			return Annotation(bits[1],bits[3],bits[4],bits[7],bits[10])
		def processAssessment(line):
			bits = line.split(" ")
			sbits = bits[1].split(":")
			return Assessment(sbits[0], "_".join(sbits[1:]), bits[2], bits[3], "_".join(bits[5:]))

		return processAnnotation,processAssessment

	elif format == 2012:
		def processAnnotation(line):
			bits = line.split("\t")
			return Annotation(bits[1],bits[3],bits[4],bits[7],bits[13])
		def processAssessment(line):
			bits = line.split("\t")
			sbits = bits[1].split(":")
			return Assessment(sbits[0],"_".join(sbits[1:]),bits[2],bits[3],bits[5])

		return processAnnotation, processAssessment

	raise Exception("incorrect year, must be in [2010,2012]: %d" % format)

def parseAnnotations(process,infi_annotation):
	r = open(infi_annotation)
	r.readline() # skip header
	for l in r:
		yield process(l)
	r.close()

def parseXMLQueries(infi_queries):
	for child in ET.parse(infi_queries).getroot():

		q = Query(child.get('id'))
		for x_attrib in child:
			if x_attrib.tag == "name":
				q.Name = x_attrib.text
			elif x_attrib.tag == "docid":
				q.DocID = x_attrib.text
			elif x_attrib.tag == "enttype":
				q.EntType = x_attrib.text
		yield q

def parseAssessments(process,indir_assessments):
	for fi in os.listdir(indir_assessments):
		for line in open(os.path.join(indir_assessments,fi)):
			line = line.strip()
			if len(line) > 0:
				try:
					yield process(line)
				except Exception, e:
					print "** OFFENDING LINE **: \"%s\"" % line
					raise e

class NamedAssessedAnnotated():
	def __init__(self,name,ass=[],ann=[]):
		self.Name = name
		self.Assessments = [x for x in ass]
		self.Annotations = [x for x in ann]

	def __str__(self):
		return "%s has %d assessments and %d annotations" % (self.Name, len(self.Assessments), len(self.Annotations))

def make_LP_train_data(naa,posandneg):
	byrel = {}
	formatLP = lambda s: string.replace(s," ","_")

	for ass in naa.Assessments:
		if ass.Relation not in byrel:
			byrel[ass.Relation] = "%s(w_%s,X)\t" % (ass.Relation,naa.Name)
		if ass.Judgement == 1 or ass.Judgement == 2:
			s = "+"
		else:
			s = "-"
		byrel[ass.Relation] += "%s%s(w_%s,w_%s)\t" % (s, ass.Relation, formatLP(naa.Name), formatLP(ass.Response))

	for ann in naa.Annotations:
		if ann.Relation not in byrel:
			byrel[ann.Relation] = "%s(w_%s,X)\t" % (ann.Relation,formatLP(naa.Name))
		if ann.Judgement == 1 or ann.Judgement == 2:
			s = "+"
		else:
			s = "-"
		byrel[ann.Relation] += "%s%s(w_%s,w_%s)\t" % (s, ann.Relation, formatLP(naa.Name), formatLP(ann.Response))

	rm = []
	for r in byrel:
		if posandneg:
			if "\t+" in byrel[r] and "\t-" in byrel[r]:
				accept = True
			else:
				accept = False
		else:
			if "\t+" in byrel[r] or "\t-" in byrel[r]:
				accept = True
			else:
				accept = False

		if accept:
			byrel[r] = byrel[r].strip()
		else:
			rm.append(r)
	for r in rm:
		del byrel[r]

	return byrel

class PN():
	def __init__(self):
		self.pos = set()
		self.neg = set()

def getByRels(naa,formatter=lambda x: x):
	import collections
	byRels = collections.defaultdict(lambda: PN())
	def f(x):
		if x.Judgement == 1 or x.Judgement == 2:
			l = byRels[x.Relation].pos
		else:
			l = byRels[x.Relation].neg
		l.add((formatter(naa.Name), formatter(x.Response)))

	for ass in naa.Assessments:
		f(ass)
	for ann in naa.Annotations:
		f(ann)

	return byRels

def make_TextClassifier_train_data(outfi,naa):
	byRels = getByRels(naa,formatter=lambda x: string.replace(x,"_"," "))
	nWrote,nRels = 0,0
	for r in byRels:
		if len(byRels[r].pos) == 0 or len(byRels[r].neg) == 0:
			print "skipping %s because it is not balanced (+%d, -%d)" % (r,len(byRels[r].pos),len(byRels[r].neg)) 
		else:
			nRels += 1
			for (name,response) in byRels[r].pos:
				outfi.write("%s\t+\t%s\t%s\n" % (r,name,response))
				nWrote += 1
			for (name,response) in byRels[r].neg:
				outfi.write("%s\t-\t%s\t%s\n" % (r,name,response))
				nWrote += 1
	print "wrote %d examples to %d relations" % (nWrote, nRels)

if __name__=="__main__":
	if len(sys.argv) != 8:
		print "need cmd arguments:\n[] year of file (format)\n[] input file, annotation\n[] input file, queries\n[] input directory, assessments\n[] output file\n[True,False] need both +/- ?\n[] ProPPR Logic Logic Program format?\n"
		print "\nINSTEAD, got these %d:\n%s" % (len(sys.argv), "\n\t".join(sys.argv))
		exit()

	format = sys.argv[1]
	infi_annotation = sys.argv[2]
	infi_queries = sys.argv[3]
	indir_assessments = sys.argv[4]
	outfi = sys.argv[5]
	posandneg = sys.argv[6]
	lpformat = sys.argv[7]
	print "format-year:                  %s" % format
	print "input, annotation:            %s" % infi_annotation
	print "input, queries:               %s" % infi_queries
	print "input, assessments (dir):     %s" % indir_assessments
	print "output (LP training data):    %s" % outfi
	print "only use things with + & -:   %s" % posandneg
	print "ProPPR LogicProgram fmt?:     %s" % lpformat
	print "****************************"

	def isTrue(x):
		x = x.lower()
		return x=='t' or x == 'true' or x=='y' or x == 'yes'

	posandneg = isTrue(posandneg)
	lpformat = isTrue(lpformat)
	print "lpformat: %s" % lpformat

	processAnn,processAss = select_processors(format)
	outfi = open(outfi,'wt')

	##
	## Gets all of the labeled data
	##
	id2info = {}
	# get the ID -> query (name) mapping from the queries
	for query in parseXMLQueries(infi_queries):
		id2info[query.ID] = NamedAssessedAnnotated(query.Name)
	# get the annotations (+)
	for ann in parseAnnotations(processAnn,infi_annotation):	
		if ann.ID not in id2info:
			print "WARNING: Annotated ID \"%s\" is not in the queries" % ann.ID
		else:
			id2info[ann.ID].Annotations.append(ann)
	# get the assessments (+ or -)
	for ass in parseAssessments(processAss, indir_assessments):
		if ass.ID not in id2info:
			print "WARNING: Assessed ID \"%s\" is NOT IN the queries !!" % ass.ID
		else:
			id2info[ass.ID].Assessments.append(ass)

	def listprinter(ls):
		return "\n\t".join(map(lambda x: x.__str__(), ls))

	##
	## Writes all of the labled data to disk
	##
	for ID in id2info:

		print "ID:          %s" % ID
		print "QUERY NAME:  %s" % id2info[ID].Name
		print "# ANNOTATIONS: %d" % len(id2info[ID].Annotations) #\n\t",listprinter(id2info[ID].Annotations)
		print "# ASSESSMENTS: %d" % len(id2info[ID].Assessments) #\n\t",listprinter(id2info[ID].Assessments)

		if lpformat:
			# contains per-relation labeled (+,-) examples formatted as strings
			# which are reable by ProPPR
			byrel = make_LP_train_data(id2info[ID],posandneg)

		else:
			# contains - per-relation labeled(+,-) examples as strings, which
			# are formatted to work with the linear text classifier
			make_TextClassifier_train_data(outfi,id2info[ID])


	if lpformat:
		print "writing %d training examples\n" % len(byrel)
		for r in byrel:
			outfi.write("%s\n" % byrel[r])
	outfi.close()
	print "****************************"
	print "processed & wrote LP training data for %d query IDs -- done" % len(id2info)
