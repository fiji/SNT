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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.text.WordUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;

import sc.fiji.snt.Path;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.util.SNTColor;

/**
 * Computes summary and descriptive statistics from univariate properties of
 * Paths and Nodes in a {@link Tree}. For analysis of groups of Trees use
 * {@link MultiTreeStatistics}.
 *
 * @author Tiago Ferreira
 */
public class TreeStatistics extends TreeAnalyzer {

	/** Flag for {@value #PATH_LENGTH} analysis. */
	public static final String PATH_LENGTH = "Path length";

	/** Flag for {@value #BRANCH_LENGTH} analysis. */
	public static final String BRANCH_LENGTH = "Branch length";

	/** Flag for {@value #TERMINAL_LENGTH} analysis. */
	public static final String TERMINAL_LENGTH = "Length of terminal branches";

	/** Flag for {@value #PRIMARY_LENGTH} analysis. */
	public static final String PRIMARY_LENGTH = "Length of primary branches";

	/** Flag for {@value #PATH_ORDER} statistics. */
	public static final String PATH_ORDER = "Path order";

	/** Flag for {@value #INTER_NODE_DISTANCE} statistics. */
	public static final String INTER_NODE_DISTANCE = "Inter-node distance";

	/** Flag for {@value #INTER_NODE_DISTANCE_SQUARED} statistics. */
	public static final String INTER_NODE_DISTANCE_SQUARED =
		"Inter-node distance (squared)";

	/** Flag for {@value #N_BRANCH_POINTS} statistics. */
	public static final String N_BRANCH_POINTS = "No. of branch points";

	/** Flag for {@value #N_BRANCH_POINTS} statistics. */
	public static final String N_NODES = "No. of nodes";

	/** Flag for {@value #NODE_RADIUS} statistics. */
	public static final String NODE_RADIUS = "Node radius";

	/** Flag for {@value #MEAN_RADIUS} statistics. */
	public static final String MEAN_RADIUS = "Path mean radius";

	/** Flag for {@value #X_COORDINATES} statistics. */
	public static final String X_COORDINATES = "X coordinates";

	/** Flag for {@value #Y_COORDINATES} statistics. */
	public static final String Y_COORDINATES = "Y coordinates";

	/** Flag for {@value #Z_COORDINATES} statistics. */
	public static final String Z_COORDINATES = "Z coordinates";

	/** Flag for {@value #CONTRACTION} statistics. */
	public static final String CONTRACTION = "Contraction";

	/**
	 * Flag for analysis of {@value #VALUES}, an optional numeric property that
	 * can be assigned to Path nodes (e.g., voxel intensities, assigned via
	 * {@link PathProfiler}. Note that an {@link IllegalArgumentException} is
	 * triggered if no values have been assigned to the tree being analyzed.
	 * 
	 * @see Path#hasNodeValues()
	 * @see PathProfiler#assignValues()
	 */
	public static final String VALUES = "Node intensity values";

	private static final String[] ALL_FLAGS = { //
			BRANCH_LENGTH, //
			TERMINAL_LENGTH, //
			PRIMARY_LENGTH, //
			PATH_LENGTH, //
			PATH_ORDER, //
			INTER_NODE_DISTANCE,//
			INTER_NODE_DISTANCE_SQUARED,//
			N_BRANCH_POINTS, //
			N_NODES, //
			NODE_RADIUS, //
			MEAN_RADIUS, //
			X_COORDINATES, //
			Y_COORDINATES, //
			Z_COORDINATES, //
			CONTRACTION, //
			VALUES };

	private LastDstats lastDstats;

	/**
	 * Instantiates a new instance from a collection of Paths
	 *
	 * @param tree the collection of paths to be analyzed
	 */
	public TreeStatistics(final Tree tree) {
		super(tree);
	}

	/**
	 * Gets the list of <i>all</i> supported metrics.
	 *
	 * @return the list of available metrics
	 */
	public static List<String> getAllMetrics() {
		return Arrays.stream(ALL_FLAGS).collect(Collectors.toList());
	}

	/**
	 * Gets the list of most commonly used metrics.
	 *
	 * @return the list of commonly used metrics
	 */
	public static List<String> getMetrics() {
		return getAllMetrics().stream().filter(metric -> {
			return !metric.toLowerCase().contains("path");
		}).collect(Collectors.toList());
	}

