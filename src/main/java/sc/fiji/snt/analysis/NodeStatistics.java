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

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import sc.fiji.snt.Path;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.AnalysisUtils.HistogramDatasetPlus;
import sc.fiji.snt.annotation.BrainAnnotation;
import sc.fiji.snt.io.MouseLightLoader;
import sc.fiji.snt.util.PointInImage;

/**
 * Computes summary and descriptive statistics from a Collection of nodes.
 * 
 * @author Tiago Ferreira
 * @param <T>
 */
public class NodeStatistics <T extends PointInImage> {

	/** Flag for {@value #BRANCH_LENGTH} analysis. */
	public static final String BRANCH_LENGTH = TreeStatistics.BRANCH_LENGTH;

	/** Flag for {@value #BRANCH_ORDER} statistics. */
	public static final String BRANCH_ORDER = "Branch order";

	/** Flag for {@value #RADIUS} statistics. */
	public static final String RADIUS = TreeStatistics.NODE_RADIUS;

	/** Flag for {@value #X_COORDINATES} statistics. */
	public static final String X_COORDINATES = TreeStatistics.X_COORDINATES;

	/** Flag for {@value #Y_COORDINATES} statistics. */
	public static final String Y_COORDINATES = TreeStatistics.Y_COORDINATES;

	/** Flag for {@value #Z_COORDINATES} statistics. */
	public static final String Z_COORDINATES = TreeStatistics.Z_COORDINATES;

	/** Flag for statistics on {@value #VALUES} */
	public static final String VALUES = TreeStatistics.VALUES;

	private static final String[] ALL_FLAGS = { //
			BRANCH_LENGTH, //
			RADIUS, //
			BRANCH_ORDER, //
			VALUES, //
			X_COORDINATES, //
			Y_COORDINATES, //
			Z_COORDINATES, //
	};

	final Collection<T> points;
	private boolean branchesAssigned;
	private Tree branchAssignmentTree;
	private String currentMetric;
	private DescriptiveStatistics currentStats;
	private String label;



	/**
	 * Performs statistics on a collection of nodes.
	 *
	 * @param points      the points to be analyzed
	 */
	public NodeStatistics(final Collection<T> points) {
		this(points, null);
	}


	/**
	 * Performs statistics on a collection of nodes.
	 *
	 * @param points the points to be analyzed
	 * @param tree   the Tree associated with {@code points}
	 */
	public NodeStatistics(final Collection<T>points, final Tree tree) {
		this.points = points;
		branchAssignmentTree = tree;
	}

	/**
	 * Gets the list of supported metrics.
	 *
	 * @return the list of supported metrics
	 */
	public static List<String> getMetrics() {
		return Arrays.stream(ALL_FLAGS).collect(Collectors.toList());
	}

	/**
	 * Sets a descriptive label to this statistic analysis to be used in histograms,
	 * etc.
	 *
	 * @param label the descriptive label
	 */
	public void setLabel(String label) {
		this.label = label;
	}

	private String getLabel() {
		return (label == null) ? "" : label;
	}

	/**
	 * Computes the {@link DescriptiveStatistics} for the specified measurement.
	 *
	 * @param metric the measurement ({@link #X_COORDINATES}, {@link #Y_COORDINATES},
	 *          etc.)
	 * @return the DescriptiveStatistics object.
	 */
	public DescriptiveStatistics getDescriptiveStatistics(final String metric) throws UnknownMetricException {
		final String normMetric = getNormalizedMeasurement(metric);
		if (normMetric.equals(currentMetric) && currentStats != null) return currentStats;
		currentMetric = normMetric;
		assembleStats();
		return currentStats;
	}

	private void assembleStats() throws UnknownMetricException {
		currentStats = new DescriptiveStatistics();
		switch (currentMetric) {
		case BRANCH_LENGTH:
			assessIfBranchesHaveBeenAssigned();
			points.forEach(p->currentStats.addValue(p.getPath().getLength()));
			break;
		case BRANCH_ORDER:
			assessIfBranchesHaveBeenAssigned();
			points.forEach(p->currentStats.addValue(p.getPath().getOrder()));
			break;
		case VALUES:
			points.forEach(p->currentStats.addValue(p.v));
			break;
		case X_COORDINATES:
			points.forEach(p->currentStats.addValue(p.getX()));
			break;
		case Y_COORDINATES:
			points.forEach(p->currentStats.addValue(p.getY()));
			break;
		case Z_COORDINATES:
			points.forEach(p->currentStats.addValue(p.getZ()));
			break;
		default:
			throw new UnknownMetricException("Unrecognized metric: " + currentMetric);
		}
	}

