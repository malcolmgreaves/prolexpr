import sys

EVERYTHING = sys.argv[1]
EVERYTHING_NO_DUPE = sys.argv[2]

print "Input (everything, documents):  %s" % EVERYTHING
print "Output (no dupes, documents):   %s" % EVERYTHING_NO_DUPE


docsentIDs = set([l.split("\t")[5].split(":")[0] for l in open(EVERYTHING)])

def docsentKey(ds):
    bits = ds.split(".S")
    return bits[0],int(bits[1])

docsentIDs.sort(key=docsentKey)
print "%d unqiue document-sentence IDs" % len(docsentIDs)


id2lines = {}
def getID(l):
    return l.split("\t")[5].split(":")[0]
for ds in docsentIDs:
    id2lines[ds] = set()

for l in open(EVERYTHING):
    if len(l.strip()) == 0:
        continue
    docsentID = getID(l)
    id2lines[docsentID].add(l)


def sentIndex(l):
    return int(l.split("\t")[0])

w = open(EVERYTHING_NO_DUPE,'wt')
for ds in docsentIDs:
    lines = sorted(id2lines[ds], key=sentIndex)
    w.write("".join(lines)+"\n")
w.close()

print "done"