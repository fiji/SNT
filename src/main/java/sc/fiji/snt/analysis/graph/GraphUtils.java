/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt.analysis.graph;

import java.awt.Window;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.scijava.util.Colors;

import net.imagej.ImageJ;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.gui.GuiUtils;
import sc.fiji.snt.util.SWCPoint;
import sc.fiji.snt.viewer.Viewer3D;

public class GraphUtils {

	private GraphUtils() {
	}

	/**
	 * Creates a DirectedGraph from a collection of reconstruction nodes.
	 *
	 * @param nodes                    the collections of SWC nodes
	 * @param assignDistancesToWeights if true, inter-node Euclidean distances are
	 *                                 used as edge weights
	 * @return the created graph
	 */
	public static DefaultDirectedGraph<SWCPoint, SWCWeightedEdge> createGraph(final Collection<SWCPoint> nodes,
	                                                                              final boolean assignDistancesToWeights) {
		return createGraphInternal(nodes, assignDistancesToWeights);
	}

	private static DirectedWeightedGraph<SWCPoint, SWCWeightedEdge> createGraphInternal(final Collection<SWCPoint> nodes,
            final boolean assignDistancesToWeights) {
		final Map<Integer, SWCPoint> map = new HashMap<>();
		final DirectedWeightedGraph<SWCPoint, SWCWeightedEdge> graph = new DirectedWeightedGraph<SWCPoint, SWCWeightedEdge>();
		for (final SWCPoint node : nodes) {
			map.put(node.id, node);
			graph.addVertex(node);
		}
		for (final SWCPoint node : nodes) {
			if (node.parent == -1)
				continue;
			final SWCPoint previousPoint = map.get(node.parent);
			node.setPreviousPoint(previousPoint);
			final SWCWeightedEdge edge = new SWCWeightedEdge();
			graph.addEdge(previousPoint, node, edge);
			if (assignDistancesToWeights) {
				graph.setEdgeWeight(edge, node.distanceTo(previousPoint));
			}
		}
		return graph;
	}

	/**
	 * Creates a DirectedGraph from a Tree.
	 *
	 * @param tree the Tree to be converted
	 * @return the created graph with edge weights corresponding to inter-node
	 *         Euclidean distances
	 */
	public static DefaultDirectedGraph<SWCPoint, SWCWeightedEdge> createGraph(final Tree tree) throws IllegalArgumentException {
		final DirectedWeightedGraph<SWCPoint, SWCWeightedEdge> graph = createGraphInternal(tree.getNodesAsSWCPoints(), true);
		graph.setLabel(tree.getLabel());
		return graph;
	}

	/**
	 * Creates a {@link Tree} from a graph.
	 *
	 * @param graph the graph to be converted.
	 * @return the Tree, assembled from from the graph vertices
	 */
	public static Tree createTree(final DefaultDirectedGraph<SWCPoint, ?> graph) {
		String label = "";
		if (graph instanceof DirectedWeightedGraph)
			label = ((DirectedWeightedGraph<SWCPoint, ?>)graph).getLabel();
		return new Tree(graph.vertexSet(), label);
	}

	/**
	 * Displays a graph in a SNT's "Dendrogram Viewer" featuring UI commands for
	 * interactive visualization and export options.
	 *
	 * @param graph the graph to be displayed
	 * @return the assembled window
	 */
	public static Window show(final Graph<SWCPoint, SWCWeightedEdge> graph) {
		GuiUtils.setSystemLookAndFeel();
		final JDialog frame = new JDialog((JFrame) null, "SNT Dendrogram Viewer");
		final TreeGraphAdapter graphAdapter = new TreeGraphAdapter(graph);
		final TreeGraphComponent graphComponent = new TreeGraphComponent(graphAdapter);
		frame.add(graphComponent.getJSplitPane());
		frame.pack();
		SwingUtilities.invokeLater(() -> frame.setVisible(true));
		return frame;
	}

	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		SNTUtils.setDebugMode(true);
		SNTService sntService = ij.context().getService(SNTService.class);
		final Tree tree = sntService.demoTree();
		tree.downSample(Double.MAX_VALUE);
		tree.setColor(Colors.RED);
		final DefaultDirectedGraph<SWCPoint, SWCWeightedEdge> graph = tree.getGraph();
		final Viewer3D recViewer = new Viewer3D(ij.context());
		final Tree convertedTree = GraphUtils.createTree(graph);
		convertedTree.setColor(Colors.CYAN);
		recViewer.add(tree);
		recViewer.add(convertedTree);
		recViewer.show();
		GraphUtils.show(graph);
	}
}