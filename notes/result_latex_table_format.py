import fileinput,string
for l in fileinput.input():
	b = l.strip().split("\t")
	name = string.replace(b[0],"_","\\_")
	numbers = map(lambda x: "%.2f" % x, map(float, b[1:]))
	print "%s & \t%s\\\\\\hline\\\\" % (name, " & ".join(numbers))
