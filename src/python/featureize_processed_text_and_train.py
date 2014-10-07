import sys,string,sklearn.svm,numpy,pickle

teststr='''1	It	It	PRP	PRP	APW_ENG_20020416.0965:1:O:0:2	6	nsubj	_	_	
2	was	was	VBD	VBD	APW_ENG_20020416.0965:1:O:3:6	6	cop	_	_	
3	the	the	DT	DT	APW_ENG_20020416.0965:1:O:7:10	6	det	_	_	
4	first	first	JJ	JJ	APW_ENG_20020416.0965:1:O:11:16	6	amod	_	_	
5	official	official	JJ	JJ	APW_ENG_20020416.0965:1:O:17:25	6	amod	_	_	
6	visit	visit	NN	NN	APW_ENG_20020416.0965:1:O:26:31	0	null	_	_	
7	by	by	IN	IN	APW_ENG_20020416.0965:1:O:32:34	6	prep	_	_	
8	the	the	DT	DT	APW_ENG_20020416.0965:1:O:35:38	9	det	_	_	
9	Congressional Women	Congressional Women	NNP	NNP	APW_ENG_20020416.0965:1:ORGANIZATION:39:58	11	poss	_	_	
10	's	's	POS	POS	APW_ENG_20020416.0965:1:O:-1:1	9	possessive	_	_	
11	Caucus	Caucus	NNP	NNP	APW_ENG_20020416.0965:1:O:61:67	7	pobj	_	_	
12	to	to	TO	TO	APW_ENG_20020416.0965:1:O:68:70	6	prep	_	_	
13	the	the	DT	DT	APW_ENG_20020416.0965:1:O:71:74	14	det	_	_	
14	United Nations	United Nations	NNPS	NNPS	APW_ENG_20020416.0965:1:ORGANIZATION:75:89	12	pobj	_	_	
15	,	,	,	,	APW_ENG_20020416.0965:1:O:131:132	6	punct	_	_	
16	and	and	CC	CC	APW_ENG_20020416.0965:1:O:156:159	6	cc	_	_	
17	co-chairs	co-chairs	NNS	NNS	APW_ENG_20020416.0965:1:O:-1:8	18	nn	_	_	
18	Juanita Millender-McDonald	Juanita Millender-McDonald	NNP	NNP	APW_ENG_20020416.0965:1:PERSON:105:131	30	nsubj	_	_	
19	,	,	,	,	APW_ENG_20020416.0965:1:O:154:155	18	punct	_	_	
20	a	a	DT	DT	APW_ENG_20020416.0965:1:O:156:157	22	det	_	_	
21	California	California	NNP	NNP	APW_ENG_20020416.0965:1:LOCATION:-1:9	22	nn	_	_	
22	Democrat	Democrat	NNP	NNP	APW_ENG_20020416.0965:1:O:146:154	18	conj	_	_	
23	,	,	,	,	APW_ENG_20020416.0965:1:O:172:173	18	punct	_	_	
24	and	and	CC	CC	APW_ENG_20020416.0965:1:O:-1:2	18	cc	_	_	
25	Judy Biggert	Judy Biggert	NNP	NNP	APW_ENG_20020416.0965:1:PERSON:160:172	18	conj	_	_	
26	,	,	,	,	APW_ENG_20020416.0965:1:O:196:197	18	punct	_	_	
27	a	an	DT	DT	APW_ENG_20020416.0965:1:O:-1:1	28	det	_	_	
28	Republican	Illinois Republican	NNP	NNP	APW_ENG_20020416.0965:1:ORGANIZATION:177:196	18	appos	_	_	
29	,	,	,	,	APW_ENG_20020416.0965:1:O:-1:0	18	punct	_	_	
30	said	said	VBD	VBD	APW_ENG_20020416.0965:1:O:198:202	6	conj	_	_	
31	they	they	PRP	PRP	APW_ENG_20020416.0965:1:O:203:207	32	nsubj	_	_	
32	hope	hope	VBP	VBP	APW_ENG_20020416.0965:1:O:208:212	30	ccomp	_	_	
33	it	it	PRP	PRP	APW_ENG_20020416.0965:1:O:213:215	38	nsubj	_	_	
34	wo	wo	MD	MD	APW_ENG_20020416.0965:1:O:216:218	38	aux	_	_	
35	n't	n't	RB	RB	APW_ENG_20020416.0965:1:O:-1:2	38	neg	_	_	
36	be	be	VB	VB	APW_ENG_20020416.0965:1:O:222:224	38	cop	_	_	
37	the	the	DT	DT	APW_ENG_20020416.0965:1:O:225:228	38	det	_	_	
38	last	last	JJ	JJ	APW_ENG_20020416.0965:1:O:229:233	32	ccomp	_	_	
39	.	.	.	.	APW_ENG_20020416.0965:1:O:-1:0	6	punct	_	_	

'''

