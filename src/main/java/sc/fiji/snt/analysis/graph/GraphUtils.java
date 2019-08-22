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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.scijava.util.Colors;

import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.layout.mxIGraphLayout;

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
	public static DefaultDirectedGraph<SWCPoint, DefaultWeightedEdge> createGraph(final Collection<SWCPoint> nodes,
	                                                                              final boolean assignDistancesToWeights) {
		return createGraphInternal(nodes, assignDistancesToWeights);
	}

	private static DirectedWeightedGraph<SWCPoint, DefaultWeightedEdge> createGraphInternal(final Collection<SWCPoint> nodes,
            final boolean assignDistancesToWeights) {
		final Map<Integer, SWCPoint> map = new HashMap<>();
		final DirectedWeightedGraph<SWCPoint, DefaultWeightedEdge> graph = new DirectedWeightedGraph<SWCPoint, DefaultWeightedEdge>();
		for (final SWCPoint node : nodes) {
			map.put(node.id, node);
			graph.addVertex(node);
		}
		for (final Entry<Integer, SWCPoint> entry : map.entrySet()) {
			final SWCPoint point = entry.getValue();
			if (point.parent == -1)
				continue;
			final SWCPoint previousPoint = map.get(point.parent);
			point.setPreviousPoint(previousPoint);
			final DefaultWeightedEdge edge = new DefaultWeightedEdge();
			graph.addEdge(previousPoint, point, edge);
			if (assignDistancesToWeights) {
				final double xd = point.x - previousPoint.x;
				final double yd = point.y - previousPoint.y;
				final double zd = point.z - previousPoint.z;
				final double sqDistance = xd * xd + yd * yd + zd * zd;
				graph.setEdgeWeight(edge, Math.sqrt(sqDistance));
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
	public static DefaultDirectedGraph<SWCPoint, DefaultWeightedEdge> createGraph(final Tree tree) {
		final DirectedWeightedGraph<SWCPoint, DefaultWeightedEdge> graph = createGraphInternal(tree.getNodes(), true);
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
	 * Displays a graph in a dedicated window featuring UI commands for interactive
	 * visualization and export options.
	 *
	 * @param       <V> the graph vertex type
	 * @param       <E> the graph edge type
	 * @param graph the graph to be displayed
	 * @return the assembled window
	 */
	public static <V, E> Window show(final Graph<V, E> graph) {
		GuiUtils.setSystemLookAndFeel();
		final JDialog frame = new JDialog((JFrame)null, "SNT Graph Canvas");
		final TreeGraphAdapter<?, ?> graphAdapter = new TreeGraphAdapter<>(graph);
		final mxIGraphLayout layout = new mxCompactTreeLayout(graphAdapter);
		layout.execute(graphAdapter.getDefaultParent());
		final TreeGraphComponent graphComponent = new TreeGraphComponent(graphAdapter);
		frame.add(graphComponent);
		frame.pack();
		final Dimension maxDim = Toolkit.getDefaultToolkit().getScreenSize();
		final Dimension prefDim = frame.getPreferredSize();
		frame.setPreferredSize(new Dimension((int) Math.min(prefDim.getWidth(), maxDim.getWidth() / 2),
				(int) Math.min(prefDim.getHeight(), maxDim.getHeight() / 2)));
		frame.pack();
		graphComponent.zoomActual();
		SwingUtilities.invokeLater(() -> frame.setVisible(true));
		return frame;
	}

	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		SNTUtils.setDebugMode(true);
		SNTService sntService = ij.context().getService(SNTService.class);
		final Tree tree = sntService.demoTree();
		tree.setColor(Colors.RED);
		DefaultDirectedGraph<SWCPoint, DefaultWeightedEdge> graph = tree.getGraph();
		final Viewer3D recViewer = new Viewer3D(ij.context());
		final Tree convertedTree = GraphUtils.createTree(graph);
		convertedTree.setColor(Colors.CYAN);
		recViewer.add(tree);
		recViewer.add(convertedTree);
		recViewer.show();
		GraphUtils.show(graph);
	}
}