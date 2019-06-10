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
version:    20190610
info:       Exemplifies how to programmatically interact with a running instance
            of SNT to analyze traced data. Because of all the GUI updates, this
            approach is _significantly slower_ than analyzing reconstructions
            directly (see Analysis_Demo.py for comparison)
"""

import math

from sc.fiji.snt import (Path, PathAndFillManager, SNT, SNTUI, SNTUtils,Tree)
from sc.fiji.snt.util import PointInImage
from sc.fiji.snt.analysis import (RoiConverter, TreeAnalyzer, TreeColorMapper, 
    TreeStatistics)
from sc.fiji.snt.viewer import(Viewer2D, Viewer3D)


def run():

    # The SNTService contains convenience methods that will simplify
    # our script. For more advanced features we'll script other classes
    # directly, but we'll use SNTService whenever pertinent. Now, lets
    # ensure SNT is currently running
    # http://javadoc.scijava.org/Fiji/sc/fiji/tracing/SNTService.html
    if not snt.isActive():
        ui.showDialog("SNT does not seem to be running. Exiting..", "Error")
        return

    # Let's import some demo data
    demo_tree = snt.loadDemoTree()
    if not demo_tree:
        ui.showDialog("Somehow could not load bundled file.", "Error")
        return

    # Pause tracing functions
    snt.getUI().changeState(SNTUI.TRACING_PAUSED)

    # Almost all SNT analyses are performed on a Tree, i.e., a collection
    # of Paths. We can immediately retrieve TreeAnalyzer (responsible for
    # analyzing Trees) or TreeStatistics (responsible for computing
    # descriptive statistics for univariate properties of a Tree) instances
    # from SNTService.
    # These instances can be built from all the Paths currently loaded, or
    # just the current subset of selected Paths in the Path Manager dialog.
    # This is useful when one only wants to analyze the groups of Paths
    # selected using the Filtering toolbar of the Path Manager.
    # http://javadoc.scijava.org/Fiji/sc/tracing/tracing/analysis/TreeAnalyzer.html
    # http://javadoc.scijava.org/Fiji/sc/tracing/analysis/TreeStatistics.html
    analyzer = snt.getAnalyzer(False)  # Include only selected paths?
    stats = snt.getStatistics(False)   # Include only selected paths?

    # Measurements can be displayed in SNT's UI:
    analyzer.summarize("TreeV Demo", True) # Split summary by compartment?
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
    analyzer.summarize("TreeV Demo (Downsampled)", True)
    analyzer.updateAndDisplayTable()
    print("After downsampling: %d" % summary_stats.getMin())

    # To overlay the downsampled tree against the original:
    tree.setColor("cyan")
    tree = snt.loadDemoTree()
    tree.setColor("yellow")
    snt.getReconstructionViewer().show()
    snt.getPlugin().updateAllViewers()

run()