	/**
	 * Computes the {@link SummaryStatistics} for the specified measurement.
	 *
	 * @param metric the measurement ({@link #N_NODES}, {@link #NODE_RADIUS},
	 *          etc.)
	 * @return the SummaryStatistics object.
	 */
	public SummaryStatistics getSummaryStats(final String metric) {
		final SummaryStatistics sStats = new SummaryStatistics();
		assembleStats(new StatisticsInstance(sStats), getNormalizedMeasurement(metric));
		return sStats;
	}

	/**
	 * Computes the {@link DescriptiveStatistics} for the specified measurement.
	 *
	 * @param metric the measurement ({@link #N_NODES}, {@link #NODE_RADIUS},
	 *          etc.)
	 * @return the DescriptiveStatistics object.
	 */
	public DescriptiveStatistics getDescriptiveStats(final String metric) {
		final DescriptiveStatistics dStats = new DescriptiveStatistics();
		final String normMeasurement = getNormalizedMeasurement(metric);
		if (!lastDstatsCanBeRecycled(normMeasurement)) {
			assembleStats(new StatisticsInstance(dStats), normMeasurement);
			lastDstats = new LastDstats(normMeasurement, dStats);
		}
		return lastDstats.dStats;
	}

	/**
	 * Gets the relative frequencies histogram for a univariate measurement. The
	 * number of bins is determined using the Freedman-Diaconis rule.
	 *
	 * @param metric the measurement ({@link #N_NODES}, {@link #NODE_RADIUS},
	 *          etc.)
	 * @return the frame holding the histogram
	 */
	public ChartFrame getHistogram(final String metric) {

		final String normMeasurement = getNormalizedMeasurement(metric);
		final HistogramDatasetPlus datasetPlus = new HistogramDatasetPlus(normMeasurement);
		final JFreeChart chart = ChartFactory.createHistogram(null, normMeasurement,
			"Rel. Frequency", datasetPlus.getDataset());

		// Customize plot
		final Color bColor = null; //Color.WHITE; make graph transparent so that it can be exported without background
		final XYPlot plot = chart.getXYPlot();
		plot.setBackgroundPaint(bColor);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinesVisible(false);
		final XYBarRenderer bar_renderer = (XYBarRenderer) plot.getRenderer();
		bar_renderer.setBarPainter(new StandardXYBarPainter());
		bar_renderer.setDrawBarOutline(true);
		bar_renderer.setSeriesOutlinePaint(0, Color.DARK_GRAY);
		bar_renderer.setSeriesPaint(0, SNTColor.alphaColor(Color.LIGHT_GRAY, 50));
		bar_renderer.setShadowVisible(false);
		chart.setBackgroundPaint(bColor);
		chart.setAntiAlias(true);
		chart.setTextAntiAlias(true);

		// Append descriptive label
		chart.removeLegend();
		final StringBuilder sb = new StringBuilder();
		final double mean = lastDstats.dStats.getMean();
		final int nDecimals = (mean < 0.51) ? 3 : 2;
		sb.append("Q1: ").append(SNTUtils.formatDouble(datasetPlus.q1, nDecimals));
		sb.append("  Median: ").append(SNTUtils.formatDouble(lastDstats.dStats
			.getPercentile(50), nDecimals));
		sb.append("  Q3: ").append(SNTUtils.formatDouble(datasetPlus.q3, nDecimals));
		sb.append("  IQR: ").append(SNTUtils.formatDouble(datasetPlus.q3 - datasetPlus.q1, nDecimals));
		sb.append("  Bins: ").append(datasetPlus.nBins);
		sb.append("\nN: ").append(datasetPlus.n);
		sb.append("  Min: ").append(SNTUtils.formatDouble(datasetPlus.min, nDecimals));
		sb.append("  Max: ").append(SNTUtils.formatDouble(datasetPlus.max, nDecimals));
		sb.append("  Mean\u00B1").append("SD: ").append(SNTUtils.formatDouble(
			mean, nDecimals)).append("\u00B1").append(SNTUtils.formatDouble(
				lastDstats.dStats.getStandardDeviation(), nDecimals));
		final TextTitle label = new TextTitle(sb.toString());
		label.setFont(label.getFont().deriveFont(Font.PLAIN));
		label.setPosition(RectangleEdge.BOTTOM);
		chart.addSubtitle(label);
		final ChartFrame frame = new ChartFrame("Hist. " + tree.getLabel(), chart);
		frame.getChartPanel().setBackground(bColor);
		frame.setPreferredSize(new Dimension(400, 400));
		frame.setBackground(Color.WHITE); // provided contrast to otherwise transparent background
		frame.pack();
		return frame;
	}

