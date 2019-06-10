# @Context context
# @LegacyService ls
# @DatasetService ds
# @DisplayService display
# @LogService log
# @SNTService snt
# @StatusService status
# @UIService ui
# @PlotService plotService

"""
file:       Analysis_Demo2.py
author:     Tiago Ferreira, Cameron Arshadi
version:    20190210
info:       A Jython demo of how SNT can analyze neuron reconstructions fetched
            from online databases such as MouseLight, NeuroMorpho or FlyCircuit
"""

import math
from collections import defaultdict

from sc.fiji.snt import (Path, PathAndFillManager, SNT, SNTUI, Tree)
from sc.fiji.snt.analysis import (RoiConverter, MultiTreeColorMapper, TreeAnalyzer,
        TreeColorMapper, TreeStatistics)
from sc.fiji.snt.io import (MouseLightLoader, NeuroMorphoLoader)
from sc.fiji.snt.viewer import (Viewer2D, Viewer3D)


def run():
    # Import some data from the MouseLight database in 'headless' mode
    loader = MouseLightLoader("AA0100")  # one of the largest cells in the database
    if not loader.isDatabaseAvailable():
        ui.showDialog("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        ui.showDialog("Somehow the specified id was not found", "Error")
        return

    d_tree = loader.getTree('dendrites', None)  # compartment, color
    a_tree = loader.getTree('axon', None)
    for tree in [d_tree, a_tree]:

        print("Parsing %s" % tree.getLabel())
        d_stats = TreeStatistics(tree)

        # NB: SummaryStatistics should be more performant than DescriptiveStatistics
        # http://javadoc.scijava.org/Fiji/tracing/analysis/TreeStatistics.html
        metric = TreeStatistics.INTER_NODE_DISTANCE  # same as "inter-node distance"
        summary_stats = d_stats.getSummaryStats(metric)
        d_stats.getHistogram(metric).show()
        print("The average inter-node distance is %d" % summary_stats.getMean())

        dsummary = d_stats.getDescriptiveStats(TreeStatistics.INTER_NODE_DISTANCE)
        print("The average inter-node distance is %d" % dsummary.getMean())

        # We can get the volume of a compartment by approximating the volume of
        # each path, and summing to total. For info on the assumptions made in
        # the volume calculation, see #getApproximatedVolume() at
        # https://github.com/fiji/Simple_Neurite_Tracer/blob/scijava/src/main/java/tracing/Path.java
        compartment_volume = 0
        for path in tree.list():
            compartment_volume += path.getApproximatedVolume()
        print("Approximate volume of tracing is %d cubic microns" % compartment_volume)

        # Let's find the dimensions of the minimum bounding box containing all existing nodes.
        # https://github.com/fiji/Simple_Neurite_Tracer/blob/scijava/src/main/java/tracing/util/BoundingBox.java
        bb = tree.getBoundingBox(True)
        bb_dim = bb.getDimensions(False)
        print("Dimensions of the bounding box containing all nodes (in micrometers): {} x {} x {}".format(*bb_dim))

    # To load a neuron from NeuroMorpho.org, use NeuroMorphoLoader.
    # We choose a reconstruction with traced node radii.
    # http://neuromorpho.org/neuron_info.jsp?neuron_name=Adol-20100419cell1
    # https://github.com/fiji/Simple_Neurite_Tracer/blob/scijava/src/main/java/tracing/io/NeuroMorphoLoader.java
    nm_loader = NeuroMorphoLoader()
    if nm_loader.isDatabaseAvailable():
        nm_tree = nm_loader.getTree("Adol-20100419cell1")

        # Like before, we can get stats using TreeStatistics and
        # plot them conveniently.
        if nm_tree is not None:
            nm_stats = TreeStatistics(nm_tree)
            nm_metric = TreeStatistics.MEAN_RADIUS
            nm_stats.getHistogram(nm_metric).show()

            # To build plots manually, use the PlotService script parameter.
            # https://github.com/maarzt/imagej-plot-service
            # For example, we can see how neurite radius
            # changes with centrifugal branch order.
            # For simplicity, we'll only consider the mean node radius of each path.
            order_dict = defaultdict(list)
            for p in nm_tree.list():
                r = p.getMeanRadius()
                order_dict[p.getOrder()].append(r)
            l = []
            for item in order_dict.items():  # average the mean path radius over each branch order
                mean_radius = sum(item[1]) / len(item[1])
                l.append((item[0], mean_radius))

            l = sorted(l, key=lambda x: x[0])
            xs, ys = zip(*l)

            plot = plotService.newXYPlot()
            plot.setTitle("Mean path radius vs Branch Order")
            series = plot.addXYSeries()
            series.setLabel("Adol-20100419cell1")
            series.setValues(xs, ys)
            ui.show(plot)


run()
