import sys,string

stopwords=set(['all', "she'll", "don't", 'being', 'over', 'through', 'yourselves', 'its', 'before', "he's", "when's", "we've", 'had', 'should', "he'd", 'to', 'only', "there's", 'those', 'under', 'ours', 'has', "haven't", 'do', 'them', 'his', "they'll", 'very', "who's", "they'd", 'cannot', "you've", 'they', 'not', 'during', 'yourself', 'him', 'nor', "we'll", 'did', "they've", 'this', 'she', 'each', "won't", 'where', "mustn't", "isn't", "i'll", "why's", 'because', "you'd", 'doing', 'some', 'up', 'are', 'further', 'out', 'what', 'for', 'while', "wasn't", 'does', "shouldn't", 'above', 'between', 'be', 'we', 'who', "you're", 'were', 'here', 'hers', "aren't", 'by', 'both', 'about', 'would', 'of', 'could', 'against', "i'd", "weren't", '\tourselves', "i'm", 'or', "can't", 'own', 'into', 'whom', 'down', "hadn't", "couldn't", 'your', "doesn't", 'from', "how's", 'her', 'their', "it's", 'there', 'been', 'why', 'few', 'too', 'themselves', 'was', 'until', 'more', 'himself', "where's", "i've", 'with', "didn't", "what's", 'but', 'herself', 'than', "here's", 'he', 'me', "they're", 'myself', 'these', "hasn't", 'below', 'ought', 'theirs', 'my', "wouldn't", "we'd", 'and', 'then', 'is', 'am', 'it', 'an', 'as', 'itself', 'at', 'have', 'in', 'any', 'if', 'again', 'no', 'that', 'when', 'same', 'how', 'other', 'which', 'you', "shan't", 'our', 'after', "let's", 'most', 'such', 'on', "he'll", 'a', 'off', 'i', "she'd", 'yours', "you'll", 'so', "we're", "she's", 'the', "that's", 'having', 'once'])
stopposes=set([])

class LineStruct:
	def __init__(self,Word,DocID,SentNum,NER,POS):
		self.Word = Word
		self.DocID = DocID
		self.SentNum = SentNum
		self.NER = NER
		self.POS = POS
		self.DocsentID = "%s.S%d" % (self.DocID,self.SentNum)

	def __str__(self):
		return "\"%s\"[%s] in doc %s sentence # %d" % (self.Word, self.NER, self.DocID, self.SentNum)

	def lp_pred_entityInDocsent(self):
		return "entityInDocsent\t%s\t%s" % (self.fmt_Word(), self.fmt_DocsentID())

	def lp_pred_docsentHasEntity(self):
		return "docsentHasEntity\t%s\t%s" % (self.fmt_DocsentID(), self.fmt_Word())

	def lp_pred_docsentHasWordHasNER(self):
		return "docsentHasWordHasNER\t%s\t%s\t%s" % (self.fmt_DocsentID(), self.fmt_Word(), self.fmt_NER())

	def lp_pred_docsentHasWordHasPOS(self):
		return "docsentHasWordHasPOS\t%s\t%s\t%s" % (self.fmt_DocsentID(), self.fmt_Word(), self.fmt_POS())

	def fmt_POS(self):
		return "p_%s" % self.POS

	def fmt_NER(self):
		return "n_%s" % self.NER

	def fmt_Word(self):
		return "w_%s" % self.Word

	def fmt_DocsentID(self):
		return "id_%s" % self.DocsentID

	def fmt_DocID(self):
		return "id_%s" % self.DocID

def parse_line_struct(line,processor=lambda x: x):
	bits = line.split("\t")
	sbits = bits[5].split(":")
	docbits = sbits[0].split(".S")

	return LineStruct(
		Word=processor(bits[1]),
		DocID=processor(docbits[0]),
		SentNum=int(docbits[1]),
		NER=processor(sbits[1]),
		POS=processor(bits[3]))

class CorpusStruct:
	def __init__(self):
		self.Doc2Docsent = {}

	def add(self, linestruct):
		docid = linestruct.fmt_DocID()
		if docid not in self.Doc2Docsent:
			self.Doc2Docsent[docid] = set()
		self.Doc2Docsent[docid].add(linestruct.fmt_DocsentID())

	def lp_pred_docsent_mapping(self):
		ret = ""
		for doc in self.Doc2Docsent:
			for docsent in self.Doc2Docsent[doc]:
				ret += "docHasDocsent\t%s\t%s\n" % (doc, docsent)
				ret += "docsentHasDoc\t%s\t%s\n" % (docsent, doc)
		return ret

if __name__=="__main__":
	if len(sys.argv) != 3:
		print "ERROR: need [0] input file and [1] output file as arguments"
		exit()
	infile = sys.argv[1]
	outfileName = sys.argv[2]
	print "input:          %s" % infile
	print "output, graph:  %s.graph" % outfileName
	print "output, facts:  %s.facts" % outfileName

	print "** REPLACING SPACES WITH _ **"
	processor = lambda x: string.replace(x, " ", "_")
	# print "** DISREGARDING NON-TAGGED WORDS **"
	# filt_func = lambda x: x.NER != "O"
	print "** REJECTING STOPWORDS (all %d of them) **" % len(stopwords)
	print "** REJECTING PUNCTUATION (tag == word) **"
	def filt_func(x):
		return x.Word.lower() not in stopwords and x.Word != x.POS

	cs = CorpusStruct()

	infile = open(infile)
	outfileG = open("%s.graph" % outfileName,'wt')
	outfileF = open("%s.facts" % outfileName, 'wt')

	nLinesProcessed = 0
	nAccepted = 0
	for l in infile:
		l = l.strip()
		if len(l) > 0:
			nLinesProcessed += 1
			try:
				ls = parse_line_struct(l,processor)
				if filt_func(ls):
					nAccepted += 1
					cs.add(ls)
					# graph link: doc-sent ID has word <->
					outfileG.write("%s\n%s\n" % 
						(ls.lp_pred_entityInDocsent(),ls.lp_pred_docsentHasEntity()))
					# facts link: NER & POS
					outfileF.write("%s\n" % ls.lp_pred_docsentHasWordHasNER())
					outfileF.write("%s\n" % ls.lp_pred_docsentHasWordHasPOS())
			except Exception, e:
				print "error processing line: \"%s\"" % (l)
				print "error: ",e.__doc__, " ", e.message

	outfileG.write(cs.lp_pred_docsent_mapping())

	outfileG.close()
	outfileF.close()
	print "done, processed %d lines, accepted %d" % (nLinesProcessed, nAccepted)
