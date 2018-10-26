# @Context context
# @LegacyService ls
# @DatasetService ds
# @DisplayService display
# @LogService log
# @SNTService snt
# @StatusService status
# @UIService ui


'''
file:       Scripted_Tracing_Demo.py
author:     Tiago Ferreira
version:    20180918
info:       Exemplifies how to programatically interact with a running instance
            of SimpleNeuriteTracer to analyze traced data
'''

import math

from tracing import (Path, PathAndFillManager, SimpleNeuriteTracer, SNTUI, Tree)
from tracing.io import MLJSONLoader
from tracing.util import PointInImage
from tracing.analysis import (RoiConverter, TreeAnalyzer, TreeColorizer, 
    TreeStatistics)
from tracing.plot import(TreePlot2D, TreePlot3D)


def run():

    # The SNTService contains convenience methods that will simplify
    # our script. For more advanced features we'll script other classes
    # directly, but we'll use SNTService whenever pertinent. Now, lets
    # ensure SNT is currently running
    # http://javadoc.scijava.org/Fiji/tracing/SNTService.html
    if not snt.isActive():
        ui.showDialog("SNT does not seem to be running. Exiting..", "Error")
        return
 
    # Reload UI in 'Analysis Mode' if running in 'Tracing Mode'
    if snt.getUI().getCurrentState() != SNTUI.ANALYSIS_MODE:
        SNTUI.reloadUI(snt.getUI(), True)

    # Let's import some data from the MouseLight database
    loader = MLJSONLoader("AA0100")
    if not loader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        ui.showDialog("Somewhow the specified id was not found", "Error")
        return

    # All the 'raw data' in the MouseLight database is stored as JSONObjects.
    # These can be access as follows:
    # http://stleary.github.io/JSON-java/index.html
    axon = loader.getCompartment("axon")  # MLJSONLoader.AXON
    print(axon)

    # But we can import reconstructions directly using PathAndFillManager,
    # SNT's work horse for management of Paths and ints importMLNeurons
    # method that accepts two parameters: a list of cell ids, and the
    # compartment to be imported ('all', 'axon', or 'dendrites')
    # http://javadoc.scijava.org/Fiji/tracing/PathAndFillManager.html
    pafm = snt.getPathAndFillManager()
    pafm.importMLNeurons(['AA0100'], 'dendrites')

    # Almost all SNT analyses are performed on a Tree, i.e., a collection
    # of Paths. We can immediately retrieve TreeAnalyzer (responsible for
    # analyzing Trees) or TreeStatistics (responsible for computating
    # descriptive statistics for univariate properties of a Tree) instances
    # from SNTService.
    # These instances can be built from all the Paths currently loaded, or
    # just the current subset of selected Paths in the Path Manager dialog.
    # This is useful when one only wants to analyze the groups of Paths
    # selected using the Filtering toolbar of the Path Manager.
    # http://javadoc.scijava.org/Fiji/tracing/analysis/TreeAnalyzer.html
    # http://javadoc.scijava.org/Fiji/tracing/analysis/TreeStatistics.html
    analyzer = snt.getAnalyzer(False)  # Include only selected paths?
    stats = snt.getStatistics(False)   # Include only selected paths?

    summary_stats = stats.getSummaryStats(TreeStatistics.INTER_NODE_DISTANCE)
    print("The average inter-node distance is %d" % summary_stats.getMean())

	# Stats can also be displayed in SNT's ui.
	# TBD:  recycle code from Scripted_Tracing_Demo.py

    # It is also possible to build the above instances from a Tree. This
    # is required if e.g, one needs to manipulate Paths before hand
    # NB: rotation, and scaling of Trees can be computer intensive
    tree = snt.getTree(False) # Retrieve only paths selected in the Manager?
    tree.rotate(Tree.X_AXIS, 90) # rotate 90 degrees along the X axis

    # Let's see the result:
    #TBD, recycle code from Scripted_Tracing_Demo.py

run()
