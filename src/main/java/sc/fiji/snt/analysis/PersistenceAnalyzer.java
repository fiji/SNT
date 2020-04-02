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
 * Performs persistent homology analysis on a {@link Tree}. For an overview see
 * Kanari, L. et al. A Topological Representation of Branching Neuronal
 * Morphologies. Neuroinform 16, 3–13 (2018).
 *
 * @author Cameron Arshadi
 * @author Tiago Ferreira
 */
public class PersistenceAnalyzer {

	private static final int FUNC_UNKNOWN = -1;
	private static final int FUNC_GEODESIC = 0;
	private static final int FUNC_RADIAL = 1;
	private static final int FUNC_PATH_ORDER = 2;
	private static final int FUNC_CENTRIFUGAL= 3;
	private static final int FUNC_X = 4;
	private static final int FUNC_Y = 5;
	private static final int FUNC_Z = 6;

	private final Tree tree;

	private final HashMap<String, ArrayList<ArrayList<Double>>> persistenceDiagramMap = new HashMap<String, ArrayList<ArrayList<Double>>>();
	private final HashMap<String, ArrayList<ArrayList<SWCPoint>>> persistenceNodesMap = new HashMap<String, ArrayList<ArrayList<SWCPoint>>>();

	private DirectedWeightedGraph graph;
	private boolean nodeValuesAssigned;

	public PersistenceAnalyzer(final Tree tree) {
		this.tree = tree;
	}

	/**
	 * Generate Persistence Diagram using the base algorithm described by Kanari, L.,
	 * Dłotko, P., Scolamiero, M. et al. A Topological Representation of Branching
	 * Neuronal Morphologies. Neuroinform 16, 3–13 (2018).
	 */
	private void compute(final String func) throws IllegalArgumentException {

		final int function = getNormFunction(func);
		if (function == FUNC_UNKNOWN) {
			throw new IllegalArgumentException("Unrecognizable descriptor \"" + func + "\". "
					+ "Maybe you meant one of the following?: \"" + String.join(", ", getDescriptors() + "\""));
		}

		final ArrayList<ArrayList<SWCPoint>> persistenceNodes = new ArrayList<ArrayList<SWCPoint>>();
		final ArrayList<ArrayList<Double>> persistenceDiagram = new ArrayList<ArrayList<Double>>();

		SNTUtils.log("Retrieving graph...");
		// Use simplified graph since geodesic distances are preserved as edge weights
		// This provides a significant performance boost over the full Graph.
		graph = tree.getGraph().getSimplifiedGraph(); // IllegalArgumentException if i.e, tree has multiple roots
		final HashMap<SWCPoint, Double> descriptorMap = new HashMap<SWCPoint, Double>();
		for (final SWCPoint node : graph.vertexSet()) {
			descriptorMap.put(node, descriptorFunc(graph, node, function));
		}
		final Set<SWCPoint> openSet = new HashSet<SWCPoint>();
		final List<SWCPoint> tips = graph.getTips();
		SWCPoint maxTip = tips.get(0);
		for (final SWCPoint t : tips) {
			openSet.add(t);
			t.v = descriptorMap.get(t);
			if (t.v > maxTip.v) {
				maxTip = t;
			}
		}
		final SWCPoint root = graph.getRoot();

		while (!openSet.contains(root)) {
			final List<SWCPoint> toRemove = new ArrayList<SWCPoint>();
			final List<SWCPoint> toAdd = new ArrayList<SWCPoint>();
			for (final SWCPoint l : openSet){
				if (toRemove.contains(l)) continue;
				final SWCPoint p = Graphs.predecessorListOf(graph, l).get(0);
				final List<SWCPoint> children = Graphs.successorListOf(graph, p);
				if (openSet.containsAll(children)) {
					final SWCPoint survivor = children.stream().max(Comparator.comparingDouble(n -> n.v)).get();
					toAdd.add(p);
					for (final SWCPoint child : children) {
						toRemove.add(child);
						if (!child.equals(survivor)) {
							persistenceDiagram.add(new ArrayList<Double>(Arrays.asList(descriptorMap.get(p), child.v)));
							persistenceNodes.add(new ArrayList<>(Arrays.asList(p, child)));
						}
					}
					p.v = survivor.v;
				}
			}
			openSet.addAll(toAdd);
			openSet.removeAll(toRemove);
		}

		persistenceDiagram.add(new ArrayList<Double>(Arrays.asList(descriptorMap.get(root), root.v)));
		persistenceNodes.add(new ArrayList<SWCPoint>(Arrays.asList(root, maxTip)));

		persistenceDiagramMap.put(func, persistenceDiagram);
		persistenceNodesMap.put(func, persistenceNodes);
		nodeValuesAssigned = false; // reset field so that it can be recycled by a different func
	}

	/**
	 * Gets the persistence diagram.
	 *
	 * @param descriptor A descriptor for the filter function as per
	 *                   {@link #getDescriptors()} (case insensitive), such as
	 *                   {@code radial}, {@code geodesic}, {@code centrifugal}
	 *                   (reverse Strahler), etc.
	 * @return the persistence diagram
	 * @throws UnknownMetricException   If {@code descriptor} is not valid
	 * @throws IllegalArgumentException If the {@code tree}'s graph could not be
	 *                                  obtained
	 */
	public ArrayList<ArrayList<Double>> getPersistenceDiagram(final String descriptor) throws UnknownMetricException, IllegalArgumentException {
		if (persistenceDiagramMap.get(descriptor) == null || persistenceDiagramMap.get(descriptor).isEmpty()) {
			compute(descriptor);
		}
		Collections.sort(persistenceDiagramMap.get(descriptor), new Comparator<ArrayList<Double>>() {
			@Override
			public int compare(final ArrayList<Double> o1, final ArrayList<Double> o2) {
				return o1.get(0).compareTo(o2.get(0));
			}
		});
		return persistenceDiagramMap.get(descriptor);
	}

