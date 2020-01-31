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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import sc.fiji.snt.Tree;
import sc.fiji.snt.util.SWCPoint;

/**
 * Class for accessing a reconstruction as a graph structure.
 * 
 * @author Tiago Ferreira
 * @author Cameron Arshadi
 */
public class DirectedWeightedGraph extends DefaultDirectedWeightedGraph<SWCPoint, SWCWeightedEdge> {

	private static final long serialVersionUID = 1L;
	private Tree tree;

	/**
	 * Creates a DirectedWeightedGraph from a Tree with edge weights corresponding
	 * to inter-node distances.
	 *
	 * @param tree the Tree to be converted
	 * @throws IllegalArgumentException if Tree contains multiple roots
	 */
	public DirectedWeightedGraph(final Tree tree) throws IllegalArgumentException {
		super(SWCWeightedEdge.class);
		this.tree = tree;
		init(tree.getNodesAsSWCPoints(), true);
	}

	protected DirectedWeightedGraph() {
		super(SWCWeightedEdge.class);
		this.tree = null;
	}

	/**
	 * Creates a DirectedWeightedGraph from a collection of reconstruction nodes.
	 *
	 * @param nodes                    the collections of SWC nodes
	 * @param assignDistancesToWeights if true, inter-node Euclidean distances are
	 *                                 used as edge weights
	 */
	protected DirectedWeightedGraph(final Collection<SWCPoint> nodes, final boolean assignDistancesToWeight)
			throws IllegalArgumentException {
		this();
		init(nodes, assignDistancesToWeight);
	}

	private void init(final Collection<SWCPoint> nodes, final boolean assignDistancesToWeights)
			throws IllegalArgumentException {
		final Map<Integer, SWCPoint> map = new HashMap<>();
		for (final SWCPoint node : tree.getNodesAsSWCPoints()) {
			map.put(node.id, node);
			addVertex(node);
		}
		for (final SWCPoint node : nodes) {
			if (node.parent == -1)
				continue;
			final SWCPoint previousPoint = map.get(node.parent);
			node.setPreviousPoint(previousPoint);
			final SWCWeightedEdge edge = new SWCWeightedEdge();
			addEdge(previousPoint, node, edge);
			if (assignDistancesToWeights) {
				setEdgeWeight(edge, node.distanceTo(previousPoint));
			}
		}
	}

	/**
	 * Returns a simplified version graph in which slab nodes are removed and graph
	 * is represented only by root, branch nodes and leaves.
	 *
	 * @return the simplified graph
	 */
	public DirectedWeightedGraph getSimplifiedGraph() {
		final LinkedHashSet<SWCPoint> relevantNodes = new LinkedHashSet<>();
		relevantNodes.add(getRoot());
		relevantNodes.addAll(getBPs());
		relevantNodes.addAll(geTips());
		final DirectedWeightedGraph simplifiedGraph = new DirectedWeightedGraph();
		transferCommonProperties(simplifiedGraph);
		relevantNodes.forEach(node -> simplifiedGraph.addVertex(node));
		for (final SWCPoint node : relevantNodes) {
			final SimplifiedVertex ancestor = firstRelevantAncestor(node);
			if (ancestor != null && ancestor.associatedWeight > 0) {
				try {
					final SWCWeightedEdge edge = simplifiedGraph.addEdge(ancestor.vertex, node);
					simplifiedGraph.setEdgeWeight(edge, ancestor.associatedWeight);
				} catch (final IllegalArgumentException ignored) {
					// do nothing. ancestor.vertex not found in simplifiedGraph
				}
			}
		}
		return simplifiedGraph;
	}

	private SimplifiedVertex firstRelevantAncestor(SWCPoint node) {
		if (!Graphs.vertexHasPredecessors(this, node)) {
			return null;
		}
		double pathWeight = 0;
		SWCPoint parent;
		while (true) {
			try {
				parent = Graphs.predecessorListOf(this, node).get(0);
				final double edgeWeight = getEdge(parent, node).getWeight();
				pathWeight += edgeWeight;
				if (inDegreeOf(parent) == 0 || outDegreeOf(parent) > 1) {
					return new SimplifiedVertex(parent, pathWeight);
				}
				node = parent;
			} catch (final IndexOutOfBoundsException | NullPointerException ignored) {
				return null;
			}
		}
	}

	private class SimplifiedVertex {
		final SWCPoint vertex;
		final double associatedWeight;

		SimplifiedVertex(final SWCPoint vertex, final double associatedWeight) {
			this.vertex = vertex;
			this.associatedWeight = associatedWeight;
		}
	}

	private void transferCommonProperties(final DirectedWeightedGraph otherGraph) {
		otherGraph.tree = this.tree;
	}

	/**
	 * Gets the branch points (junctions) of the graph.
	 *
	 * @return the list of branch points
	 */
	public List<SWCPoint> getBPs() {
		return vertexSet().stream().filter(v -> outDegreeOf(v) > 1).collect(Collectors.toList());
	}

	/**
	 * Gets the end points (tips) of the graph.
	 *
	 * @return the list of end points
	 */
	public List<SWCPoint> geTips() {
		return vertexSet().stream().filter(v -> outDegreeOf(v) == 0).collect(Collectors.toList());
	}

	/**
	 * Gets the root of this graph.
	 *
	 * @return the root node.
	 */
	public SWCPoint getRoot() {
		return vertexSet().stream().filter(v -> inDegreeOf(v) == 0).findFirst().orElse(null);
	}

	/**
	 * Returns the Tree associated with this graph.
	 *
	 * @return the tree (or null if no association exists)
	 */
	public Tree getTree() {
		return tree;
	}

	/**
	 * Displays this graph in a new instance of SNT's "Dendrogram Viewer".
	 *
	 * @return a reference to the displayed window.
	 */
	public Window show() {
		return GraphUtils.show(this);
	}
}