	/**
	 * Gets the relative frequencies histogram for a univariate measurement. The
	 * number of bins is determined using the Freedman-Diaconis rule.
	 *
	 * @param metric the measurement ({@link #X_COORDINATES}, {@link #RADIUS},
	 *          etc.)
	 * @return the frame holding the histogram
	 */
	public SNTChart getHistogram(final String metric) {
		getDescriptiveStatistics(metric);
		final HistogramDatasetPlus datasetPlus = new HistogramDatasetPlus(currentStats, true);
		final JFreeChart chart = AnalysisUtils.createHistogram(currentMetric, currentStats, datasetPlus);
		final SNTChart frame = new SNTChart("Hist. " + currentMetric, chart);
		return frame;
	}

	/**
	 * Gets the list of nodes associated with the specified compartment (neuropil
	 * label).
	 *
	 * @param compartment the query compartment (null not allowed). All of its
	 *                    children will be considered
	 * @return the list of filtered nodes
	 */
	public List<T> get(final BrainAnnotation compartment) {
		return get(compartment, true);
	}

	/**
	 * Gets the list of nodes associated with the specified compartment (neuropil
	 * label).
	 *
	 * @param compartment the query compartment (null not allowed)
	 * @param includeChildren whether children of {@code compartment} should be included
	 * @return the list of filtered nodes
	 */
	public List<T> get(final BrainAnnotation compartment, final boolean includeChildren) {
		final List<T> list = new ArrayList<T>();
		for (final T node : points) {
			final BrainAnnotation annotation = node.getAnnotation();
			if (annotation != null) {
				boolean include = annotation.equals(compartment);
				if (includeChildren)
					include = include || compartment.isParentOf(annotation);
				if (include) {
					list.add(node);
				}
			}
		}
		return list;
	}

	public Map<BrainAnnotation, List<T>> getAnnotatedNodes() {
		return getAnnotatedNodes(Integer.MAX_VALUE);
	}

	public Map<BrainAnnotation, List<T>> getAnnotatedNodes(final int level) {
		final HashMap<BrainAnnotation, List<T>> map  = new HashMap<>();
		for (final T p : points) {
			BrainAnnotation mappingAnnotation = null;
			final BrainAnnotation pAnnotation = p.getAnnotation();
			if (pAnnotation != null) {
				final int depth = pAnnotation.getOntologyDepth();
				if (depth > level) {
					mappingAnnotation = pAnnotation.getAncestor(level-depth);
				} else {
					mappingAnnotation = pAnnotation;
				}
			}
			List<T> currentList = map.get(mappingAnnotation);
			if (currentList == null) {
				currentList = new ArrayList<T>();
				map.put(mappingAnnotation, currentList);
			}
			currentList.add(p);
		}
	
		return map;
	}

	public Map<BrainAnnotation, Integer> getAnnotatedFrequencies() {
		return getAnnotatedFrequencies(Integer.MAX_VALUE);
	}

	public Map<BrainAnnotation, Integer> getAnnotatedFrequencies(final int level) {
		final HashMap<BrainAnnotation, Integer> map  = new HashMap<>();
		points.forEach(p -> {
			BrainAnnotation mappingAnnotation = null;
			final BrainAnnotation pAnnotation = p.getAnnotation();
			if (pAnnotation != null) {
				final int depth = pAnnotation.getOntologyDepth();
				if (depth > level) {
					mappingAnnotation = pAnnotation.getAncestor(level-depth);
				} else {
					mappingAnnotation = pAnnotation;
				}
			}
			Integer currentCount = map.get(mappingAnnotation);
			if (currentCount == null) currentCount = 0;
			map.put(mappingAnnotation, ++currentCount);
		});
		return map;
	}

	public SNTChart getAnnotatedHistogram() {
		return getAnnotatedHistogram(Integer.MAX_VALUE);
	}

