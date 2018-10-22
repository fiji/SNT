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

package tracing.plot;

import java.awt.Color;
import java.awt.Dimension;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.imagej.ImageJ;
import net.imagej.plot.LineStyle;
import net.imagej.plot.MarkerStyle;
import net.imagej.plot.PlotService;
import net.imagej.plot.XYPlot;
import net.imagej.plot.XYSeries;
import net.imagej.ui.swing.viewer.plot.jfreechart.XYPlotConverter;
import net.imglib2.display.ColorTable;

import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.chart.ui.RectangleEdge;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;
import org.scijava.util.ColorRGB;

import tracing.Path;
import tracing.Tree;
import tracing.analysis.TreeColorizer;
import tracing.plugin.DistributionCmd;
import tracing.util.PointInImage;

/**
 * Class for rendering {@link Tree}s as 2D plots that can be exported as SVG, PNG or
 * PDF.
 *
 * @author Tiago Ferreira
 *
 */
public class TreePlot2D extends TreeColorizer {

	@Parameter
	private PlotService plotService;

	@Parameter
	private UIService uiService;

	private XYPlot plot;
	private String title;
	private JFreeChart chart;
	private ColorRGB defaultColor = new ColorRGB("black");

	/**
	 * Instantiates an empty tree plot.
	 *
	 * @param context
	 *            the SciJava application context providing the services required by
	 *            the class
	 */
	public TreePlot2D(final Context context) {
		super(context);
	}

	private void addPaths(final ArrayList<Path> paths) {
		this.paths = paths;
		plotPaths();
	}

	private void initPlot() {
		if (plot == null)
			plot = plotService.newXYPlot();
	}

	private void plotPaths() {
		if (paths == null || paths.isEmpty()) {
			throw new IllegalArgumentException("No paths to plot");
		}
		initPlot();
		for (final Path p : paths) {
			if (p.hasNodeColors()) {
				plotColoredNodePaths(p);
				continue;
			}
			final XYSeries series = plot.addXYSeries();
			series.setLabel(p.getName());
			final List<Double> xc = new ArrayList<>();
			final List<Double> yc = new ArrayList<>();
			for (int node = 0; node < p.size(); node++) {
				final PointInImage pim = p.getPointInImage(node);
				xc.add(pim.x);
				yc.add(pim.y);
			}
			series.setValues(xc, yc);
			series.setLegendVisible(false);
			final ColorRGB color = (p.getColor() == null) ? defaultColor
					: new ColorRGB(p.getColor().getRed(), p.getColor().getGreen(), p.getColor().getBlue());
			series.setStyle(plot.newSeriesStyle(color, LineStyle.SOLID, MarkerStyle.NONE));
		}
	}

	private void plotColoredNodePaths(final Path p) {
		for (int node = 0; node < p.size(); node++) {
			final XYSeries series = plot.addXYSeries();
			final List<Double> xc = new ArrayList<>();
			final List<Double> yc = new ArrayList<>();
			final PointInImage pim = p.getPointInImage(node);
			xc.add(pim.x);
			yc.add(pim.y);
			series.setValues(xc, yc);
			series.setLegendVisible(false);
			final Color c = p.getNodeColor(node);
			final ColorRGB cc = (c==null) ? defaultColor : new ColorRGB(c.getRed(), c.getGreen(), c.getBlue());
			series.setStyle(plot.newSeriesStyle(cc, LineStyle.NONE, MarkerStyle.FILLEDCIRCLE));
		}
	}

	/**
	 * Adds a color bar legend (LUT ramp).
	 *
	 * @param colorTable the color table
	 * @param min        the minimum value in the color table
	 * @param max        the maximum value in the color table
	 */
	public void addColorBarLegend(final ColorTable colorTable, final double min, final double max) {
		final double previousMin = this.min;
		final double previousMax = this.max;
		final ColorTable previousColorTable = this.colorTable;
		this.min = min;
		this.max = max;
		this.colorTable = colorTable;
		addColorBarLegend();
		this.min = previousMin;
		this.max = previousMax;
		this.colorTable = previousColorTable;
	}

	/**
	 * Adds a color bar legend (LUT ramp) to the plot. Does nothing if no
	 * measurement mapping occurred successfully.
	 *
	 * Note that when performing mapping to different measurements, the legend
	 * reflects only the last mapped measurement.
	 */
	public void addColorBarLegend() {

		if (min >= max || colorTable == null)
			return;

		final LookupPaintScale paintScale = new LookupPaintScale(min, max, Color.BLACK);
		for (int i = 0; i < colorTable.getLength(); i++) {
			final Color color = new Color(colorTable.get(ColorTable.RED, i), colorTable.get(ColorTable.GREEN, i),
					colorTable.get(ColorTable.BLUE, i));
			final double value = i * (max - min) / colorTable.getLength();
			paintScale.add(value, color);
		}

		final NumberAxis numberAxis = new NumberAxis();
		if (integerScale) {
			numberAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
			numberAxis.setNumberFormatOverride(new DecimalFormat("#"));
		}
		final PaintScaleLegend psl = new PaintScaleLegend(paintScale, numberAxis);
		psl.setPosition(RectangleEdge.RIGHT);
		psl.setAxisLocation(AxisLocation.TOP_OR_RIGHT);
		psl.setHorizontalAlignment(HorizontalAlignment.CENTER);
		psl.setMargin(50, 5, 50, 5);
		chart = getChart();
		chart.addSubtitle(psl);
	}

