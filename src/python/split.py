import os, gzip, sys


N_SPLIT = sys.argv[1]
INPUT = sys.argv[2]
OUTPUT_DIR = sys.argv[3]

print "# split:    %s" % N_SPLIT
print "input:      %s" % INPUT
print "output dir: %s" % OUTPUT_DIR

_head, INNAME = os.path.split(INPUT)
if ".gz" in INNAME:
	INNAME = INNAME.split(".gz")[0]

N_SPLIT = int(N_SPLIT)

writers = [gzip.open(os.path.join(OUTPUT_DIR, "%s_subpart-%d.gz" % (INNAME, ii)), 'wt') for ii in xrange(N_SPLIT)]

ii = 0
for l in gzip.open(INPUT):
	writers[ii % N_SPLIT].write(l)
	writers[ii % N_SPLIT].write("\n")
	ii +=1

	if ii % 500000 == 0:
		print "wrote %d lines" % ii

for w in writers:
	w.close()

print "wrote %d lines to %d files" % (ii, N_SPLIT)