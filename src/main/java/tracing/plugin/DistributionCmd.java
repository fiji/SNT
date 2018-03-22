/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2018 Fiji developers.
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

package tracing.plugin;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
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
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ImageJ;
import tracing.Path;
import tracing.SNT;
import tracing.gui.GuiUtils;
import tracing.measure.TreeAnalyzer;
import tracing.util.PointInImage;
import tracing.util.SWCColor;

@Plugin(type = Command.class, visible = false, label = "Distribution of Morphometric Measurements")
public class DistributionCmd implements Command {

	@Parameter(required = true, label = "Measurement", choices = { TreeAnalyzer.BRANCH_ORDER,
			TreeAnalyzer.INTER_NODE_DISTANCE, TreeAnalyzer.N_BRANCH_POINTS, TreeAnalyzer.N_NODES,
			TreeAnalyzer.NODE_RADIUS, TreeAnalyzer.MEAN_RADIUS })
	private String measurementChoice;

	@Parameter(required = true)
	private HashSet<Path> paths;

	@Parameter(required = false, visibility = ItemVisibility.INVISIBLE, persist = false)
	private String frameTitle = "SNT: Histogram";

	@Override
	public void run() {

		final List<Double> data = new ArrayList<Double>();
		for (final Path p : paths) {
			if (TreeAnalyzer.NODE_RADIUS.equals(measurementChoice)) {
				for (int i = 0; i< p.size(); i++) data.add(p.getNodeRadius(i));
			}
			else if (TreeAnalyzer.INTER_NODE_DISTANCE.equals(measurementChoice)) {
				for (int i = 0; i < p.size() - 1; i++) {
					final PointInImage pim1 = p.getPointInImage(i);
					final PointInImage pim2 = p.getPointInImage(i + 1);
					data.add(pim1.distanceTo(pim2));
				}
			}
			else if (TreeAnalyzer.MEAN_RADIUS.equals(measurementChoice))
				data.add(p.getMeanRadius());
			else if (TreeAnalyzer.BRANCH_ORDER.equals(measurementChoice))
				data.add((double) p.getOrder());
			else if (TreeAnalyzer.N_NODES.equals(measurementChoice))
				data.add((double)p.size());
			else if (TreeAnalyzer.N_BRANCH_POINTS.contains(measurementChoice))
				data.add((double)p.findJoinedPoints().size());
			else // TreeAnalyzer.LENGTH
				data.add(p.getRealLength());

		}
		final JFreeChart getHistogram = getHistogram(data, measurementChoice);
		final ChartFrame frame = new ChartFrame((frameTitle == null) ? "SNT: Histogram" : frameTitle, getHistogram);
		frame.setPreferredSize(new Dimension(400, 400));
		frame.pack();
		frame.setVisible(true);
	}

	public JFreeChart getHistogram(final List<Double> data, final String title) {

		final double[] values = data.stream().mapToDouble(d -> d).toArray();
		final DescriptiveStatistics da = new DescriptiveStatistics(values);
		final long n = da.getN();
		final double q1 = da.getPercentile(25);
		final double q3 = da.getPercentile(75);
		final double min = da.getMin();
		final double max = da.getMax();
		final double binWidth = 2 * (q3 - q1) / Math.cbrt(n); // Freedman-Diaconis rule
		final int nBins = (int) Math.ceil((max - min) / binWidth);

		final HistogramDataset dataset = new HistogramDataset();
		dataset.setType(HistogramType.RELATIVE_FREQUENCY);
		dataset.addSeries(title, values, Math.max(1, nBins));
		final JFreeChart chart = ChartFactory.createHistogram(null, title, "Rel. Frequency", dataset);

		// Customize plot
		final Color bColor = SWCColor.alphaColor(Color.WHITE, 100);
		final XYPlot plot = chart.getXYPlot();
		plot.setBackgroundPaint(bColor);
		final XYBarRenderer bar_renderer = (XYBarRenderer) plot.getRenderer();
		bar_renderer.setBarPainter(new StandardXYBarPainter());
		bar_renderer.setDrawBarOutline(true);
		bar_renderer.setSeriesOutlinePaint(0, Color.DARK_GRAY);
		bar_renderer.setSeriesPaint(0, SWCColor.alphaColor(Color.LIGHT_GRAY, 50));
		bar_renderer.setShadowVisible(false);
		chart.setBackgroundPaint(bColor);
		chart.setAntiAlias(true);
		chart.setTextAntiAlias(true);

		// Append descriptive label
		chart.removeLegend();
		final StringBuilder sb = new StringBuilder();
		sb.append("Q1: ").append(SNT.formatDouble(q1, 2));
		sb.append("  Median: ").append(SNT.formatDouble(da.getPercentile(50), 2));
		sb.append("  Q3: ").append(SNT.formatDouble(q3, 2));
		sb.append("  IQR: ").append(SNT.formatDouble(q3-q1, 2));
		sb.append("\nN: ").append(n);
		sb.append("  Min: ").append(SNT.formatDouble(min, 2));
		sb.append("  Max: ").append(SNT.formatDouble(max, 2));
		sb.append("  Mean\u00B1").append("SD: ").append(SNT.formatDouble(da.getMean(), 2)).append("\u00B1")
				.append(SNT.formatDouble(da.getStandardDeviation(), 2));
		final TextTitle label = new TextTitle(sb.toString());
		label.setFont(label.getFont().deriveFont(Font.PLAIN));
		label.setPosition(RectangleEdge.BOTTOM);
		chart.addSubtitle(label);
		return chart;
	}

	public static List<Path> randomPaths() {
		final List<Path> data = new ArrayList<Path>();
		for (int i = 0; i < 200; i++) {
			final Path p = new Path(1, 1, 1, "unit");
			final double v = new Random().nextGaussian();
			p.addPointDouble(0, 0, 0);
			p.addPointDouble(v, v, v);
			data.add(p);
		}
		return data;
	}

	/**
	 * IDE debug method
	 * 
	 * @throws IOException
	 **/
	public static void main(final String[] args) throws IOException {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input = new HashMap<>();
		input.put("paths", new HashSet<Path>(randomPaths()));
		ij.command().run(DistributionCmd.class, true, input);
	}

}