class LineStruct:
	def __init__(self,Word,DocID,SentNum):
		self.Word = Word
		self.DocID = DocID
		self.SentNum = SentNum
		self.DocSentID = "%s.%d" % (self.DocID,self.SentNum)

	def __str__(self):
		return "\"%s\" in doc %s sentence # %d" % (self.Word, self.DocID, self.SentNum)

def parse_line_struct(line,lowercase=False,underscore=False):
	bits = line.split("\t")
	sbits = bits[5].split(":")[0].split(".")
	if lowercase:
		bits[1] = bits[1].lower()
		sbits[0] = sbits[0].lower()
	if underscore:
		bits[1] = string.replace(bits[1], " ", "_")
		sbits[0] = string.replace(sbits[0], " ", "_")
	return  LineStruct(Word=bits[1],DocID=sbits[0],SentNum=int(sbits[1]))

if __name__=="__main__":
	if len(sys.argv) != 3:
		print "ERROR: need [0] input file and [1] output file as arguments"
		exit()
	infile = sys.argv[1]
	outfile = sys.argv[2]
	print "input:   %s" % infile
	print "output:  %s" % outfile

	print "** LOWERCASING EVERYTHING **"
	lowercasing = True
	print "** REPLACING SPACES WITH _ **"
	underscore = True

	infile = open(infile)
	outfile = open(outfile,'wt')

	DIMENSION = 10000
	print "hashed dimension",DIMENSION
	X = numpy.array([])
	xRowWords = []
	Y = numpy.array([])

	sentence = []

	nLinesProcessed = 0
	nDemocrat = 0
	
	for l in infile:
		l = l.strip()
		if len(l) > 0:
			nLinesProcessed += 1
			try:
				ls = parse_line_struct(l,lowercase=lowercasing,underscore=underscore)				
				sentence.append(ls)
			except Exception, e:
				print "error processing line: \"%s\"" % (l)
				print "error: ",e.__doc__, " ", e.message
		else:
			for (ii, tokenLS) in zip(xrange(len(sentence)), sentence):
				left = ii - 2
				if left < 0:
					left = 0
				right = ii + 3
				if right > len(sentence):
					right = len(sentence)

				leftBigram = " ".join(map(lambda x: x.Word, sentence[left:ii]))
				rightBigram = " ".join(map(lambda x: x.Word, sentence[ii+1:right]))

				print "left[",left,"]:",leftBigram,"\tright[",right,"]:",rightBigram,"\tword[",ii,"]:",tokenLS.Word

				row = numpy.array([0 for x in xrange(DIMENSION)])
				if len(leftBigram) > 0:
					row[hash(leftBigram) % DIMENSION] = 1
				if len(rightBigram) > 0:
					row[hash(rightBigram) % DIMENSION] = 1

				if tokenLS.Word == "democrat":
					answer = numpy.array([1])
					nDemocrat += 1
				else:
					answer = numpy.array([0])

				if len(X) == 0:
					X = row
					Y = answer
				else:
					X = numpy.vstack((X, row))	
					Y = numpy.hstack((Y,answer))

				xRowWords.append(tokenLS.Word)
			sentence = []

	print "%d / %d examples are democrat" % (nDemocrat, len(xRowWords))
	print "fitting to %d examples" % len(xRowWords)
	clf = sklearn.svm.SVC()
	clf.fit(X,Y)

	print "dumping..."
	pickle.dump(clf, outfile, protocol=-1)

	outfile.close()
	print "done, processed %d lines" % nLinesProcessed