	protected String tryReallyHardToGuessMetric(final String guess) {
		final String normGuess = guess.toLowerCase();
		if (normGuess.indexOf("contrac") != -1) {
			return CONTRACTION;
		}
		if (normGuess.indexOf("length") != -1 || normGuess.indexOf("cable") != -1) {
			if (normGuess.indexOf("term") != -1) {
				return TERMINAL_LENGTH;
			}
			else if (normGuess.indexOf("prim") != -1) {
				return PRIMARY_LENGTH;
			}
			else if (normGuess.indexOf("path") != -1) {
				return PATH_LENGTH;
			}
			else {
				return BRANCH_LENGTH;
			}
		}
		if (normGuess.indexOf("path") != -1 && normGuess.indexOf("order") != -1) {
			return PATH_ORDER;
		}
		if (normGuess.indexOf("bp") != -1 || normGuess.indexOf("branch points") != -1 || normGuess.indexOf("junctions") != -1) {
			return N_BRANCH_POINTS;
		}
		if (normGuess.indexOf("nodes") != -1) {
			return N_NODES;
		}
		if (normGuess.indexOf("node") != -1 && (normGuess.indexOf("dis") != -1 || normGuess.indexOf("dx") != -1)) {
			if (normGuess.indexOf("sq") != -1) {
				return INTER_NODE_DISTANCE_SQUARED;
			}
			else {
				return INTER_NODE_DISTANCE;
			}
		}
		if (normGuess.indexOf("radi") != -1 ) {
			if (normGuess.indexOf("mean") != -1 || normGuess.indexOf("avg") != -1 || normGuess.indexOf("average") != -1) {
				return MEAN_RADIUS;
			}
			else {
				return NODE_RADIUS;
			}
		}
		if (normGuess.indexOf("values") != -1 || normGuess.indexOf("intensit") > -1) {
			return VALUES;
		}
		if (normGuess.matches(".*\\bx\\b.*")) {
			return X_COORDINATES;
		}
		if (normGuess.matches(".*\\by\\b.*")) {
			return Y_COORDINATES;
		}
		if (normGuess.matches(".*\\bz\\b.*")) {
			return Z_COORDINATES;
		}
		return "unknown";
	}

	protected String getNormalizedMeasurement(final String measurement) {
		if (Arrays.stream(ALL_FLAGS).anyMatch(measurement::equalsIgnoreCase)) {
			// This is just so that we can use capitalized strings in the GUI
			// and lower case strings in scripts
			return WordUtils.capitalize(measurement, new char[]{});
		}
		final String normMeasurement = tryReallyHardToGuessMetric(measurement);
		if (!measurement.equals(normMeasurement)) {
			SNTUtils.log("\"" + normMeasurement + "\" assumed");
			if ("unknonwn".equals(normMeasurement)) {
				throw new IllegalArgumentException("Unrecognizable measurement! "
						+ "Maybe you meant one of the following?: " + Arrays.toString(ALL_FLAGS));
			}
		}
		return normMeasurement;
	}

