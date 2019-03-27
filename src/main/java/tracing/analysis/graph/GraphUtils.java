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

package tracing.analysis.graph;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import javax.swing.JFrame;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.layout.mxIGraphLayout;

import tracing.gui.GuiUtils;
import tracing.io.MouseLightLoader;
import tracing.util.SWCPoint;

public class GraphUtils {

	private GraphUtils() {
	}

	/**
	 * Creates a DirectedGraph from a collection of reconstruction nodes.
	 *
	 * @param nodes                    the collections of SWC nodes
	 * @param assignDistancesToWeights if true, inter-node Eucledian distances are
	 *                                 used as edge weights
	 * @return the created graph
	 */
	public static DefaultDirectedGraph<SWCPoint, DefaultWeightedEdge> createGraph(final TreeSet<SWCPoint> nodes,
			final boolean assignDistancesToWeights) {
		final Map<Integer, SWCPoint> map = new HashMap<>();
		final DefaultDirectedWeightedGraph<SWCPoint, DefaultWeightedEdge> graph = new DefaultDirectedWeightedGraph<SWCPoint, DefaultWeightedEdge>(
				null);
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
	 * Assembles a frame holding an UI commands for interacting with a graph
	 *
	 * @param       <V> the graph vertex type
	 * @param       <E> the graph edge type
	 * @param graph the graph to be displayed
	 * @return the assembled frame
	 */
	public static <V, E> JFrame assembleFrame(final Graph<V, E> graph) {
		GuiUtils.setSystemLookAndFeel();
		final JFrame frame = new JFrame("DemoGraph");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
		frame.setLocationByPlatform(true);
		frame.setTitle("SNT Graph Canvas");
		return frame;
	}

	public static void main(final String[] args) {
		try {
			final File file = new File("/home/tferr/Downloads/AA0001.json");
			final Map<String, TreeSet<SWCPoint>> map = MouseLightLoader.extractNodes(file, "dendrite");
			final String firstCell = map.keySet().iterator().next();
			final DefaultDirectedGraph<SWCPoint, DefaultWeightedEdge> graph = GraphUtils.createGraph(map.get(firstCell),
					true);
			GraphUtils.assembleFrame(graph).setVisible(true);
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}