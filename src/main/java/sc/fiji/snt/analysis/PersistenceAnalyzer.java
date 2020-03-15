package sc.fiji.snt.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graphs;

import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.graph.DirectedWeightedGraph;
import sc.fiji.snt.analysis.graph.SWCWeightedEdge;
import sc.fiji.snt.util.SWCPoint;

/**
 * Class to perform persistent homology analysis and vectorization on a
 * {@link Tree}.
 *
 * @author Tiago Ferreira
 * @author Cameron Arshadi
 */
public class PersistenceAnalyzer {

	private final Tree tree;
	private final HashMap<SWCPoint, ArrayList<Double>> persistenceMap = new HashMap<SWCPoint, ArrayList<Double>>();
	private DirectedWeightedGraph graph;

	public PersistenceAnalyzer(final Tree tree) {
		this.tree = tree;
	}

	private void compute() {
		// Generate persistence points via sub-level set filtration
		SNTUtils.log("Retrieving graph...");
		// Use simplified graph since geodesic distances are preserved as edge weights
		// This provides a significant performance boost over the full Graph.
		graph = tree.getGraph().getSimplifiedGraph(); // IllegalArgumentException if i.e, tree has multiple roots
		if (graph == null)
			return;

		Set<SWCPoint> openSet = new HashSet<SWCPoint>();
		Set<SWCPoint> closedSet = new HashSet<SWCPoint>();

		List<SWCPoint> tips = graph.getTips();
		List<SWCPoint> branchPoints = graph.getBPs();
		SWCPoint root = graph.getRoot();
		root.v = 0.0;
		for (SWCPoint t : tips) {
			t.v = distanceToRoot(graph, t);
			// only keep track of open end points
			openSet.add(t);
		}
		for (SWCPoint bp : branchPoints) {
			bp.v = distanceToRoot(graph, bp);
		}
		while (!openSet.isEmpty()) {
			HashMap<SWCPoint, ArrayList<SWCPoint>> currentBranchPoints = new HashMap<SWCPoint, ArrayList<SWCPoint>>();
			ArrayList<SWCPoint> toRemove = new ArrayList<SWCPoint>();
			for (SWCPoint node : openSet) {
				SWCPoint p = getOpenPredecessor(graph, closedSet, node);
				if (p.parent == -1) {
					// We've arrived at the last open point,
					// which is the point with maximum f(x).
					ArrayList<Double> point = new ArrayList<Double>();
					point.add(0.0); // component born at root
					point.add(node.v); // died at end point farthest from root
					persistenceMap.put(node, point);
					toRemove.add(node);
					closedSet.add(node);
					continue;
				}
				if (!currentBranchPoints.containsKey(p)) {
					currentBranchPoints.put(p, new ArrayList<SWCPoint>());
				}
				currentBranchPoints.get(p).add(node);
			}
			for (SWCPoint bp : currentBranchPoints.keySet()) {
				if (currentBranchPoints.get(bp).size() == graph.outDegreeOf(bp)) {
					ArrayList<SWCPoint> group = currentBranchPoints.get(bp);
					SWCPoint survivor = group.stream().max(Comparator.comparingDouble(n -> n.v)).get();
					group.remove(survivor);
					for (SWCPoint dead : group) {
						toRemove.add(dead);
						closedSet.add(dead);
						ArrayList<Double> point = new ArrayList<Double>();
						point.add(bp.v); // component born at branch point
						point.add(dead.v); // died at end point
						persistenceMap.put(dead, point);
					}
					closedSet.add(bp);
				}
			}
			openSet.removeAll(toRemove);
		}
	}

	public ArrayList<ArrayList<Double>> getPersistenceDiagram() {
		if (persistenceMap == null || persistenceMap.isEmpty())
			compute();
		ArrayList<ArrayList<Double>> diagram = new ArrayList<ArrayList<Double>>();
		for (ArrayList<Double> point : persistenceMap.values()) {
			diagram.add(point);
		}
		Collections.sort(diagram, new Comparator<ArrayList<Double>>() {
			@Override
			public int compare(ArrayList<Double> o1, ArrayList<Double> o2) {
				return o1.get(0).compareTo(o2.get(0));
			}
		});
		return diagram;
	}

	private double distanceToRoot(final DirectedWeightedGraph graph, SWCPoint node) {
		double distance = 0;
		if (node.parent == -1)
			return 0.0;
		while (node.parent != -1) {
			SWCPoint p = Graphs.predecessorListOf(graph, node).get(0);
			// only one incoming edge, unless something has gone horribly wrong...
			SWCWeightedEdge incomingEdge = graph.getEdge(p, node);
			double weight = incomingEdge.getWeight();
			// un-comment the following to test against Allen biccn tools implementation test cases
			// They use Euclidean distance between simplified graph nodes instead of geodesic
			// weight = p.distanceTo(node)
			distance += weight;
			node = p;
		}
		return distance;
	}

	private SWCPoint getOpenPredecessor(final DirectedWeightedGraph graph, Set<SWCPoint> closedSet, SWCPoint node) {
		while (node.parent != -1) {
			node = Graphs.predecessorListOf(graph, node).get(0);
			if (!closedSet.contains(node) && graph.outDegreeOf(node) > 1) {
				return node;
			}
		}
		return node;
	}

	private double gaussian(double mean, double sigma, double sample) {
		return (double) 1.0 / Math.exp(((mean - sample) * (mean - sample)) / (2 * (sigma * sigma)));
	}

}
