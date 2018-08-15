# @Context context
# @LegacyService ls
# @DatasetService ds
# @DisplayService display
# @LogService log
# @SNTService snt
# @StatusService status
# @UIService ui


'''
file:       Scripting_SNT_Demo.ey
author:     Tiago Ferreira
version:    20180620
info:       Exemplifies how to programatically interact with a running instance
            of SimpleNeuriteTracer to perform auto-tracing tasks.
'''

import math

from tracing import (Path, PathAndFillManager, SimpleNeuriteTracer, Tree)
from tracing.util import PointInImage
from tracing.analysis import (RoiConverter, TreeAnalyzer, TreeColorizer, 
    TreePlot, TreeStatistics)


def run():

    # Exit if SNT is not running
    if not snt.isActive():
        ui.showDialog("SNT does not seem to be running. Exiting..", "Error")
        return
    if not snt.isUIReady():
        ui.showDialog("Demo cannot run in current state: UI not ready", "Error")
        return

    # For basic functionality we can call SNTService directly: E.g.:
    # http://javadoc.scijava.org/Fiji/tracing/SNTService.html
    print("There are currently %s traced paths" % snt.getPaths().size())
    print("...of which %s are selected" % snt.getSelectedPaths().size())
    
    # But for more advanced features, we need to access SimpleNeuriteTracer
    # and PathAndFillManager (the latter manages all things related to Paths):
    # http://javadoc.scijava.org/Fiji/tracing/SimpleNeuriteTracer.html
    # http://javadoc.scijava.org/Fiji/tracing/PathAndFillManager.html
    plugin = snt.getPlugin()
    pafm = snt.getPathAndFillManager()

    # Now let's do some auto-tracing. But first let's remember the current
    # status of the plugin so that we can restore things at the end
    state = plugin.getUI().getState()
    astar_enabled = plugin.isAstarEnabled()

    # Let's first announce (discretely) our scripting intentions
    msg = "SNT is being scripted!"
    plugin.getUI().showStatus(msg)
    plugin.setCanvasLabelAllPanes(msg)
    snt.updateViewers()

    # In a real-world scenario we would have a routine in place to detect seed
    # points for auto-tracing. For this demo, let's just use the center pixel
    # of the Z-plane currently being traced as the start [s] point. Because this
    # demo knows nothing about the current image (and this center voxel), we'll
    # tur-off the A* Search algorithm
    plugin.enableAstar(False)
    imp = plugin.getImagePlus()
    dim = imp.getDimensions()
    sx = imp.getCalibration().getX(dim[0]) / 2
    sy = imp.getCalibration().getY(dim[0]) / 2
    z = imp.getCalibration().getZ(imp.getCurrentSlice() - 1)

    # Define a Tree (a collection of Paths) to hold all the paths we'll create
    tree = Tree()

    # Let's use some other random point for the end-point [e] of the Path, such 
    # as the top-center voxel (we need at least 2 points to auto-trace a Path)
    ex = sx
    ey = 0

    # Lets's compute the path between these two seeds (since A* is disabled,
    # the path will be a straight line between the two points). A point in the
    # tracing space (always in spatially calibrated units!) is defined through
    # a PointInImage object
    # http://javadoc.scijava.org/Fiji/tracing/utils/PointInImage.html
    p = plugin.autoTrace(PointInImage(sx,sy,z), PointInImage(ex,ey,z), None)
    tree.add(p)

    # Cool. It worked! We can also compute paths from a list of points. Let's
    # create a bunch of child paths, e.g., by rotating the parent path above
    fork_point = p.getPointInImage(0)  # 0-based index
    for deg_angle in range(10,  360, 10):
        angle = math.radians(deg_angle)
        rot_x = sx + math.cos(angle) * (ex - sx) - math.sin(angle) * (ey - sy)
        rot_y = sy + math.sin(angle) * (ex - sx) + math.cos(angle) * (ey - sy)
        path_nodes = []
        path_nodes.append(PointInImage(sx, sy, z))
        path_nodes.append(PointInImage(rot_x, rot_y, z))
        child = plugin.autoTrace(path_nodes, fork_point)
        tree.add(child)

    # Now we could, e.g., find out the fluorescent intensities along a path,
    # calculate its node diameters ("fit it" in SNT lingo), or use it to
    # generate a mask ("fill it" in SNT's lingo). For simplicity, let's just
    # get some measurements out of the paths computed so far. The class to
    # script is tracing.analysis.TreeAnalyzer, or its subclass TreeStatistics
    # http://javadoc.scijava.org/Fiji/tracing/analysis/TreeAnalyzer.html
    # http://javadoc.scijava.org/Fiji/tracing/analysis/TreeStatistics.html
    tree_stats = TreeStatistics(tree)
    tree_stats.setContext(context)
    s_stats = tree_stats.getSummaryStats("length")
    print("The length variance is: %s" % s_stats.getVariance())
    print("... The sum of logs is: %s" % s_stats.getSumOfLogs())

    # Let's also append some data to SNT's table
    table = snt.getTable()
    tree_stats.setTable(table)
    tree_stats.summarize("Scripted Paths", False)
    tree_stats.updateAndDisplayTable()

    # Remaining analysis classes can be access using the same scripting
    # pattern. E.g., to plot paths colored by rotation angle:
    # http://javadoc.scijava.org/Fiji/tracing/analysis/TreePlot.html
    plot = TreePlot(context)
    plot.addTree(tree, "y coordinates", "Ice.lut")
    plot.addLookupLegend()
    plot.setTitle("Scripted Paths Rotation Plot")
    plot.showPlot()

    # Finally, we restore the plugin to its initial status
    ui.showDialog("Press OK to clear scripted paths", "Script Terminated")
    for path in tree.list():
        pafm.deletePath(path)
    plugin.changeUIState(state)
    plugin.enableAstar(astar_enabled)
    plugin.setCanvasLabelAllPanes(None)
    snt.updateViewers()


run()

