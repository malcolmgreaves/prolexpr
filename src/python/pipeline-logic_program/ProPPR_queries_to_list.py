import sys,string,collections

if __name__=="__main__":
	if len(sys.argv) != 3:
		print "ERROR need:\n\toutput names\n\tinput .queries"
		sys.exit(2)
	output = sys.argv[1]
	queries = sys.argv[2]
	print "output:   %s" % output
	print "queries:  %s" % queries
	w = open(output,'wt')

	def process(line):
		startQuery = string.index(line,"(")
		endQuery = string.index(line,",X)")
		# remove prefix "w_"
		# replace "_" with " "
		query = string.replace(string.lstrip(line[startQuery+1:endQuery], "w_"), "_"," ")
		relation = line[0:startQuery]
		return relation,query


	rel2q = collections.defaultdict(lambda: set())
	for l in open(queries):
		relation,query = process(l)
		rel2q[relation].add(query)

	for r in rel2q:
		w.write(r+"\t"+"\t".join(rel2q[r])+"\n")
	w.close()

	print "done"