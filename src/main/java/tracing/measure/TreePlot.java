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

package tracing.measure;

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

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

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import net.imagej.plot.LineStyle;
import net.imagej.plot.MarkerStyle;
import net.imagej.plot.PlotService;
import net.imagej.plot.XYPlot;
import net.imagej.plot.XYSeries;
import net.imagej.ui.swing.viewer.plot.jfreechart.XYPlotConverter;
import net.imglib2.display.ColorTable;
import tracing.Path;
import tracing.Tree;
import tracing.plugin.DistributionCmd;
import tracing.util.PointInImage;

/**
 * Class for rendering trees as 2D plots that can be exported as SVG, PNG or PDF
 *
 * @author Tiago Ferreira
 *
 */
public class TreePlot {

	public static final String BRANCH_ORDER = PathAnalyzer.BRANCH_ORDER;
	public static final String LENGTH = PathAnalyzer.LENGTH;
	public static final String N_BRANCH_POINTS = PathAnalyzer.N_BRANCH_POINTS;
	public static final String N_NODES = PathAnalyzer.N_NODES;
	public static final String MEAN_RADIUS = PathAnalyzer.MEAN_RADIUS;
	private static final String INTERNAL_COUNTER = "";

	@Parameter
	private LUTService lutService;

	@Parameter
	private PlotService plotService;

	@Parameter
	private UIService uiService;

	private HashSet<Path> paths;
	private ColorTable colorTable;
	private XYPlot plot;
	private String title;
	private JFreeChart chart;
	private boolean integerScale;

	private double min = Double.MAX_VALUE;
	private double max = Double.MIN_VALUE;
	private Color defaultColor = Color.BLACK;
	private int internalCounter = 1;

	/**
	 * Instantiates a new empty plot.
	 *
	 * @param context
	 *            the SciJava application context providing all the services
	 *            required by the class
	 */
	public TreePlot(final Context context) {
		context.inject(this);
	}

	private void addPaths(final HashSet<Path> paths) {
		this.paths = paths;
		plotPaths();
	}

	private ColorTable getColorTable(final String lut) {
		final Map<String, URL> luts = lutService.findLUTs();
		for (final Map.Entry<String, URL> entry : luts.entrySet()) {
			if (entry.getKey().contains(lut)) {
				try {
					return lutService.loadLUT(entry.getValue());
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	private void mapToProperty(final String measurement, final ColorTable colorTable, final double min,
			final double max) {
		if (colorTable == null)
			return;
		if (min > max)
			throw new IllegalArgumentException("min > max.");
		this.colorTable = colorTable;
		setTitle("SNT: Plot [" + measurement + "]");
		final List<MappedPath> mappedPaths = new ArrayList<>();
		switch (measurement) {
		case BRANCH_ORDER:
			integerScale = true;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.getOrder()));
			break;
		case LENGTH:
			integerScale = false;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, p.getRealLength()));
			break;
		case MEAN_RADIUS:
			integerScale = false;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, p.getMeanRadius()));
			break;
		case N_NODES:
			integerScale = true;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.size()));
			break;
		case N_BRANCH_POINTS:
			integerScale = true;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.findJoinedPoints().size()));
			break;
		case INTERNAL_COUNTER:
			integerScale = true;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) internalCounter));
			break;
		default:
			throw new IllegalArgumentException("Unknown parameter");
		}
		if (!Double.isNaN(min))
			this.min = min;
		if (!Double.isNaN(max))
			this.max = max;
		for (final MappedPath mp : mappedPaths) {
			final int idx;
			if (mp.mappedValue <= min)
				idx = 0;
			else if (mp.mappedValue > max)
				idx = colorTable.getLength() - 1;
			else
				idx = (int) Math
						.round((colorTable.getLength() - 1) * (mp.mappedValue - this.min) / (this.max - this.min));
			final Color color = new Color(colorTable.get(ColorTable.RED, idx), colorTable.get(ColorTable.GREEN, idx),
					colorTable.get(ColorTable.BLUE, idx));
			mp.path.setColor(color);
		}
	}

	private void plotPaths() {
		if (paths == null || paths.isEmpty()) {
			throw new IllegalArgumentException("No paths to plot");
		}
		plot = getPlot(false);
		for (final Path p : paths) {
			final XYSeries series = plot.addXYSeries();
			series.setLabel(p.getName());
			final List<Double> xc = new ArrayList<Double>();
			final List<Double> yc = new ArrayList<Double>();
			for (int node = 0; node < p.size(); node++) {
				final PointInImage pim = p.getPointInImage(node);
				xc.add(pim.x);
				yc.add(-pim.y);
			}
			series.setValues(xc, yc);
			series.setLegendVisible(false);
			final Color color = (p.getColor() == null) ? defaultColor : p.getColor();
			series.setStyle(plot.newSeriesStyle(new ColorRGB(color.getRed(), color.getGreen(), color.getBlue()),
					LineStyle.SOLID, MarkerStyle.NONE));
		}
		plot.yAxis().setAutoRange();
		plot.xAxis().setAutoRange();
	}

	/**
	 * Adds a lookup legend to the plot. Does nothing if no measurement mapping
	 * occurred successfully.
	 * 
	 * Note that when performing mapping to different measurements, the legend will
	 * reflects the last measurement.
	 */
	public void addLookupLegend() {

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
		addPaths(tree.getPaths());
	}

	/**
	 * Appends a tree to the plot.
	 *
	 * @param tree
	 *            the Collection of paths to be plotted
	 * @param color
	 *            the color to render the Tree
	 */
	public void addTree(final Tree tree, final Color color) {
		final Color prevDefaultColor = defaultColor;
		setDefaultColor(color);
		addPaths(tree.getPaths());
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
		this.paths = tree.getPaths();
		mapToProperty(measurement, colorTable, min, max);
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

	public void addTrees(final List<Tree> trees, final String lut) {
		final double max = trees.size();
		for (final ListIterator<Tree> it = trees.listIterator(); it.hasNext();) {
			addTree(it.next(), INTERNAL_COUNTER, lut, 1, max);
			internalCounter = it.nextIndex();
		}
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
		if (plot == null)
			plot = plotService.newXYPlot();
		if (show && chart == null) {
			uiService.show((title == null) ? "SNT Path Plot" : title, plot);
		} else if (show) {
			final ChartFrame frame = new ChartFrame(title, chart);
			frame.setPreferredSize(new Dimension(600, 450));
			frame.pack();
			frame.setVisible(true);
		}
		return plot;
	}

	/**
	 * Sets the default (fallback) color for plotting paths.
	 *
	 * @param color
	 *            null not allowed
	 */
	public void setDefaultColor(final Color color) {
		if (color != null)
			defaultColor = color;
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

	/** Displays the current plot on a dedicated frame */
	public void showPlot() {
		getPlot(true);
	}

	private class MappedPath {

		private final Path path;
		private final Double mappedValue;

		private MappedPath(final Path path, final Double mappedValue) {
			this.path = path;
			this.mappedValue = mappedValue;
			if (mappedValue > max)
				max = mappedValue;
			if (mappedValue < min)
				min = mappedValue;
		}
	}

	/** IDE debug method */
	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final TreePlot pplot = new TreePlot(ij.context());
		final List<Tree> trees = new ArrayList<Tree>();
		for (int i = 0; i < 10; i++) {
			final Tree tree = new Tree(new HashSet<Path>(DistributionCmd.randomPaths()));
			tree.rotate(Tree.Z_AXIS, i * 20);
			trees.add(tree);
		}
		pplot.addTrees(trees, "Ice.lut");
		pplot.addLookupLegend();
		pplot.showPlot();
	}

}
