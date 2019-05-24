# @Context context
# @LegacyService ls
# @DatasetService ds
# @DisplayService display
# @LogService log
# @SNTService snt
# @StatusService status
# @UIService ui


"""
file:       Analysis_Demo_(Interactive).py
author:     Tiago Ferreira
version:    20181126
info:       Exemplifies how to programmatically interact with a running instance
            of SNT to analyze traced data. Because of all the GUI updates, this
            approach is _significantly slower_ than analyzing reconstructions
            retrieved directly from the loader.
            Do check Analysis_Demo.py for comparison
"""

import math

from fiji.sc.snt import (Path, PathAndFillManager, SimpleNeuriteTracer, SNTUI, Tree)
from fiji.sc.snt.io import MouseLightLoader
from fiji.sc.snt.util import PointInImage
from fiji.sc.snt.analysis import (RoiConverter, TreeAnalyzer, TreeColorMapper, 
    TreeStatistics)
from fiji.sc.snt.viewer import(Viewer2D, Viewer3D)


def run():

    # The SNTService contains convenience methods that will simplify
    # our script. For more advanced features we'll script other classes
    # directly, but we'll use SNTService whenever pertinent. Now, lets
    # ensure SNT is currently running
    # http://javadoc.scijava.org/Fiji/tracing/SNTService.html
    if not snt.isActive():
        ui.showDialog("SNT does not seem to be running. Exiting..", "Error")
        return

    # Let's import some data from the MouseLight database
    loader = MouseLightLoader("AA0001")
    if not loader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        ui.showDialog("Somehow the specified id was not found", "Error")
        return

    # Pause tracing functions
    snt.getUI().changeState(SNTUI.TRACING_PAUSED)

    # All the 'raw data' in the MouseLight database is stored as JSONObjects.
    # If needed, these could be access as follows:
    # http://stleary.github.io/JSON-java/index.html
    axon = loader.getCompartment("axon")  # MouseLightLoader.AXON

    # But we can import reconstructions directly using PathAndFillManager,
    # SNT's work horse for management of Paths and ints importMLNeurons
    # method that accepts 3 parameters: a list of cell ids, the compartment
    # to be imported ('all', 'axon', or 'dendrites') and a color
    # http://javadoc.scijava.org/Fiji/tracing/PathAndFillManager.html
    pafm = snt.getPathAndFillManager()
    pafm.importMLNeurons(['AA0001'], 'all', None)

    # Almost all SNT analyses are performed on a Tree, i.e., a collection
    # of Paths. We can immediately retrieve TreeAnalyzer (responsible for
    # analyzing Trees) or TreeStatistics (responsible for computing
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

    # Measurements can be displayed in SNT's UI:
    analyzer.setTable(snt.getTable())
    analyzer.summarize("AA0001", True) # Split summary by compartment?
    analyzer.updateAndDisplayTable()

    # It is also possible to build the above instances from a Tree. This
    # is useful if, e.g, one needs to manipulate Paths in advanced.
    # NB: rotation, and scaling of large Trees can be computer intensive
    metric = TreeStatistics.INTER_NODE_DISTANCE # same as "inter-node distance"
    summary_stats = stats.getSummaryStats(metric)
    stats.getHistogram(metric).show()
    print("Smallest inter-node distance: %d" % summary_stats.getMin())

    # E.g., let's' downsample the Tree, imposing 10um between 'shaft' nodes.
    # NB: When downsampling, branch points and tip positions are not altered
    tree = snt.getTree(False) # Retrieve only paths selected in the Manager?
    tree.downSample(10) # 10um
    tree.setLabel("10um downsampled")

    # Let's compare the metric distribution after downsampling
    stats = TreeStatistics(tree)
    summary_stats = stats.getSummaryStats(metric)
    stats.getHistogram(metric).show()
    print("After downsampling: %d" % summary_stats.getMin())


run()
