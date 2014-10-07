import fileinput

def filterNegs(line):
	bits = line.strip().split("\t")
	return bits[0]+"\t"+"\t".join(filter(lambda x: x[0] == "+",bits[1:]))

for line in fileinput.input():
	print filterNegs(line)