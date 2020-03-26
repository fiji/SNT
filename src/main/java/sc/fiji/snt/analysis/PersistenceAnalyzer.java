package sc.fiji.snt.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graphs;

import net.imagej.ImageJ;
import sc.fiji.snt.SNTService;
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

	private final HashMap<String, ArrayList<ArrayList<Double>>> persistenceDiagramMap = new HashMap<String, ArrayList<ArrayList<Double>>>();
	private final HashMap<String, ArrayList<ArrayList<SWCPoint>>> persistenceNodesMap = new HashMap<String, ArrayList<ArrayList<SWCPoint>>>();

	private DirectedWeightedGraph graph;

	public PersistenceAnalyzer(final Tree tree) {
		this.tree = tree;
	}

	/**
	 * Generate Persistence Diagram using the algorithm described by Kanari, L.,
	 * Dłotko, P., Scolamiero, M. et al. A Topological Representation of Branching
	 * Neuronal Morphologies. Neuroinform 16, 3–13 (2018).
	 */
	private void compute(String func) {

		ArrayList<ArrayList<SWCPoint>> persistenceNodes = new ArrayList<ArrayList<SWCPoint>>();
		ArrayList<ArrayList<Double>> persistenceDiagram = new ArrayList<ArrayList<Double>>();

		SNTUtils.log("Retrieving graph...");
		// Use simplified graph since geodesic distances are preserved as edge weights
		// This provides a significant performance boost over the full Graph.
		graph = tree.getGraph().getSimplifiedGraph(); // IllegalArgumentException if i.e, tree has multiple roots
		if (graph == null)
			return;

		Set<SWCPoint> openSet = new HashSet<SWCPoint>();
		List<SWCPoint> tips = graph.getTips();
		SWCPoint maxTip = tips.get(0);
		for (SWCPoint t : tips) {
			openSet.add(t);
			t.v = descriptorFunc(graph, t, func);
			if (t.v > maxTip.v) {
				maxTip = t;
			}
		}
		SWCPoint root = graph.getRoot();
		
		while (!openSet.contains(root)) {
			List<SWCPoint> toRemove = new ArrayList<SWCPoint>();
			List<SWCPoint> toAdd = new ArrayList<SWCPoint>();
			for (SWCPoint l : openSet){
				if (toRemove.contains(l)) continue;
				SWCPoint p = Graphs.predecessorListOf(graph, l).get(0);
				List<SWCPoint> children = Graphs.successorListOf(graph, p);
				if (openSet.containsAll(children)) {
					SWCPoint survivor = children.stream().max(Comparator.comparingDouble(n -> n.v)).get();
					toAdd.add(p);
					for (SWCPoint child : children) {
						toRemove.add(child);
						if (!child.equals(survivor)) {
							persistenceDiagram.add(new ArrayList<Double>(Arrays.asList(descriptorFunc(graph, p, func), child.v)));
							persistenceNodes.add(new ArrayList<>(Arrays.asList(p, child)));
						}
					}
					p.v = survivor.v;
				}
			}
			openSet.addAll(toAdd);
			openSet.removeAll(toRemove);
		}


		persistenceDiagram.add(new ArrayList<Double>(Arrays.asList(descriptorFunc(graph, root, func), root.v)));
		persistenceNodes.add(new ArrayList<SWCPoint>(Arrays.asList(root, maxTip)));

		persistenceDiagramMap.put(func, persistenceDiagram);
		persistenceNodesMap.put(func, persistenceNodes);
	}

	public ArrayList<ArrayList<Double>> getPersistenceDiagram(String descriptor) {
		if (persistenceDiagramMap.get(descriptor) == null || persistenceDiagramMap.get(descriptor).isEmpty()) {
			compute(descriptor);
		}
		Collections.sort(persistenceDiagramMap.get(descriptor), new Comparator<ArrayList<Double>>() {
			@Override
			public int compare(ArrayList<Double> o1, ArrayList<Double> o2) {
				return o1.get(0).compareTo(o2.get(0));
			}
		});
		return persistenceDiagramMap.get(descriptor);
	}

	public ArrayList<ArrayList<SWCPoint>> getPersistenceDiagramNodes(String descriptor) {
		if (persistenceNodesMap.get(descriptor) == null || persistenceNodesMap.get(descriptor).isEmpty())
			compute(descriptor);
		Collections.sort(persistenceNodesMap.get(descriptor), new Comparator<ArrayList<SWCPoint>>() {
			@Override
			public int compare(ArrayList<SWCPoint> o1, ArrayList<SWCPoint> o2) {
				return Double.compare(o1.get(0).v, o2.get(0).v);
			}
		});
		return persistenceNodesMap.get(descriptor);
	}

	private double descriptorFunc(final DirectedWeightedGraph graph, SWCPoint node, String func) {
		if (func.equalsIgnoreCase("geodesic"))
			return geodesicDistanceToRoot(graph, node);
		else if (func.equalsIgnoreCase("radial"))
			return radialDistanceToRoot(graph, node);
		else
			throw new IllegalArgumentException("Unrecognized Descriptor");
	}

	private double geodesicDistanceToRoot(final DirectedWeightedGraph graph, SWCPoint node) {
		double distance = 0;
		if (node.parent == -1)
			return 0.0;
		while (node.parent != -1) {
			SWCPoint p = Graphs.predecessorListOf(graph, node).get(0);
			SWCWeightedEdge incomingEdge = graph.getEdge(p, node);
			double weight = incomingEdge.getWeight();
			distance += weight;
			node = p;
		}
		return distance;
	}

	private double radialDistanceToRoot(final DirectedWeightedGraph graph, SWCPoint node) {
		return graph.getRoot().distanceTo(node);
	}

	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
		final ImageJ ij = new ImageJ();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final Tree tree = sntService.demoTree();
		final PersistenceAnalyzer analyzer = new PersistenceAnalyzer(tree);
		ArrayList<ArrayList<Double>> diagram = analyzer.getPersistenceDiagram("radial");
		for (ArrayList<Double> point : diagram) {
			System.out.println(point);
		}
		System.out.println(diagram.size());
	}
}
