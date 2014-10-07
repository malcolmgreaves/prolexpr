import sys, collections, string, os

def _make_normRelName():
	__rel_old2new = {
		"org_dissolved":"org_date_dissolved",
		"org_founded":"org_date_founded",
		"org_number_of_employees,members":"org_number_of_employees_members",
		"org_political,religious_affiliation":"org_political_religious_affiliation",
		"org_top_members,employees":"org_top_members_employees",
		"per_stateorprovinces_of_residence":"per_statesorprovinces_of_residence",
	}
	__known = set(['per_stateorprovince_of_birth', 'org_number_of_employees_members', 
		'per_cause_of_death', 'org_stateorprovince_of_headquarters', 
		'org_country_of_headquarters', 'org_date_founded', 'org_parents', 
		'org_member_of', 'org_city_of_headquarters', 'per_cities_of_residence', 
		'org_top_members_employees', 'org_date_dissolved', 'per_country_of_death', 
		'per_date_of_birth', 'per_alternate_names', 'org_subsidiaries',
		 'org_alternate_names', 'per_charges', 'per_city_of_death',
		  'per_countries_of_residence', 'per_date_of_death', 'per_stateorprovince_of_death', 
		  'per_parents', 'org_shareholders', 'org_founded_by', 'per_employee_of', 
		  'per_country_of_birth', 'per_siblings', 'org_website', 'per_religion', 'per_spouse', 
		  'per_origin', 'org_political_religious_affiliation', 'org_members', 'per_children', 
		  'per_title', 'per_other_family', 'per_statesorprovinces_of_residence', 
		  'per_schools_attended', 'per_age', 'per_city_of_birth', 'per_member_of'])
	def f(oldname):
		if oldname in __rel_old2new:
			return __rel_old2new[oldname]
		if oldname not in __known:
			raise Exception("*** UNKNOWN RELATION *** %s" % oldname)
		return oldname
	return f

normRelName = _make_normRelName()

if __name__=="__main__":
	if len(sys.argv) < 3:
		print "ERROR: need [0] OUTPUT dir and [1] INPUT file as arguments"
		exit()
	outfile = sys.argv[1]
	print "output dir:       %s" % outfile
	inputs = sys.argv[2:]
	for ii, infile in zip(xrange(len(inputs)), inputs):
		print "input[%d / %d]:   %s" % (ii+1, len(inputs), infile)
	os.mkdir(outfile)

	print "--- normalizing relations & splitting into per-relation files"
	rel2examples = collections.defaultdict(set)
	for infi in inputs:
			for line in open(infi):
				line = line.strip()
				try:
					oldrelname = line.split("\t")[0].split("(")[0]
					relname = normRelName(oldrelname)
					rel2examples[relname].add(string.replace(line, oldrelname, relname))
				except Exception, e:
					print "Exception reading line: \"%s\"\n from file: \"%s\"" % (line, infi)
					raise e

	print "--- writing"
	for relname in rel2examples:
		outfi = open(os.path.join(outfile,"%s.data" % relname), 'wt')
		for example in rel2examples[relname]:
			outfi.write("%s\n" % example)
		outfi.close()

	print "--- done"