#@Context context
"""
file:       Graph_Analysis.py
author:     Tiago Ferreira, Cameron Arshadi
version:    20190402
info:       Demonstrates how to handle neurons as graph structures[1] (graph theory)
            in which nodes connected by edges define the morphology of the neuron.
            SNT represents neurons as directed graphs (assuming the root -typically
            the soma- as origin) and allows data be processed using the powerful
            jgrapht[2] library.
            In this demo, the graph diameter[3] (i.e., the length of the longest
            shortest path or the longest graph geodesic) of a cellular compartment
            is computed for a neuron fetched from the MouseLight database.
            [1] https://en.wikipedia.org/wiki/Graph_theory
            [2] https://jgrapht.org/
            [3] https://mathworld.wolfram.com/GraphDiameter.html
"""

import time

from fiji.sc.snt import Tree
from fiji.sc.snt.io import MouseLightLoader
from fiji.sc.snt.viewer import Viewer3D
from fiji.sc.snt.analysis.graph import GraphUtils

from org.jgrapht import Graphs, GraphPath, GraphTests
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
    print("Loading cell...")
    loader = MouseLightLoader("AA0004")

    # Get the SWCPoint representation of each dendritic node, which
    # contains all attributes of a single line in an SWC file.
    print("Extracting dendritic nodes...")
    nodes = loader.getNodes("dendrite")

    # Build a jgrapht Graph object from the nodes and assign
    # euclidean distance between adjacent nodes as edge weights.
    # Note that other weighs could be assigned using the jgrapht API.
    print("Assembling graph from %s vertices..." % len(nodes))
    graph = GraphUtils.createGraph(nodes, True)
    
    # When dealing with a relatively low number of vertices (<10k),
    # one can display the graph in SNT's dedicated canvas w/ controls
    # for export and visualization in its right-click menu.
    print("Displaying graph...")
    GraphUtils.show(graph)

    # Retrieve the root: the singular node with in-degree 0
    root = [node for node in graph.vertexSet() if graph.inDegreeOf(node) == 0][0]

    # Compute the longest shortest path using parent pointers
    t0 = time.time()
    tips = getGraphTips(graph)
    lsp = getTreeDiameter(graph)
    t1 = time.time()
    print("Shortest path (PP)=%s. Time: %ss" % (lsp, t1-t0))

    # Compute the longest shortest path using Dijkstra's algorithm,
    # which allows us to easily get the nodes along the path, albeit
    # more slowly.
    t0 = time.time()
    max_dist = 0
    grapht_path = None
    for tip in tips:
        dsp = DijkstraShortestPath(graph)
        dist = dsp.getPathWeight(root, tip)
        if dist > max_dist:
            max_dist = dist
            grapht_path = dsp.getPath(root, tip)
    t1 = time.time()
    print("Shortest path (DSP)=%s. Time: %ss" % (max_dist, t1-t0))

    # Visualize the longest path in Viewer3D (interactive instance)
    viewer = Viewer3D(context)

    # Import results as sc.fiji.snt.Tree objects expected by Viewer3D
    snt_tree_input_nodes = Tree(nodes, "Input nodes")
    snt_tree_input_nodes.setColor("cyan")
    viewer.add(snt_tree_input_nodes)

    shortest_path_vertices = grapht_path.getVertexList()
    snt_tree_shortest_path = Tree(shortest_path_vertices, "Dijkstra shortest path")
    snt_tree_shortest_path.setColor("orange")

    # Highlight the shortest path by offsetting it laterally by 10um
    snt_tree_shortest_path.translate(10,10,0)
    viewer.add(snt_tree_shortest_path)
    viewer.show()

run()
