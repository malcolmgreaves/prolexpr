import sys,gzip,os,string,collections

if __name__ == "__main__":
	if len(sys.argv) != 4:
		print "ERROR: need arguments:\n\toutput dir\n\tname fi\n\tinput dir"
		sys.exit(2)
	outdir = sys.argv[1]
	nameFi = sys.argv[2]
	inputDir = sys.argv[3]

	print "output directory:   %s" % outdir
	print "names file:         %s" % nameFi
	print "input:              %s" % inputDir

	if os.path.exists(outdir):
		if not os.path.isdir(outdir):
			raise Exception("output dir is actually a file!")
	else:
		os.mkdir(outdir)

	def nameparser(x):
		bits = x.strip().split("\t")
		print "loading %d queries for relation: %s" % (len(bits[1:]),bits[0])
		for x in bits[1:]:
			yield x

	names,nameparts = set(),set()
	name2wtr,name2docs,namepart2name = {},{},collections.defaultdict(lambda: set())
	nameFiSanitizer = lambda x: string.replace(x," ","_")
	
	for x in open(nameFi):
		for name in nameparser(x):
			if name not in names:
				names.add(name)
				name2wtr[name] = open(os.path.join(outdir,nameFiSanitizer(name)),'wt')		
				name2docs[name] = set()

				namepart2name[name].add(name)
				nameparts.add(name)
				for npart in name.split(" "):
					namepart2name[npart].add(name)
					nameparts.add(npart)

	print "looking for %d names and %d individual (space-delim) parts" % (len(names),len(nameparts)-len(names))

	def opener(fi):
		if ".gz" in fi:
			return gzip.open(fi)
		else:
			return open(fi)

	def filelister(inp):
		if os.path.isdir(inp):
			for fi in os.listdir(inp):
				yield os.path.join(inp,fi)
		else:
			yield inp

	print "reading all %d text files" % len([x for x in filelister(inputDir)])
	# gather all docsent IDs for all names
	for fi in filelister(inputDir):
		for l in opener(fi):
			bits = l.strip().split("\t")
			docsentID = bits[0]
			sentence = bits[2]
			for n in namepart2name:
				for bit in map(lambda x: string.rstrip(x,"."), sentence.split(" ")):
					if bit in nameparts:
						for name in namepart2name[bit]: 
							name2docs[name].add(docsentID)

	
	# go back through data and grab all sentences from all
	# collected docsentID's
	for fi in filelister(inputDir):
		for l in opener(fi):
			bits = l.split("\\t")
			docsentID = bits[0]
			for n in name2docs:
				if docsentID in name2docs[n]:
					name2wtr[n].write(l)

	for n in name2wtr:
		name2wtr[n].close()				
	print "done"