	public SNTChart getAnnotatedHistogram(final int depth) {
		final Map<BrainAnnotation, Integer> map = getAnnotatedFrequencies(depth);
		final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		final String seriesLabel = (depth == Integer.MAX_VALUE) ? "no filtering" : "depth \u2264" + depth;
		map.entrySet().stream().sorted((e1, e2) -> -e1.getValue().compareTo(e2.getValue())).forEach(entry -> {
			if (entry.getKey() != null)
					dataset.addValue(entry.getValue(), seriesLabel, entry.getKey().acronym());
		});
		int nAreas = map.size();
		if (map.get(null) != null) {
			dataset.addValue(map.get(null), seriesLabel,"Other" );
			nAreas--;
		}
		final JFreeChart chart = AnalysisUtils.createCategoryPlot( //
				"Brain areas (N=" + nAreas + ", "+ seriesLabel +")", // domain axis title
				"Frequency", // range axis title
				dataset, seriesLabel);
		final SNTChart frame = new SNTChart(getLabel() + " Annotated Node Distribution", chart, new Dimension(400, 600));
		return frame;
	}

	protected static String getStandardizedMetric(final String guess) {
		if (Arrays.stream(ALL_FLAGS).anyMatch(guess::equals)) {
			return guess;
		}
		final String normGuess = guess.toLowerCase();
		if (normGuess.matches(".*\\bx\\b.*")) {
			return X_COORDINATES;
		}
		if (normGuess.matches(".*\\by\\b.*")) {
			return Y_COORDINATES;
		}
		if (normGuess.matches(".*\\bz\\b.*")) {
			return Z_COORDINATES;
		}
		if (normGuess.indexOf("rad") != -1 ) {
			return RADIUS;
		}
		if (normGuess.indexOf("val") != -1 || normGuess.indexOf("int") > -1) {
			return VALUES;
		}
		if (normGuess.indexOf("len") != -1) {
			return BRANCH_LENGTH;
		}
		if (normGuess.indexOf("ord") != -1 || normGuess.indexOf("strahler") != -1 || normGuess.indexOf("horton") != -1
				|| normGuess.indexOf("h-s") != -1) {
			return BRANCH_ORDER;
		}
		return "unknown";
	}

	/**
	 * Associates the nodes being analyzed to the branches of the specified tree
	 *
	 * @param tree the association tree
	 */
	public void assignBranches(final Tree tree) {
		final StrahlerAnalyzer sa = new StrahlerAnalyzer(tree);
		final Map<Integer, List<Path>> mappedBranches = sa.getBranches();
		points.forEach(point->point.onPath = null);
		for (final PointInImage node : points) {
			branchLoop:
			for (final Map.Entry<Integer, List<Path>> entry : mappedBranches.entrySet()) {
				for (final Path branch : entry.getValue()) {
					for (int i = 0; i < branch.size(); i++) {
						if (branch.getNode(i).isSameLocation(node)) {
							branch.setOrder(entry.getKey());
							node.onPath = branch;
							break branchLoop;
						}
					}
				}
			}
		}
		branchesAssigned = true;
		branchAssignmentTree = tree;
	}

	protected boolean isBranchesAssigned() {
		return branchesAssigned;
	}

	protected static String getNormalizedMeasurement(final String measurement) {
		final String normMeasurement = getStandardizedMetric(measurement);
		if (!measurement.equals(normMeasurement)) {
			SNTUtils.log("\"" + normMeasurement + "\" assumed");
			if ("unknown".equals(normMeasurement)) {
				throw new UnknownMetricException("Unrecognizable measurement! "
						+ "Maybe you meant one of the following?: " + Arrays.toString(ALL_FLAGS));
			}
		}
		return normMeasurement;
	}

	protected void assessIfBranchesHaveBeenAssigned() {
		if (!branchesAssigned && branchAssignmentTree == null) {
			throw new IllegalArgumentException("assignBranches() has not been called");
		} else if (!branchesAssigned && branchAssignmentTree != null) {
			assignBranches(branchAssignmentTree);
		}
	}


	/* IDE debug method */
	public static void main(final String[] args) {
		final Tree tree = new SNTService().demoTrees().get(0);
		final NodeStatistics<?> treeStats = new NodeStatistics<>(tree.getNodes());
		final SNTChart hist = treeStats.getHistogram("x-coord");
		hist.annotate("Free text");
		hist.show();
		final MouseLightLoader loader = new MouseLightLoader("AA0001");
		final TreeAnalyzer analyzer = new TreeAnalyzer(loader.getTree("axon"));
		final NodeStatistics<?> nStats = new NodeStatistics<>(analyzer.getTips());
		final SNTChart plot = nStats.getAnnotatedHistogram();
		plot.annotateCategory("CA1", "Not showing");
		plot.annotateCategory("CP", "highlighted category");
		plot.annotate("Free Text");
		plot.show();

	}
}