# -*- coding: utf-8 -*-
"""
This python script demonstrates how to integrate functionality from
Simple Neurite Tracer with various python libraries through pyimagej.
It requires that the latest version of SNT is installed in your
ImageJ/Fiji environment.

Python dependencies:
- pyimagej
- numpy
- scipy
"""

import imagej
import numpy as np
from scipy.spatial import ConvexHull

# replace with path to your local ImageJ or Fiji installation
ij = imagej.init(r'C:\Users\cam\Desktop\fiji-win64\Fiji.app', headless=False)
from jnius import autoclass, cast

# import relevant Java classes
HashSet = autoclass('java.util.HashSet')
PointInImage = autoclass('tracing.util.PointInImage')
MouseLightLoader = autoclass('tracing.io.MouseLightLoader')
Tree = autoclass('tracing.Tree')
TreeAnalyzer = autoclass('tracing.analysis.TreeAnalyzer')
Color = autoclass('org.scijava.util.Colors')
Viewer = autoclass('tracing.viewer.Viewer3D')


def run():
    # fetch swc from MouseLight database by ID
    loader = MouseLightLoader('AA0265')
    if not loader.isDatabaseAvailable():
        print("Could not connect to ML database", "Error")
        return
    if not loader.idExists():
        print("Somewhow the specified id was not found", "Error")
        return

    tree = loader.getTree('axon', None)
    analyzer = TreeAnalyzer(tree)

    # extract the axonal end points from the tree as a java HashSet
    tips_java_set = analyzer.getTips()
    tips_iterator = tips_java_set.iterator()

    # convert to python list
    tips_list = []
    while tips_iterator.hasNext():
        n = tips_iterator.next()
        tips_list.append([n.x, n.y, n.z])

    assert len(tips_list) == tips_java_set.size()

    # Find the convex hull of the end points
    X = np.asarray(tips_list)
    hull = ConvexHull(X)
    print("The volume of the convex hull encapsulating all axonal end points is {} cubic microns".format(
        round(hull.volume, 2)))
    print("The area of the hull is {} square microns".format(round(hull.area, 2)))

    verts = X[hull.vertices]

    # construct new java Hashset containing the hull vertices
    verts_set = HashSet()
    for v in list(verts):
        verts_set.add(PointInImage(v[0], v[1], v[2]))

    # Visualize the result using SNT Viewer3D
    viewer = Viewer()
    #viewer.setDefaultColor(Color.GREEN)
    viewer.add(tree)
    viewer.addSurface(verts_set, Color.CORAL, 50)
    viewer.show()


run()

# HACK TO KEEP VIEWER WINDOW OPEN, PRESS <ENTER> TO QUIT
input()
