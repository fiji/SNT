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

from tracing import (Path, PathAndFillManager, SimpleNeuriteTracer, SNTUI, Tree)
from tracing.io import MouseLightLoader
from tracing.util import PointInImage
from tracing.analysis import (RoiConverter, TreeAnalyzer, TreeColorizer, 
    TreeStatistics)
from tracing.viewer import(Viewer2D, Viewer3D)

def run():

    # Import some data from the MouseLight database in 'headless' mode
    loader = MouseLightLoader("AA0100") # one of the largest cells in the database
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


run()
