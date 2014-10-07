import re,fileinput

# get entire log from stdin (or all files on cmd line args)
l = [line.strip() for line in fileinput.input()]

# replace logging info
def replaceLogMake():
	patterns = ["[0-9]*-[0-9]*-[0-9]* [0-9]*:[0-9]*:[0-9]*,[0-9]*[ A-Za-z\[\]0-9/]*:[0-9]*:[0-9]* ",
	"[0-9]*-[0-9]*-[0-9]* [0-9]*:[0-9]*:[0-9]*,[0-9]*[ A-Za-z\[\]0-9/]* "]
	def p(x):
		for p in patterns:
			x = re.sub(p,"",x)
		return x
	return p
replace = replaceLogMake()
l = map(replace, l)

# remove lines not related to configuration, performance, or relation name
def acceptMake():
	conditions = ["\[test fold [0-9]*\]","Using these options for svm_light:",
	"multi_svm for relation:"]
	def a(x):
		for c in conditions:
			if re.match(c,x) != None:
				return True
		return False
	return a
accept = acceptMake()
l = filter(accept, l)

# echo to stdout
for x in l:
	print x