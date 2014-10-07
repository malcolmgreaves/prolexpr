import numpy as np
import matplotlib.pyplot as plt
import collections

def mkRel2Type(relfi="relation_to_type"):
   relation2reltype = {}
   for line in open(relfi):
       bits = line.strip().split("\t")
       relation2reltype[bits[0].strip()] = bits[1].strip()
   return relation2reltype

def compperf2reltypeperf(fi,relation2reltype):
   reltype2perf = collections.defaultdict(lambda:[0.0,0.0,0.0])
   reltype2count = collections.defaultdict(float)
   reltype2all = collections.defaultdict(lambda: [[],[],[]])

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
      reltype2all[relation][0].append(precision)
      reltype2all[relation][1].append(recall)
      reltype2all[relation][2].append(f1)

   for r in reltype2perf:
      reltype2perf[r][0] /= reltype2count[r] 
      reltype2perf[r][1] /= reltype2count[r]
      reltype2perf[r][2] /= reltype2count[r]
   return reltype2perf,reltype2all

def display(reltype2perf):
   reltypes = sorted([r for r in reltype2perf])
   for r in reltypes:
      print "%s\t%f\t%f\t%f" % (r, reltype2perf[r][0],reltype2perf[r][1],reltype2perf[r][2])

r2rt = mkRel2Type()
method2rt2perf = {}
methods = ['baseline','proppr','baseline+proppr']
method2reltype2all = {}
for meth,fi in zip(methods, map(lambda x: "comp-PERF_SEARCH-"+x, methods)):
  method2rt2perf[meth],method2reltype2all[meth] = compperf2reltypeperf(fi,r2rt)

reltypes = sorted(set([r2rt[r] for r in r2rt]))

n_groups = len(reltypes)

fig, ax = plt.subplots()

index = np.arange(0, 1.1*n_groups, 1.1)
bar_width = 0.35

opacity = 0.4
error_config = {'ecolor': '0.6'}

def toInts(dct,selection=2):
    values = [dct[x][selection] for x in reltypes]
    return map(lambda x: x, values)

def variance(s):
  return np.array(s).std()/2.0

def varianceByReltype(meth,selection=2):
  all_reltypes_meth = method2reltype2all[meth]
  variances = []
  for r in reltypes:
    variances.append(variance(all_reltypes_meth[r][:][selection]))
  return variances

baseline = toInts(method2rt2perf[methods[0][:]])
proppr = toInts(method2rt2perf[methods[1][:]])
both = toInts(method2rt2perf[methods[2][:]])

rects0 = plt.bar(index, baseline, bar_width,
                 # alpha=opacity,
                 color='b',
                 yerr=varianceByReltype(methods[0]),
                 error_kw=error_config,
                 label=methods[0])

rects1 = plt.bar(index + bar_width, proppr, bar_width,
                 # alpha=opacity,
                 color='r',
                 yerr=varianceByReltype(methods[1]),
                 error_kw=error_config,
                 label=methods[1])

rects2 = plt.bar(index + 2*bar_width, both, bar_width,
                 # alpha=opacity,
                 color='g',
                 yerr=varianceByReltype(methods[2]),
                 error_kw=error_config,
                 label=methods[2])

plt.xlabel('Aggregate Relation Type',size="xx-large")
plt.ylabel('Micro-Averaged F1',size="xx-large")
plt.title('F1 vs Aggregate Relation Type',size="xx-large")
plt.xticks(index + bar_width, reltypes, size="large")
plt.legend()

plt.tight_layout()
plt.show() 