	/**
	 * Appends a tree to the plot using default options.
	 *
	 * @param tree
	 *            the Collection of paths to be plotted
	 */
	public void addTree(final Tree tree) {
		addPaths(tree.list());
	}

	/**
	 * Adds a list of trees while assigning each tree to a LUT index.
	 *
	 * @param trees
	 *            the list of trees to be plotted
	 * @param lut
	 *            the lookup table specifying the color mapping
	 *
	 */
	public void addTrees(final List<Tree> trees, final String lut) {
		colorizeTrees(trees, lut);
		for (final ListIterator<Tree> it = trees.listIterator(); it.hasNext();) {
			addTree(it.next());
		}
	}

	/**
	 * Appends a tree to the plot.
	 *
	 * @param tree
	 *            the Collection of paths to be plotted
	 * @param color
	 *            the color to render the Tree
	 */
	public void addTree(final Tree tree, final ColorRGB color) {
		final ColorRGB prevDefaultColor = defaultColor;
		setDefaultColor(color);
		addPaths(tree.list());
		setDefaultColor(prevDefaultColor);
	}

	/**
	 * Appends a tree to the plot rendered after the specified measurement.
	 *
	 * @param tree
	 *            the tree to be plotted
	 * @param measurement
	 *            the measurement ({@link BRANCH_ORDER} }{@link LENGTH}, etc.)
	 * @param colorTable
	 *            the color table specifying the color mapping
	 * @param min
	 *            the mapping lower bound (i.e., the highest measurement value for
	 *            the LUT scale)
	 * @param max
	 *            the mapping upper bound (i.e., the highest measurement value for
	 *            the LUT scale)
	 */
	public void addTree(final Tree tree, final String measurement, final ColorTable colorTable, final double min,
			final double max) {
		this.paths = tree.list();
		setMinMax(min, max);
		mapToProperty(measurement, colorTable);
		plotPaths();
	}

	/**
	 * Appends a tree to the plot rendered after the specified measurement. Mapping
	 * bounds are automatically determined.
	 *
	 * @param tree
	 *            the tree to be plotted
	 * @param measurement
	 *            the measurement ({@link BRANCH_ORDER} }{@link LENGTH}, etc.)
	 * @param lut
	 *            the lookup table specifying the color mapping
	 */
	public void addTree(final Tree tree, final String measurement, final String lut) {
		addTree(tree, measurement, getColorTable(lut), Double.NaN, Double.NaN);
	}

	/**
	 * Appends a tree to the plot rendered after the specified measurement.
	 *
	 * @param tree
	 *            the tree to be plotted
	 * @param measurement
	 *            the measurement ({@link BRANCH_ORDER} }{@link LENGTH}, etc.)
	 * @param lut
	 *            the lookup table specifying the color mapping
	 * @param min
	 *            the mapping lower bound (i.e., the highest measurement value for
	 *            the LUT scale)
	 * @param max
	 *            the mapping upper bound (i.e., the highest measurement value for
	 *            the LUT scale)
	 */
	public void addTree(final Tree tree, final String measurement, final String lut, final double min,
			final double max) {
		addTree(tree, measurement, getColorTable(lut), min, max);
	}

	/**
	 * Gets the current plot as a {@link JFreeChart} object
	 *
	 * @return the converted plot
	 */
	public JFreeChart getChart() {
		if (plot == null)
			plot = plotService.newXYPlot();
		final XYPlotConverter converter = new XYPlotConverter();
		return converter.convert(plot, JFreeChart.class);
		// chart.setAntiAlias(true);
		// chart.setTextAntiAlias(true);
	}

	/**
	 * Gets the current plot as a {@link XYPlot} object
	 *
	 * @param show
	 *            if true, plot is displayed
	 * @return the current plot
	 */
	public XYPlot getPlot(final boolean show) {
		initPlot();
		plot.yAxis().setAutoRange();
		plot.xAxis().setAutoRange();
		if (show) {
			if (chart == null) {
				uiService.show((title == null) ? "SNT Path Plot" : title, plot);
			} else {
				final ChartFrame frame = new ChartFrame(title, chart);
				frame.setPreferredSize(new Dimension(600, 450));
				frame.pack();
				frame.setVisible(true);
			}
		}
		return plot;
	}

	/**
	 * Sets the default (fallback) color for plotting paths.
	 *
	 * @param color
	 *            null not allowed
	 */
	public void setDefaultColor(final ColorRGB color) {
		if (color != null)
			defaultColor = color;
	}

	/**
	 * Sets the preferred size of the plot to a constant value.
	 *
	 * @param width
	 *            the preferred width
	 * @param height
	 *            the preferred height
	 */
	public void setPreferredSize(final int width, final int height) {
		initPlot();
		plot.setPreferredSize(width, height);
	}

	/**
	 * Sets the plot display title.
	 *
	 * @param title
	 *            the new title
	 */
	public void setTitle(final String title) {
		this.title = title;
	}

	/**
	 * Gets the plot display title.
	 *
	 * @return the current display title
	 */
	public String getTitle() {
		return title;
	}

	/** Displays the current plot on a dedicated frame */
	public void showPlot() {
		getPlot(true);
	}

	/* IDE debug method */
	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final TreePlot2D pplot = new TreePlot2D(ij.context());
		final List<Tree> trees = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			final Tree tree = new Tree(DistributionCmd.randomPaths());
			tree.rotate(Tree.Z_AXIS, i * 20);
			trees.add(tree);
		}
		pplot.addTrees(trees, "Ice.lut");
		pplot.addColorBarLegend();
		pplot.showPlot();
	}

}
