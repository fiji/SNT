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
file:       Measure_Multiple_Files.py
author:     Tiago Ferreira
version:    20180531
info:       Bulk measurements of reconstruction files using SNT
"""

import os, sys
from sc.fiji.snt import Tree
from sc.fiji.snt.analysis import TreeAnalyzer
from org.scijava.table import DefaultGenericTable



def get_swc_files(directory, filtering_string):
    """Returns a list containing the paths of files in the specified
       directory. The list will only include SWC, TRACES and JSON
       files whose filename contains the specified string."""
    files = []
    for (dirpath, dirnames, filenames) in os.walk(directory):
        for f in filenames:
            if os.path.basename(f).startswith('.'):
                continue
            if filtering_string in f and f.lower().endswith(('swc', 'traces', 'json')):
                files.append(os.path.join(dirpath, f))
        if not recursive:
            break # do not process subdirectories
    return files


def run():

    d = str(input_dir)
    files = get_swc_files(d, name_filter)
    if not files or len(files) == 0:
        uiservice.showDialog("No files matched the specified criteria", "Error")
        return

    # Define a common table to host results
    table = DefaultGenericTable()

    for (counter, f) in enumerate(files):

        basename = os.path.basename(f)
        msg = 'Loading file %s: %s...' % (counter + 1, basename)
        status.showStatus(msg)
        log.info(msg)

        # Import file as an analyzable Tree. Move on if import failed
        tree = Tree.fromFile(f)
        if not tree:
           log.error("Traces not imported. Invalid File? %s" % basename)
           continue

        # Prepare analysis
        analyzer = TreeAnalyzer(tree)
        analyzer.setContext(context)
        analyzer.setTable(table, ("SWC Measurements: %s" % input_dir))

        # Analyze the data grouping measurements by compartment (e.g., axon,
        # dendrite). See TreeAnalyzer's API for more sophisticated analysis
        analyzer.summarize(True) # Split results by compartment?

        # Update table before parsing next file
        analyzer.updateAndDisplayTable()

    log.info('Done. %s file(s) analyzed...' % (counter + 1))  
    if table.getRowCount() == 0:
        uiservice.showDialog("No files were analyzed", "Error")
        

run()