	protected void assembleStats(final StatisticsInstance stat,
		final String measurement)
	{
		switch (getNormalizedMeasurement(measurement)) {
			case CONTRACTION:
			try {
				final TreeAnalyzer analyzer = new TreeAnalyzer(tree);
				for (final Path p : analyzer.getBranches())
					stat.addValue(p.getContraction());
			} catch (final IllegalArgumentException ignored) {
				SNTUtils.log("Error: " + ignored.getMessage());
				stat.addValue(Double.NaN);
			}
			break;
			case BRANCH_LENGTH:
			try {
				final TreeAnalyzer analyzer = new TreeAnalyzer(tree);
				for (final Path p : analyzer.getBranches())
					stat.addValue(p.getLength());
			} catch (final IllegalArgumentException ignored) {
				SNTUtils.log("Error: " + ignored.getMessage());
				stat.addValue(Double.NaN);
			}
			break;
			case PATH_LENGTH:
				for (final Path p : tree.list())
					stat.addValue(p.getLength());
				break;
			case PRIMARY_LENGTH:
				for (final Path p : getPrimaryBranches())
					stat.addValue(p.getLength());
				break;
			case TERMINAL_LENGTH:
				for (final Path p : getTerminalBranches())
					stat.addValue(p.getLength());
				break;
			case N_NODES:
				for (final Path p : tree.list())
					stat.addValue(p.size());
				break;
			case INTER_NODE_DISTANCE:
				for (final Path p : tree.list()) {
					if (p.size() < 2) continue;
					for (int i = 1; i < p.size(); i += 1) {
						stat.addValue(p.getNode(i).distanceTo(p.getNode(i -
							1)));
					}
				}
				break;
			case INTER_NODE_DISTANCE_SQUARED:
				for (final Path p : tree.list()) {
					if (p.size() < 2) continue;
					for (int i = 1; i < p.size(); i += 1) {
						stat.addValue(p.getNode(i).distanceSquaredTo(p
							.getNode(i - 1)));
					}
				}
				break;
			case NODE_RADIUS:
				for (final Path p : tree.list()) {
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNodeRadius(i));
					}
				}
				break;
			case MEAN_RADIUS:
				for (final Path p : tree.list()) {
					stat.addValue(p.getMeanRadius());
				}
				break;
			case PATH_ORDER:
				for (final Path p : tree.list()) {
					stat.addValue(p.getOrder());
				}
				break;
			case N_BRANCH_POINTS:
				for (final Path p : tree.list()) {
					stat.addValue(p.findJunctions().size());
				}
				break;
			case X_COORDINATES:
				for (final Path p : tree.list()) {
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNode(i).x);
					}
				}
				break;
			case Y_COORDINATES:
				for (final Path p : tree.list()) {
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNode(i).y);
					}
				}
			case Z_COORDINATES:
				for (final Path p : tree.list()) {
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNode(i).z);
					}
				}
				break;
			case VALUES:
				for (final Path p : tree.list()) {
					if (!p.hasNodeValues()) continue;
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNodeValue(i));
					}
				}
				if (stat.getN() == 0)
					throw new IllegalArgumentException("Tree has no values assigned");
				break;
			default:
				throw new IllegalArgumentException("Unrecognized parameter " +
					measurement);
		}
	}

	private boolean lastDstatsCanBeRecycled(final String normMeasurement) {
		return (lastDstats != null && tree.size() == lastDstats.size &&
			normMeasurement.equals(lastDstats.measurement));
	}

	private class LastDstats {

		private final String measurement;
		private final DescriptiveStatistics dStats;
		private final int size;

		private LastDstats(final String measurement,
			final DescriptiveStatistics dStats)
		{
			this.measurement = measurement;
			this.dStats = dStats;
			size = tree.size();
		}
	}

	class StatisticsInstance {

		private SummaryStatistics sStatistics;
		private DescriptiveStatistics dStatistics;

		StatisticsInstance(final SummaryStatistics sStatistics) {
			this.sStatistics = sStatistics;
		}

		StatisticsInstance(final DescriptiveStatistics dStatistics) {
			this.dStatistics = dStatistics;
		}

		void addValue(final double value) {
			if (sStatistics != null) sStatistics.addValue(value);
			else dStatistics.addValue(value);
		}

		long getN() {
			return (sStatistics != null) ? sStatistics.getN() : dStatistics.getN();
		}

	}

	class HistogramDatasetPlus {
		String measurement;
		double[] values;
		int nBins;
		long n;
		double q1, q3, min, max;

		HistogramDatasetPlus(final String measurement) {
			this.measurement = measurement;
			getDescriptiveStats(measurement);
			values = lastDstats.dStats.getValues();
		}

		void compute() {
			n = lastDstats.dStats.getN();
			q1 = lastDstats.dStats.getPercentile(25);
			q3 = lastDstats.dStats.getPercentile(75);
			min = lastDstats.dStats.getMin();
			max = lastDstats.dStats.getMax();
			if (n == 0) {
				nBins = 1;
			}
			if (n <= 10) {
				nBins = (int) n;
			} else {
				final double binWidth = 2 * (q3 - q1) / Math.cbrt(n); // Freedman-Diaconis rule
				if (binWidth == 0) {
					nBins = (int) Math.round(Math.sqrt(n));
				} else {
					nBins = (int) Math.ceil((max - min) / binWidth);
				}
				nBins = Math.max(1, nBins);
			}
		}

		HistogramDataset getDataset() {
			compute();
			final HistogramDataset dataset = new HistogramDataset();
			dataset.setType(HistogramType.RELATIVE_FREQUENCY);
			dataset.addSeries(measurement, values, nBins);
			return dataset;
		}

	}

	/* IDE debug method */
	public static void main(final String[] args) {
		final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
		final TreeStatistics treeStats = new TreeStatistics(tree);
		treeStats.getHistogram("contraction").setVisible(true);
		treeStats.getHistogram(PATH_ORDER).setVisible(true);
		treeStats.restrictToOrder(1);
		treeStats.getHistogram(PATH_ORDER).setVisible(true);
		treeStats.resetRestrictions();
		treeStats.getHistogram(PATH_ORDER).setVisible(true);
	}
}
