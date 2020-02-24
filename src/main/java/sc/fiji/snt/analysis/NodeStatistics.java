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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.JFreeChart;

import sc.fiji.snt.Path;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.AnalysisUtils.HistogramDatasetPlus;
import sc.fiji.snt.util.PointInImage;

/**
 * Computes summary and descriptive statistics from a Collection of nodes.
 * 
 * @author Tiago Ferreira
 */
public class NodeStatistics {

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


	final Collection<? extends PointInImage> points;
	private boolean branchesAssigned;
	private Tree branchAssignmentTree;
	private String currentMetric;
	private DescriptiveStatistics currentStats;


	/**
	 * Performs statistics on a collection of nodes.
	 *
	 * @param points      the points to be analyzed
	 */
	public NodeStatistics(final Collection<? extends PointInImage> points) {
		this(points, null);
	}


	/**
	 * Performs statistics on a collection of nodes.
	 *
	 * @param points the points to be analyzed
	 * @param tree   the Tree associated with {@code points}
	 */
	public NodeStatistics(final Collection<? extends PointInImage> points, final Tree tree) {
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
		final JFreeChart chart = AnalysisUtils.getHistogram(currentMetric, currentStats, datasetPlus);
		final SNTChart frame = new SNTChart("Hist. " + currentMetric, chart);
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
		final NodeStatistics treeStats = new NodeStatistics(tree.getNodes());
		final SNTChart hist = treeStats.getHistogram("x-coord");
		hist.show();
	}
}