	/**
	 * Gets the 'bar codes' for the specified filter function.
	 *
	 * @param descriptor A descriptor for the filter function as per
	 *                   {@link #getDescriptors()} (case insensitive), such as
	 *                   {@code radial}, {@code geodesic}, {@code centrifugal}
	 *                   (reverse Strahler), etc.
	 * @return the bar codes
	 * @throws UnknownMetricException   If {@code descriptor} is not valid
	 * @throws IllegalArgumentException If the {@code tree}'s graph could not be
	 *                                  obtained
	 */
	public ArrayList<Double> getBarCodes(final String descriptor) throws UnknownMetricException, IllegalArgumentException {
		final ArrayList<ArrayList<Double>> diag = getPersistenceDiagram(descriptor);
		final ArrayList<Double> barcodes = new ArrayList<>(diag.size());
		diag.forEach(point -> {
			barcodes.add(point.get(1) - point.get(0));
		});
		return barcodes;
	}

	/**
	 * Gets the persistence diagram nodes.
	 *
	 * @param descriptor A descriptor for the filter function as per
	 *                   {@link #getDescriptors()} (case insensitive), such as
	 *                   {@code radial}, {@code geodesic}, {@code centrifugal}
	 *                   (reverse Strahler), etc.
	 * @return the persistence diagram nodes.
	 * @throws UnknownMetricException   If {@code descriptor} is not valid.
	 * @throws IllegalArgumentException If the {@code tree}'s graph could not be
	 *                                  obtained
	 */
	public ArrayList<ArrayList<SWCPoint>> getPersistenceDiagramNodes(final String descriptor) {
		if (persistenceNodesMap.get(descriptor) == null || persistenceNodesMap.get(descriptor).isEmpty())
			compute(descriptor);
		Collections.sort(persistenceNodesMap.get(descriptor), new Comparator<ArrayList<SWCPoint>>() {
			@Override
			public int compare(final ArrayList<SWCPoint> o1, final ArrayList<SWCPoint> o2) {
				return Double.compare(o1.get(0).v, o2.get(0).v);
			}
		});
		return persistenceNodesMap.get(descriptor);
	}

	/**
	 * Gets a list of supported descriptor functions.
	 * 
	 * @return the list of available descriptors.
	 */
	public static List<String> getDescriptors() {
		return new ArrayList<String>(Arrays.asList("geodesic", "radial", "centrifugal (reverse Strahler)", "path order",
				"x", "y", "z (depth)"));
	}

	private double descriptorFunc(final DirectedWeightedGraph graph, final SWCPoint node, final int func) throws UnknownMetricException {
		switch (func) {
		case FUNC_CENTRIFUGAL:
			if (!nodeValuesAssigned) {
				StrahlerAnalyzer.classify(graph, true);
				nodeValuesAssigned = true;
			}
			return node.v;
		case FUNC_GEODESIC:
			return geodesicDistanceToRoot(graph, node);
		case FUNC_PATH_ORDER:
			return node.getPath().getOrder();
		case FUNC_RADIAL:
			return radialDistanceToRoot(graph, node);
		case FUNC_X:
			return node.getX();
		case FUNC_Y:
			return node.getY();
		case FUNC_Z:
			return node.getZ();
		default:
			throw new UnknownMetricException("Unrecognized Descriptor");
		}
	}

	private int getNormFunction(final String func) {
		if (func == null || func.trim().isEmpty()) return FUNC_UNKNOWN;
		final String normFunc = func.toLowerCase();
		if (normFunc.indexOf("geodesic") != -1) {
			return FUNC_GEODESIC;
		}
		if (normFunc.indexOf("centrifugal") != -1
				|| (normFunc.indexOf("reverse") != -1 && normFunc.indexOf("strahler") != -1)) {
			return FUNC_CENTRIFUGAL;
		}
		if (normFunc.indexOf("path") != -1 && normFunc.indexOf("order") != -1) {
			return FUNC_PATH_ORDER;
		}
		if (normFunc.indexOf("depth") == -1 || normFunc.matches(".*\\bz\\b.*")) {
			return FUNC_Z;
		}
		if (normFunc.matches(".*\\bx\\b.*")) {
			return FUNC_X;
		}
		if (normFunc.matches(".*\\by\\b.*")) {
			return FUNC_Y;
		}
		return FUNC_UNKNOWN;
	}

	private double geodesicDistanceToRoot(final DirectedWeightedGraph graph, SWCPoint node) {
		double distance = 0;
		if (node.parent == -1)
			return 0.0;
		while (node.parent != -1) {
			final SWCPoint p = Graphs.predecessorListOf(graph, node).get(0);
			final SWCWeightedEdge incomingEdge = graph.getEdge(p, node);
			final double weight = incomingEdge.getWeight();
			distance += weight;
			node = p;
		}
		return distance;
	}

	private double radialDistanceToRoot(final DirectedWeightedGraph graph, final SWCPoint node) {
		return graph.getRoot().distanceTo(node);
	}

	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
		final ImageJ ij = new ImageJ();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final Tree tree = sntService.demoTree();
		final PersistenceAnalyzer analyzer = new PersistenceAnalyzer(tree);
		final ArrayList<ArrayList<Double>> diagram = analyzer.getPersistenceDiagram("centrifugal");
		for (final ArrayList<Double> point : diagram) {
			System.out.println(point);
		}
		System.out.println(diagram.size());
	}
}
