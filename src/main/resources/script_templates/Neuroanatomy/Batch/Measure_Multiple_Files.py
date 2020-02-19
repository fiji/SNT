# @String(value="This script measures all SWC/TRACES files in a directory",visibility="MESSAGE") msg
# @File(label="Input directory", style="directory") input_dir
# @String(label="Consider only filenames containing",description="Clear field for no filtering",value="") name_filter
# @boolean(label="Include subdirectories") recursive
# @Context context
# @DisplayService displayservice
# @LogService log
# @StatusService status
# @UIService uiservice


"""
file:	   Measure_Multiple_Files.py
author:	 Tiago Ferreira
version:	20180531
info:	   Bulk measurements of reconstruction files using SNT
"""

import os, sys
from sc.fiji.snt import Tree
from sc.fiji.snt.analysis import TreeAnalyzer
from org.scijava.table import DefaultGenericTable



def get_trees(directory, filtering_string):
	"""Returns a list containing the Tree objects which represent
	   reconstruction files in the specified directory. The list will 
	   only contain Trees from SWC, TRACES and JSON files whose filename contains 
	   the specified string."""
	if not recursive:
		return Tree.listFromDir(directory, filtering_string)
	trees = []
	for (dirpath, dirnames, filenames) in os.walk(directory):
			trees += Tree.listFromDir(dirpath, filtering_string)
			
	return trees


def run():

	d = str(input_dir)
	trees = get_trees(d, name_filter)
	if not trees or len(trees) == 0:
		uiservice.showDialog("No files matched the specified criteria", "Error")
		return

	# Define a common table to host results
	table = DefaultGenericTable()

	for (counter, t) in enumerate(trees):

		msg = 'Analyzing Tree: %s: %s...' % (counter + 1, t.getLabel())
		status.showStatus(msg)
		log.info(msg)

		# Prepare analysis
		analyzer = TreeAnalyzer(t)
		analyzer.setContext(context)
		analyzer.setTable(table, ("SWC Measurements: %s" % input_dir))

		# Analyze the data grouping measurements by compartment (e.g., axon,
		# dendrite). See TreeAnalyzer's API for more sophisticated analysis.
		# Table is automatically updated
		analyzer.summarize(True) # Split results by compartment?

	log.info('Done. %s file(s) analyzed...' % (counter + 1))  
	if table.getRowCount() == 0:
		uiservice.showDialog("No files were analyzed", "Error")
		

run()
