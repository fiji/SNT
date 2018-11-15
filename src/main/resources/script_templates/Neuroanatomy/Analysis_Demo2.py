# @Context context
# @LegacyService ls
# @DatasetService ds
# @DisplayService display
# @LogService log
# @SNTService snt
# @StatusService status
# @UIService ui


'''
file:       Analysis_Demo2.py
author:     Tiago Ferreira
version:    20180928
info:       
'''

import math
from collections import defaultdict

from ij.gui import Plot

from tracing import (Path, PathAndFillManager, SimpleNeuriteTracer, SNTUI, Tree)
from tracing.io import MLJSONLoader
from tracing.util import PointInImage
from tracing.analysis import (RoiConverter, TreeAnalyzer, TreeColorizer, 
    TreeStatistics)
from tracing.plot import(TreePlot2D, TreePlot3D)

def run():

    # Import some data from the MouseLight database in 'headless' mode
    loader = MLJSONLoader("AA0100") # one of the largest cells in the database
    if not loader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        ui.showDialog("Somewhow the specified id was not found", "Error")
        return

    d_tree = loader.getTree('dendrites', None)  # compartment, color
    a_tree = loader.getTree('axon', None)
    for tree in [d_tree, a_tree]:

        print("Parsing %s" % tree.getLabel())
        d_stats = TreeStatistics(tree)

        # NB: SummaryStatistics should be more performant than DescriptiveStatistics
        ssummary = d_stats.getSummaryStats(TreeStatistics.INTER_NODE_DISTANCE)
        print("The average inter-node distance is %d" % ssummary.getMean())

        dsummary = d_stats.getDescriptiveStats(TreeStatistics.INTER_NODE_DISTANCE)
        print("The average inter-node distance is %d" % dsummary.getMean())
        
        # We can calculate the approximated volume of a tracing
        compartment_volume = 0
        manager = PathAndFillManager()
        for path in tree.list():
            manager.addPath(path)
            compartment_volume += path.getApproximatedVolume()

        bb = manager.getBoundingBox(True)
        bb_dim = bb.getDimensions(False)
        bb_volume = bb_dim[0] * bb_dim[1] * bb_dim[2]
        print("Volume of bounding box containing all nodes is %d" % bb_volume)
        print("Approximate volume of tracing is %d cubic microns" % compartment_volume)
        print("Tracing uses %d percent of space given by bounding box" % ((compartment_volume/bb_volume)*100))

        # We can look at how mean burke taper changes with branch order
        order_dict = defaultdict(list)
        for path in tree.list():
            Da = path.getNodeRadius(0) * 2
            Db = path.getNodeRadius(path.size()-1) * 2
            path_length = path.getLength()
            try:
                burke_taper = (Da - Db) / path_length
                order_dict[path.getOrder()].append(burke_taper)
            # appears to be finding length 0 paths
            except ZeroDivisionError:
                continue
        l = []
        
        for item in order_dict.items():
            mean_taper = sum(item[1])/len(item[1])
            l.append((item[0], mean_taper))
            
        l = sorted(l, key = lambda x:x[0])
        xs, ys = zip(*l)
        plot = Plot('Mean Burke taper vs. Branch Order', 'branch order', 'mean taper')
        plot.add('x', xs, ys)
        plot.show()
        
        # We may also calculate the longest path from root to endpoint
        # TODO


run()
