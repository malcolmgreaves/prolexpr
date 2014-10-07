
def mkRel2Type(relfi="relation_to_type"):
   relation2reltype = {}
   for line in open(relfi):
       bits = line.strip().split("\t")
       relation2reltype[bits[0].strip()] = bits[1].strip()
   return relation2reltype

def compperf2reltypeperf(fi,relation2reltype):
   import collections
   reltype2perf = collections.defaultdict(lambda:[0.0,0.0,0.0])
   reltype2count = collections.defaultdict(float)

   for l in open(fi):
      bits = l.split("\t")
      relation = relation2reltype[bits[0]]
      precision = float(bits[1])
      recall = float(bits[2])
      f1 = float(bits[3])
      reltype2count[relation] += 1.0
      reltype2perf[relation][0] += precision
      reltype2perf[relation][1] += recall
      reltype2perf[relation][2] += f1

   for r in reltype2perf:
      reltype2perf[r][0] /= reltype2count[r] 
      reltype2perf[r][1] /= reltype2count[r]
      reltype2perf[r][2] /= reltype2count[r]
   return reltype2perf

def display(reltype2perf):
   reltypes = sorted([r for r in reltype2perf])
   for r in reltypes:
      print "%s\t%f\t%f\t%f" % (r, reltype2perf[r][0],reltype2perf[r][1],reltype2perf[r][2])

def getPrecRecF1(rt2perf):
   prec = sum([rt2perf[r][0] for r in rt2perf])/len(rt2perf)
   rec = sum([rt2perf[r][1] for r in rt2perf])/len(rt2perf)
   f1 = sum([rt2perf[r][2] for r in rt2perf])/len(rt2perf)
   return (prec,rec,f1) 


if __name__=="__main__":
   r2rt = mkRel2Type()
   for fi in map(lambda x: "comp-PERF_SEARCH-"+x, ['baseline','baseline+proppr','proppr']):
      rt2perf = compperf2reltypeperf(fi,r2rt)
      prec,rec,f1 = getPrecRecF1(rt2perf)
      print "%s\t%f\t%f\t%f" % (fi, prec,rec,f1)
      display(rt2perf)

   fi = 'comp-PERF_CORPUS-baseline'
   r2rt = mkRel2Type('relation_to_type-CORPUS')
   rt2perf = compperf2reltypeperf(fi,r2rt)
   prec,rec,f1 = getPrecRecF1(rt2perf)
   print "%s\t%f\t%f\t%f" % (fi, prec,rec,f1)
   display(rt2perf)   
