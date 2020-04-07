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
 * 
http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt.analysis;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;

import sc.fiji.snt.Path;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SNTColor;

/**
 * Static utilities for sc.fiji.analysis.
 *
 * @author Tiago Ferreira
 */
class AnalysisUtils {

	private AnalysisUtils() {
	}

	/* Converts nodes to single point paths: useful to allow Tree-based classes to parse isolated nodes */
	static Tree convertPointsToSinglePointPaths(final Collection<? extends PointInImage> points) {
		final Tree tree = new Tree();
		for (final PointInImage point : points) {
			final Path p = new Path(1, 1, 1, "NA");
			p.addNode(point);
			point.onPath = p;
			tree.add(p);
		}
		return tree;
	}

	static Tree createTreeFromPointAssociatedPaths(final Collection<? extends PointInImage> points) {
		final Tree tree = new Tree();
		for (final PointInImage point : points) {
			if (point.onPath != null)
				tree.add(point.onPath);
		}
		return tree;
	}

	static <T extends StatisticalSummary> String getSummaryDescription(final T stats, final HistogramDatasetPlus datasetPlus) {
		final StringBuilder sb = new StringBuilder();
		final double mean = stats.getMean();
		final int nDecimals = (mean < 0.51) ? 3 : 2;
		sb.append("Q1: ").append(SNTUtils.formatDouble(datasetPlus.q1, nDecimals));
		if (stats instanceof DescriptiveStatistics)
			sb.append("  Median: ").append(SNTUtils.formatDouble(//
					((DescriptiveStatistics) stats).getPercentile(50), nDecimals));
		sb.append("  Q3: ").append(SNTUtils.formatDouble(datasetPlus.q3, nDecimals));
		sb.append("  IQR: ").append(SNTUtils.formatDouble(datasetPlus.q3 - datasetPlus.q1, nDecimals));
		sb.append("  Bins: ").append(datasetPlus.nBins);
		sb.append("\nN: ").append(datasetPlus.n);
		sb.append("  Min: ").append(SNTUtils.formatDouble(datasetPlus.min, nDecimals));
		sb.append("  Max: ").append(SNTUtils.formatDouble(datasetPlus.max, nDecimals));
		sb.append("  Mean\u00B1").append("SD: ").append(SNTUtils.formatDouble(
			mean, nDecimals)).append("\u00B1").append(SNTUtils.formatDouble(
				stats.getStandardDeviation(), nDecimals));
		return sb.toString();
	}

	static JFreeChart createHistogram(final String xAxisTitle, final DescriptiveStatistics stats, final HistogramDatasetPlus datasetPlus) {

		final JFreeChart chart = ChartFactory.createHistogram(null, xAxisTitle,
			"Rel. Frequency", datasetPlus.getDataset(xAxisTitle));

		// Customize plot
		final Color bColor = null; //Color.WHITE; make graph transparent so that it can be exported without background
		final XYPlot plot = chart.getXYPlot();
		plot.setBackgroundPaint(bColor);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinesVisible(false);
		plot.setAxisOffset(new RectangleInsets(0,0, 0, 0));
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
		final String desc = getSummaryDescription(stats, datasetPlus);
		final TextTitle label = new TextTitle(desc);
		label.setFont(label.getFont().deriveFont(Font.PLAIN));
		label.setPosition(RectangleEdge.BOTTOM);
		chart.addSubtitle(label);
		return chart;
	}

	static JFreeChart createCategoryPlot(final String domainTitle,
			final String rangeTitle, final DefaultCategoryDataset dataset, final String seriesLabel) {
		final JFreeChart chart = ChartFactory.createBarChart(null, domainTitle, rangeTitle, dataset,
				PlotOrientation.HORIZONTAL, // orientation
				false, // include legend
				true, // tooltips?
				false // URLs?
		);
		// Customize plot
		final CategoryPlot plot = chart.getCategoryPlot();
		plot.setBackgroundPaint(null); //make graph transparent so that it can be exported without background
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinesVisible(true);
		plot.setRangeGridlinePaint(SNTColor.alphaColor(Color.LIGHT_GRAY, 50));
		plot.getDomainAxis().setCategoryMargin(0);
		plot.getDomainAxis().setLowerMargin(0.01);
		plot.getDomainAxis().setUpperMargin(0.01);
		plot.setAxisOffset(new RectangleInsets(0,0, 0, 0));
		final BarRenderer barRender = ((BarRenderer) plot.getRenderer());
		barRender.setBarPainter(new StandardBarPainter());
		barRender.setDrawBarOutline(true);
		barRender.setSeriesPaint(0, SNTColor.alphaColor(Color.LIGHT_GRAY, 50));
		barRender.setSeriesOutlinePaint(0, Color.DARK_GRAY);
		barRender.setShadowVisible(false);
		return chart;
	}

	static class HistogramDatasetPlus {
		final List<Double> values;
		int nBins;
		long n;
		double q1, q3, min, max;
		private DescriptiveStatistics dStats;

		HistogramDatasetPlus() {
			values = new ArrayList<Double>();
		}

		HistogramDatasetPlus(final DescriptiveStatistics stats, final boolean retrieveValues) {
			this();
			dStats = stats;
			if (retrieveValues) {
				for (final double v : stats.getValues()) {
					values.add(v);
				}
			}
		}

		double[] valuesAsArray() {
			final double[] doubles = new double[values.size()];
			for (int i = 0; i < doubles.length; i++) {
				doubles[i] = values.get(i);
			}
			return doubles;
		}

		void compute() {
			if (dStats == null) {
				dStats = new DescriptiveStatistics();
				values.forEach(v -> dStats.addValue(v));
			}
			n = dStats.getN();
			q1 = dStats.getPercentile(25);
			q3 = dStats.getPercentile(75);
			min = dStats.getMin();
			max = dStats.getMax();
			if (n == 0 || max == min || Double.isNaN(max) || Double.isNaN(min)) {
				nBins = 1;
			}
			{
				final double binWidth = 2 * (q3 - q1) / Math.cbrt(n); // Freedman-Diaconis rule
				if (binWidth == 0) {
					nBins = (int) Math.round(Math.sqrt(n));
				} else {
					nBins = (int) Math.ceil((max - min) / binWidth);
				}
				nBins = Math.max(1, nBins);
			}
		}

		HistogramDataset getDataset(final String label) {
			compute();
			final HistogramDataset dataset = new HistogramDataset();
			dataset.setType(HistogramType.RELATIVE_FREQUENCY);
			dataset.addSeries(label, valuesAsArray(), nBins);
			return dataset;
		}

	}
}