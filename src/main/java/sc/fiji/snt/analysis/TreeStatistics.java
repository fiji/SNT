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

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
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
 * Computes summary and descriptive statistics from univariate properties of a
 * {@link Tree}. For analysis of groups of Trees use {@link #MultiTreeStatistics}.
 *
 * @author Tiago Ferreira
 */
public class TreeStatistics extends TreeAnalyzer {

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
	 * Computes the {@link SummaryStatistics} for the specified measurement.
	 *
	 * @param measurement the measurement ({@link #N_NODES}, {@link #NODE_RADIUS},
	 *          etc.)
	 * @return the SummaryStatistics object.
	 */
	public SummaryStatistics getSummaryStats(final String measurement) {
		final SummaryStatistics sStats = new SummaryStatistics();
		assembleStats(new StatisticsInstance(sStats), normalizedMeasurement(
			measurement));
		return sStats;
	}

	/**
	 * Computes the {@link DescriptiveStatistics} for the specified measurement.
	 *
	 * @param measurement the measurement ({@link #N_NODES}, {@link #NODE_RADIUS},
	 *          etc.)
	 * @return the DescriptiveStatistics object.
	 */
	public DescriptiveStatistics getDescriptiveStats(final String measurement) {
		final DescriptiveStatistics dStats = new DescriptiveStatistics();
		final String normMeasurement = normalizedMeasurement(measurement);
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
	 * @param measurement the measurement ({@link #N_NODES}, {@link #NODE_RADIUS},
	 *          etc.)
	 * @return the frame holding the histogram
	 */
	public ChartFrame getHistogram(final String measurement) {
		getDescriptiveStats(measurement);
		final double[] values = lastDstats.dStats.getValues();
		final long n = lastDstats.dStats.getN();
		final double q1 = lastDstats.dStats.getPercentile(25);
		final double q3 = lastDstats.dStats.getPercentile(75);
		final double min = lastDstats.dStats.getMin();
		final double max = lastDstats.dStats.getMax();
		int nBins;
		if (n == 0) {
			nBins = 1;
		} else {
			final double binWidth = 2 * (q3 - q1) / Math.cbrt(n); // Freedman-Diaconis rule
			if (binWidth == 0) {
				nBins = (int) Math.round(Math.sqrt(n));
			} else {
				nBins = (int) Math.ceil((max - min) / binWidth);
			}
			nBins = Math.max(1, nBins);
		}
		final HistogramDataset dataset = new HistogramDataset();
		dataset.setType(HistogramType.RELATIVE_FREQUENCY);
		dataset.addSeries(measurement, values, nBins);
		final JFreeChart chart = ChartFactory.createHistogram(null, measurement,
			"Rel. Frequency", dataset);

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
		sb.append("Q1: ").append(SNTUtils.formatDouble(q1, nDecimals));
		sb.append("  Median: ").append(SNTUtils.formatDouble(lastDstats.dStats
			.getPercentile(50), nDecimals));
		sb.append("  Q3: ").append(SNTUtils.formatDouble(q3, nDecimals));
		sb.append("  IQR: ").append(SNTUtils.formatDouble(q3 - q1, nDecimals));
		sb.append("  Bins: ").append(nBins);
		sb.append("\nN: ").append(n);
		sb.append("  Min: ").append(SNTUtils.formatDouble(min, nDecimals));
		sb.append("  Max: ").append(SNTUtils.formatDouble(max, nDecimals));
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

	private String normalizedMeasurement(final String measurement) {
		if (measurement == null) throw new IllegalArgumentException(
			"Parameter cannot be null");
		// This is just so that we can use capitalized strings in the GUI and lower
		// case strings in scripts
		return new StringBuilder().append(measurement.substring(0, 1).toUpperCase())
			.append(measurement.substring(1)).toString();
	}

	protected void assembleStats(final StatisticsInstance stat,
		final String measurement)
	{
		switch (measurement) {
			case TreeAnalyzer.LENGTH:
				for (final Path p : tree.list())
					stat.addValue(p.getLength());
				break;
			case TreeAnalyzer.PRIMARY_LENGTH:
				for (final Path p : getPrimaryBranches())
					stat.addValue(p.getLength());
				break;
			case TreeAnalyzer.TERMINAL_LENGTH:
				for (final Path p : getTerminalBranches())
					stat.addValue(p.getLength());
				break;
			case TreeAnalyzer.N_NODES:
				for (final Path p : tree.list())
					stat.addValue(p.size());
				break;
			case TreeAnalyzer.INTER_NODE_DISTANCE:
				for (final Path p : tree.list()) {
					if (p.size() < 2) continue;
					for (int i = 1; i < p.size(); i += 1) {
						stat.addValue(p.getNode(i).distanceTo(p.getNode(i -
							1)));
					}
				}
				break;
			case TreeAnalyzer.INTER_NODE_DISTANCE_SQUARED:
				for (final Path p : tree.list()) {
					if (p.size() < 2) continue;
					for (int i = 1; i < p.size(); i += 1) {
						stat.addValue(p.getNode(i).distanceSquaredTo(p
							.getNode(i - 1)));
					}
				}
				break;
			case TreeAnalyzer.NODE_RADIUS:
				for (final Path p : tree.list()) {
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNodeRadius(i));
					}
				}
				break;
			case TreeAnalyzer.MEAN_RADIUS:
				for (final Path p : tree.list()) {
					stat.addValue(p.getMeanRadius());
				}
				break;
			case TreeAnalyzer.PATH_ORDER:
				for (final Path p : tree.list()) {
					stat.addValue(p.getOrder());
				}
				break;
			case TreeAnalyzer.N_BRANCH_POINTS:
				for (final Path p : tree.list()) {
					stat.addValue(p.findJunctions().size());
				}
				break;
			case TreeAnalyzer.X_COORDINATES:
				for (final Path p : tree.list()) {
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNode(i).x);
					}
				}
				break;
			case TreeAnalyzer.Y_COORDINATES:
				for (final Path p : tree.list()) {
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNode(i).y);
					}
				}
			case TreeAnalyzer.VALUES:
				for (final Path p : tree.list()) {
					if (!p.hasNodeValues()) continue;
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNodeValue(i));
					}
				}
				if (stat.getN() == 0)
					throw new IllegalArgumentException("Tree has no values assigned");
				break;
			case TreeAnalyzer.Z_COORDINATES:
				for (final Path p : tree.list()) {
					for (int i = 0; i < p.size(); i++) {
						stat.addValue(p.getNode(i).z);
					}
				}
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

	/* IDE debug method */
	public static void main(final String[] args) {
		final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
		final TreeStatistics treeStats = new TreeStatistics(tree);
		treeStats.getHistogram(TreeAnalyzer.PATH_ORDER).setVisible(true);
		treeStats.restrictToOrder(1);
		treeStats.getHistogram(TreeAnalyzer.PATH_ORDER).setVisible(true);
		treeStats.resetRestrictions();
		treeStats.getHistogram(TreeAnalyzer.PATH_ORDER).setVisible(true);
	}
}
