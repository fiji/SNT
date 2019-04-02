"""
file:       Tree_Diameter.py
author:     Tiago Ferreira, Cameron Arshadi
version:    20190210
info:       A Jython demo which computes the diameter (graph theory) of a traced compartment
            fetched from the MouseLight database.
"""

import time

from tracing import Tree
from tracing.io import MouseLightLoader
from tracing.viewer import OBJMesh, Viewer3D
from tracing.viewer.Viewer3D import ViewMode
from tracing.analysis.graph import GraphUtils

from org.jgrapht import GraphTests, Graphs
from org.jgrapht import GraphPath
from org.jgrapht.alg.shortestpath import DijkstraShortestPath


def getGraphTips(graph):
    """Return all terminal nodes (out-degree 0) of the graph."""
    
    return [node for node in graph.vertexSet() if graph.outDegreeOf(node) == 0]


def distanceToRoot(graph, n):
    """Return graph geodesic distance from node n to tree root."""
    
    distance = 0
    if n.parent == -1:
        return 0
    while n.parent != -1:
        pred = Graphs.predecessorListOf(graph, n)[0]
        incoming_edge = [edge for edge in graph.incomingEdgesOf(n)][0]
        weight = graph.getEdgeWeight(incoming_edge)
        distance += weight
        n = pred
        
    return distance


def getTreeDiameter(graph):
    """Return graph diameter, which for rooted directed trees
    is the longest shortest path between the root and any terminal node."""
    
    tips = getGraphTips(graph)
    max_dist = 0
    for tip in tips:
        dist = distanceToRoot(graph, tip)
        if dist > max_dist:
            max_dist = dist
            
    return max_dist


def run():

    # Fetch a neuron from the MouseLight database.
    loader = MouseLightLoader("AA0100")

    # Get the SWCPoint representation of each node,
    # which contains all attributes of a single
    # line in an SWC file.
    nodes = loader.getNodes("axon")

    # Build a jgrapht Graph object from the nodes
    # and assign edge weights. Note that the
    # default edge weight is euclidean distance
    # between adjacent nodes.
    graph = GraphUtils.createGraph(nodes, True)

    # The root of the tree is the singular node with in-degree 0.
    root = [node for node in graph.vertexSet() if graph.inDegreeOf(node) == 0][0]

    # Tree diameter using parent pointers.
    t0 = time.time()
    tips = getGraphTips(graph)
    print(getTreeDiameter(graph))
    t1 = time.time()
    print(t1-t0)

    # Tree diameter using Dijkstra, which allows us
    # to easily get the nodes along the path, albeit
    # more slowly. Note that the above approach using
    # parent pointers could be trivially modified to
    # produce the nodes of the path.
    t0 = time.time()
    max_dist = 0
    grapht_path = None
    for tip in tips:
        dsp = DijkstraShortestPath(graph)
        dist = dsp.getPathWeight(root, tip)
        if dist > max_dist:
            max_dist = dist
            grapht_path = dsp.getPath(root, tip)

    print(max_dist)
    t1 = time.time()
    print(t1-t0)

    # Visualize the longest path with Viewer3D
    viewer = Viewer3D()

    # Viewer3D accepts a tracing.Tree
    snt_tree_input_nodes = Tree(nodes, "input nodes")
    snt_tree_input_nodes.setColor("cyan")
    viewer.add(snt_tree_input_nodes)

    # Convert longest path to tracing.Tree
    snt_tree_shortest_path = Tree(grapht_path.getVertexList(), "Dijkstra Shortest Path")
    snt_tree_shortest_path.setColor("orange")

    # Translate the path of the tree diameter to avoid color overlap
    # with underlying path.
    snt_tree_shortest_path.translate(30,30,30)
    viewer.add(snt_tree_shortest_path)

    viewer.show()


run()
