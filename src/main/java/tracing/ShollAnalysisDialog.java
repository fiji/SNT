/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2006, 2007, 2008, 2009, 2010, 2011 Mark Longair */

/*
  This file is part of the ImageJ plugin "Simple Neurite Tracer".

  The ImageJ plugin "Simple Neurite Tracer" is free software; you
  can redistribute it and/or modify it under the terms of the GNU
  General Public License as published by the Free Software
  Foundation; either version 3 of the License, or (at your option)
  any later version.

  The ImageJ plugin "Simple Neurite Tracer" is distributed in the
  hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE.  See the GNU General Public License for more
  details.

  In addition, as a special exception, the copyright holders give
  you permission to combine this program with free software programs or
  libraries that are released under the Apache Public License.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package tracing;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GUI;
import ij.io.FileInfo;
import ij.io.SaveDialog;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import sholl.Sholl_Analysis;
import sholl.Sholl_Utils;
import util.FindConnectedRegions;

@SuppressWarnings("serial")
public class ShollAnalysisDialog extends Dialog implements WindowListener, ActionListener, TextListener, ItemListener {

	protected double x_start, y_start, z_start;

	protected CheckboxGroup pathsGroup = new CheckboxGroup();
	protected Checkbox useAllPathsCheckbox = new Checkbox("Use all paths in analysis?", pathsGroup, true);
	protected Checkbox useSelectedPathsCheckbox = new Checkbox("Use only selected paths in analysis?", pathsGroup,
			false);

	protected JButton swcTypesButton = new JButton("SWC Type Filtering...");
	protected JPopupMenu swcTypesMenu = new JPopupMenu();
	protected ArrayList<String> filteredTypes = Path.getSWCtypeNames();
	protected JLabel filteredTypesWarningLabel = new JLabel();

	protected Button makeShollImageButton = new Button("Sholl Image");
	protected Button exportProfileButton = new Button("Save Profile...");
	protected Button drawShollGraphButton = new Button("Preview Plot");
	protected Button analyzeButton = new Button("Analyze Profile (Sholl Analysis v" + Sholl_Utils.version() + ")...");

	protected int numberOfSelectedPaths;
	protected int numberOfAllPaths;

	protected CheckboxGroup axesGroup = new CheckboxGroup();
	protected Checkbox normalAxes = new Checkbox("Use standard axes", axesGroup, true);
	protected Checkbox semiLogAxes = new Checkbox("Use semi-log axes", axesGroup, false);
	protected Checkbox logLogAxes = new Checkbox("Use log-log axes", axesGroup, false);

	protected CheckboxGroup normalizationGroup = new CheckboxGroup();
	protected Checkbox noNormalization = new Checkbox("No normalization of intersections", normalizationGroup, true);
	protected Checkbox normalizationForSphereVolume = new Checkbox("[BUG: should be set in the constructor]",
			normalizationGroup, false);

	protected TextField sampleSeparation = new TextField(
			"" + Prefs.get("tracing.ShollAnalysisDialog.sampleSeparation", 0));

	protected ShollResults currentResults;
	protected ImagePlus originalImage;
	private String exportPath;

	GraphFrame graphFrame;

	@Override
	public void actionPerformed(final ActionEvent e) {
		final Object source = e.getSource();
		final ShollResults results;
		synchronized (this) {
			results = getCurrentResults();
		}
		if (results == null) {
			IJ.error("The sphere separation field must be a number, not '" + sampleSeparation.getText() + "'");
			return;
		}
		if (source == makeShollImageButton) {
			results.makeShollCrossingsImagePlus(originalImage);

		} else if (source == analyzeButton) {

			final Thread newThread = new Thread(new Runnable() {
				@Override
				public void run() {
					analyzeButton.setEnabled(false);
					analyzeButton.setLabel("Running Analysis. Please wait...");
					results.analyzeWithShollAnalysisPlugin(getExportPath(), shollpafm.getPathsStructured().length);
					analyzeButton.setLabel("Analyze Profile (Sholl Analysis v" + Sholl_Utils.version() + ")...");
					analyzeButton.setEnabled(true);
				}
			});
			newThread.start();
			return;

		} else if (source == exportProfileButton) {

			// We only only to save the detailed profile. Summary profile will
			// be handled by sholl.Sholl_Analysis

			final SaveDialog sd = new SaveDialog("Export data as...", getExportPath(),
					originalImage.getTitle() + "-sholl" + results.getSuggestedSuffix(), ".csv");

			if (sd.getFileName() == null) {
				return;
			}

			final File saveFile = new File(exportPath = sd.getDirectory(), sd.getFileName());
			if ((saveFile != null) && saveFile.exists()) {
				if (!IJ.showMessageWithCancel("Export data...",
						"The file " + saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Exporting CSV data to " + saveFile.getAbsolutePath());

			try {
				results.exportDetailToCSV(saveFile);
			} catch (final IOException ioe) {
				IJ.error("Saving to " + saveFile.getAbsolutePath() + " failed");
				return;
			}

		} else if (source == drawShollGraphButton) {
			graphFrame.setVisible(true);
		}
	}

	@Override
	public void textValueChanged(final TextEvent e) {
		final Object source = e.getSource();
		if (source == sampleSeparation) {
			final String sampleSeparationText = sampleSeparation.getText();
			float s;
			try {
				s = Float.parseFloat(sampleSeparationText);
			} catch (final NumberFormatException nfe) {
				return;
			}
			if (s >= 0)
				updateResults();
		}
	}

	protected synchronized void updateResults() {
		JFreeChart chart;

		if (numberOfAllPaths <= 0) {

			makePromptInteractive(false);
			if (graphFrame != null) {
				chart = graphFrame.chartPanel.getChart();
				if (chart != null) {
					chart.setNotify(false);
					final TextTitle currentitle = chart.getTitle();
					if (currentitle != null)
						currentitle.setText("");
					final XYPlot plot = chart.getXYPlot();
					if (plot != null)
						plot.setDataset(null);
					chart.setNotify(true);
				}
			}

		} else { // valid paths to be analyzed

			makePromptInteractive(true);
			final ShollResults results = getCurrentResults();
			resultsPanel.updateFromResults(results);
			chart = results.createGraph();
			if (chart == null)
				return;
			if (graphFrame == null)
				graphFrame = new GraphFrame(chart, results.getSuggestedSuffix());
			else
				graphFrame.updateWithNewChart(chart, results.getSuggestedSuffix());
		}

	}

	private void makePromptInteractive(final boolean interactive) {
		if (!interactive) {
			final String noData = " ";
			resultsPanel.criticalValuesLabel.setText(noData);
			resultsPanel.dendriteMaximumLabel.setText(noData);
			resultsPanel.shollsRegressionCoefficientLabel.setText(noData);
			resultsPanel.shollsRegressionInterceptLabel.setText(noData);
			resultsPanel.shollsRegressionRSquaredLabel.setText(noData);
			filteredTypesWarningLabel.setText("No paths matching current filter(s). Please revise choices...");
			filteredTypesWarningLabel.setForeground(java.awt.Color.RED);
		} else {
			filteredTypesWarningLabel.setText("" + filteredTypes.size() + " type(s) are currently selected");
			filteredTypesWarningLabel.setForeground(java.awt.Color.DARK_GRAY);
		}
		drawShollGraphButton.setEnabled(interactive);
		exportProfileButton.setEnabled(interactive);
		analyzeButton.setEnabled(interactive);
		makeShollImageButton.setEnabled(interactive);
	}

	@Override
	public void itemStateChanged(final ItemEvent e) {
		updateResults();
	}

	public ShollResults getCurrentResults() {
		List<ShollPoint> pointsToUse;
		String description = "Sholl analysis ";
		final String postDescription = " for " + originalImage.getTitle();
		final boolean useAllPaths = !useSelectedPathsCheckbox.getState();
		if (useAllPaths) {
			pointsToUse = shollPointsAllPaths;
			description += "of all paths" + postDescription;
		} else {
			pointsToUse = shollPointsSelectedPaths;
			description += "of selected paths " + postDescription;
		}

		int axes = 0;
		if (normalAxes.getState())
			axes = AXES_NORMAL;
		else if (semiLogAxes.getState())
			axes = AXES_SEMI_LOG;
		else if (logLogAxes.getState())
			axes = AXES_LOG_LOG;
		else
			throw new RuntimeException("BUG: somehow no axis checkbox was selected");

		int normalization = 0;
		if (noNormalization.getState())
			normalization = NOT_NORMALIZED;
		else if (normalizationForSphereVolume.getState())
			normalization = NORMALIZED_FOR_SPHERE_VOLUME;
		else
			throw new RuntimeException("BUG: somehow no normalization checkbox was selected");

		final String sphereSeparationString = sampleSeparation.getText();
		double sphereSeparation = Double.MIN_VALUE;

		try {
			sphereSeparation = Double.parseDouble(sphereSeparationString);
		} catch (final NumberFormatException nfe) {
			return null;
		}

		final ShollResults results = new ShollResults(pointsToUse, originalImage, useAllPaths,
				useAllPaths ? numberOfAllPaths : numberOfSelectedPaths, x_start, y_start, z_start, description, axes,
				normalization, sphereSeparation, twoDimensional);

		return results;
	}

	public static class GraphFrame extends JFrame implements ActionListener {
		JButton exportButton;
		JFreeChart chart = null;
		ChartPanel chartPanel = null;
		JPanel mainPanel;
		String suggestedSuffix;

		public void updateWithNewChart(final JFreeChart chart, final String suggestedSuffix) {
			updateWithNewChart(chart, suggestedSuffix, false);
		}

		synchronized public void updateWithNewChart(final JFreeChart chart, final String suggestedSuffix,
				final boolean setSize) {
			this.suggestedSuffix = suggestedSuffix;
			if (chartPanel != null)
				remove(chartPanel);
			chartPanel = null;
			this.chart = chart;
			chartPanel = new ChartPanel(chart);
			if (setSize)
				chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
			mainPanel.add(chartPanel, BorderLayout.CENTER);
			validate();
		}

		public GraphFrame(final JFreeChart chart, final String suggestedSuffix) {
			super();

			this.suggestedSuffix = suggestedSuffix;

			mainPanel = new JPanel();
			mainPanel.setLayout(new BorderLayout());

			updateWithNewChart(chart, suggestedSuffix, true);

			final JPanel buttonsPanel = new JPanel();
			exportButton = new JButton("Export graph as SVG");
			exportButton.addActionListener(this);
			buttonsPanel.add(exportButton);
			mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

			setContentPane(mainPanel);
			validate();
			setSize(new java.awt.Dimension(500, 270));
			GUI.center(this);
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			final Object source = e.getSource();
			if (source == exportButton) {
				exportGraphAsSVG();
			}
		}

		public void exportGraphAsSVG() {

			final SaveDialog sd = new SaveDialog("Export graph as...", "sholl" + suggestedSuffix, ".svg");

			if (sd.getFileName() == null) {
				return;
			}

			final File saveFile = new File(sd.getDirectory(), sd.getFileName());
			if ((saveFile != null) && saveFile.exists()) {
				if (!IJ.showMessageWithCancel("Export graph...",
						"The file " + saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Exporting graph to " + saveFile.getAbsolutePath());

			try {
				exportChartAsSVG(chart, chartPanel.getBounds(), saveFile);
			} catch (final IOException ioe) {
				IJ.error("Saving to " + saveFile.getAbsolutePath() + " failed");
				return;
			}

		}

		/**
		 * Exports a JFreeChart to a SVG file.
		 *
		 * @param chart
		 *            JFreeChart to export
		 * @param bounds
		 *            the dimensions of the viewport
		 * @param svgFile
		 *            the output file.
		 * @throws IOException
		 *             if writing the svgFile fails.
		 *
		 *             This method is taken from:
		 *             http://dolf.trieschnigg.nl/jfreechart/
		 */
		void exportChartAsSVG(final JFreeChart chart, final Rectangle bounds, final File svgFile) throws IOException {

			// Get a DOMImplementation and create an XML document
			final DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
			final Document document = domImpl.createDocument(null, "svg", null);

			// Create an instance of the SVG Generator
			final SVGGraphics2D svgGenerator = new SVGGraphics2D(document);

			// draw the chart in the SVG generator
			chart.draw(svgGenerator, bounds);

			// Write svg file
			final OutputStream outputStream = new FileOutputStream(svgFile);
			final Writer out = new OutputStreamWriter(outputStream, "UTF-8");
			svgGenerator.stream(out, true /* use css */);
			outputStream.flush();
			outputStream.close();
		}
	}

	public static final int AXES_NORMAL = 1;
	public static final int AXES_SEMI_LOG = 2;
	public static final int AXES_LOG_LOG = 3;

	public static final String[] axesParameters = { null, "normal", "semi-log", "log-log" };

	public static final int NOT_NORMALIZED = 1;
	public static final int NORMALIZED_FOR_SPHERE_VOLUME = 2;

	public static final String[] normalizationParameters = { null, "not-normalized", "normalized" };

	public static class ShollResults {
		protected double[] squaredRangeStarts;
		protected int[] crossingsPastEach;
		protected int n;
		protected double x_start, y_start, z_start;
		/* maxCrossings is the same as the "Dendrite Maximum". */
		protected int maxCrossings = Integer.MIN_VALUE;
		protected double criticalValue = Double.MIN_VALUE;
		protected String description;
		protected int axes;
		protected int normalization;
		protected double sphereSeparation;
		protected double[] x_graph_points;
		protected double[] y_graph_points;
		protected double minY;
		protected double maxY;
		protected int graphPoints;
		protected String yAxisLabel;
		protected String xAxisLabel;
		protected double regressionGradient = Double.MIN_VALUE;
		protected double regressionIntercept = Double.MIN_VALUE;
		protected double regressionRSquare = Double.NaN;
		protected int n_samples;
		protected double[] sampled_distances;
		protected double[] sampled_counts;
		String parametersSuffix;

		public double[] getSampledDistances() {
			return sampled_distances;
		}

		public double[] getSampledCounts() {
			return sampled_counts;
		}

		public int getDendriteMaximum() {
			return maxCrossings;
		}

		public double getCriticalValue() {
			return criticalValue;
		}

		public double getRegressionGradient() {
			return regressionGradient;
		}

		public double getShollRegressionCoefficient() {
			return -regressionGradient;
		}

		public double getRegressionIntercept() {
			return regressionIntercept;
		}

		public double getRegressionRSquare() {
			return regressionRSquare;
		}

		public double getMaxDistanceSquared() {
			return squaredRangeStarts[n - 1];
		}

		public String getSuggestedSuffix() {
			return parametersSuffix;
		}

		/**
		 * Instructs the Sholl Analysis plugin to analyze the profile sampled by
		 * .
		 */
		public void analyzeWithShollAnalysisPlugin(final String exportDir, final double primaryBranches) {

			final Sholl_Analysis sa = new Sholl_Analysis();
			sa.setDescription("Tracings for " + originalImage.getTitle(), false);
			final Calibration cal = originalImage.getCalibration();
			if (cal != null) {
				final int pX = (int) cal.getRawX(x_start);
				final int pY = (int) cal.getRawY(y_start, originalImage.getHeight());
				final int pZ = (int) (z_start / cal.pixelDepth + cal.zOrigin);
				sa.setCenter(pX, pY, pZ);
				sa.setUnit(cal.getUnit());
			}
			sa.setStepRadius(sphereSeparation);
			sa.setPrimaryBranches(primaryBranches);
			sa.setExportPath(exportDir);
			sa.analyzeProfile(sampled_distances, sampled_counts, !twoDimensional);

		}

		public void addToResultsTable() {
			final ResultsTable rt = Analyzer.getResultsTable();
			if (!Analyzer.resetCounter())
				return;
			rt.incrementCounter();
			rt.addValue("Filename", getOriginalFilename());
			rt.addValue("All paths used", String.valueOf(useAllPaths));
			rt.addValue("Paths used", numberOfPathsUsed);
			rt.addValue("Sphere separation", sphereSeparation);
			rt.addValue("Normalization", normalization);
			rt.addValue("Axes", axes);
			rt.addValue("Max inters. radius", getCriticalValue());
			rt.addValue("Max inters.", getDendriteMaximum());
			rt.addValue("Regression coefficient", getShollRegressionCoefficient());
			rt.addValue("Regression gradient", getRegressionGradient());
			rt.addValue("Regression intercept", getRegressionIntercept());
			rt.show("Results");
		}

		boolean twoDimensional;
		ImagePlus originalImage;
		boolean useAllPaths;
		int numberOfPathsUsed;

		public ShollResults(final List<ShollPoint> shollPoints, final ImagePlus originalImage,
				final boolean useAllPaths, final int numberOfPathsUsed, final double x_start, final double y_start,
				final double z_start, final String description, final int axes, final int normalization,
				final double sphereSeparation, final boolean twoDimensional) {
			parametersSuffix = "_" + axesParameters[axes] + "_" + normalizationParameters[normalization] + "_"
					+ sphereSeparation;
			this.originalImage = originalImage;
			this.useAllPaths = useAllPaths;
			this.numberOfPathsUsed = numberOfPathsUsed;
			this.x_start = x_start;
			this.y_start = y_start;
			this.z_start = z_start;
			this.description = description;
			this.axes = axes;
			this.normalization = normalization;
			this.sphereSeparation = sphereSeparation;
			this.twoDimensional = twoDimensional;
			Collections.sort(shollPoints);
			n = shollPoints.size();
			squaredRangeStarts = new double[n];
			crossingsPastEach = new int[n];
			int currentCrossings = 0;
			for (int i = 0; i < n; ++i) {
				final ShollPoint p = shollPoints.get(i);
				if (p.nearer)
					++currentCrossings;
				else
					--currentCrossings;
				squaredRangeStarts[i] = p.distanceSquared;
				crossingsPastEach[i] = currentCrossings;
				if (currentCrossings > maxCrossings) {
					maxCrossings = currentCrossings;
					criticalValue = Math.sqrt(p.distanceSquared);
				}
				// System.out.println("Range starting at:
				// "+Math.sqrt(p.distanceSquared)+" has crossings:
				// "+currentCrossings);
			}

			// Retrieve the data points for the sampled profile
			if (sphereSeparation > 0) { // Discontinuous sampling

				n_samples = (int) Math.ceil(Math.sqrt(getMaxDistanceSquared()) / sphereSeparation);
				sampled_distances = new double[n_samples];
				sampled_counts = new double[n_samples];
				for (int i = 0; i < n_samples; ++i) {
					final double x = i * sphereSeparation;
					sampled_distances[i] = x;
					sampled_counts[i] = crossingsAtDistanceSquared(x * x);
				}

			} else { // Continuous sampling

				// We'll ensure we are not keeping duplicated data points so
				// we'll store unique distances in a temporary LinkedHashSet
				final LinkedHashSet<Double> uniqueDistancesSquared = new LinkedHashSet<>();
				for (int i = 0; i < n; ++i)
					uniqueDistancesSquared.add(squaredRangeStarts[i]);
				n_samples = uniqueDistancesSquared.size();
				sampled_distances = new double[n_samples];
				sampled_counts = new double[n_samples];
				final Iterator<Double> it = uniqueDistancesSquared.iterator();
				int idx = 0;
				while (it.hasNext()) {
					final double distanceSquared = it.next();
					sampled_distances[idx] = Math.sqrt(distanceSquared);
					sampled_counts[idx++] = crossingsAtDistanceSquared(distanceSquared);
				}
			}

			// At this point what has been sampled is what is set to be plotted
			// in a non-normalized linear plot
			graphPoints = n_samples;
			x_graph_points = Arrays.copyOf(sampled_distances, n_samples);
			y_graph_points = Arrays.copyOf(sampled_counts, n_samples);

			xAxisLabel = "Distance from (" + IJ.d2s(x_start, 3) + ", " + IJ.d2s(y_start, 3) + ", " + IJ.d2s(z_start, 3)
					+ ")";
			yAxisLabel = "N. of Intersections";

			if (normalization == NORMALIZED_FOR_SPHERE_VOLUME) {
				for (int i = 0; i < graphPoints; ++i) {
					final double x = x_graph_points[i];
					final double distanceSquared = x * x;
					if (twoDimensional)
						y_graph_points[i] /= (Math.PI * distanceSquared);
					else
						y_graph_points[i] /= ((4.0 * Math.PI * x * distanceSquared) / 3.0);
				}
				if (twoDimensional)
					yAxisLabel = "Inters./Area";
				else
					yAxisLabel = "Inters./Volume";
			}

			final SimpleRegression regression = new SimpleRegression();

			maxY = Double.MIN_VALUE;
			minY = Double.MAX_VALUE;
			for (int i = 0; i < graphPoints; ++i) {
				final double x = x_graph_points[i];
				final double y = y_graph_points[i];
				double x_for_regression = x;
				double y_for_regression = y;
				if (!(Double.isInfinite(y) || Double.isNaN(y))) {
					if (y > maxY)
						maxY = y;
					if (y < minY)
						minY = y;
					if (axes == AXES_SEMI_LOG) {
						if (y <= 0)
							continue;
						y_for_regression = Math.log(y);
					} else if (axes == AXES_LOG_LOG) {
						if (x <= 0 || y <= 0)
							continue;
						x_for_regression = Math.log(x);
						y_for_regression = Math.log(y);
					}
					regression.addData(x_for_regression, y_for_regression);
				}
			}
			regressionGradient = regression.getSlope();
			regressionIntercept = regression.getIntercept();
			// Retrieve r-squared, i.e., the square of the Pearson regression
			// coefficient
			regressionRSquare = regression.getRSquare();

			if (maxY == Double.MIN_VALUE)
				throw new RuntimeException("[BUG] Somehow there were no valid points found");
		}

		public JFreeChart createGraph() {

			XYSeriesCollection data = null;

			double minX = Double.MAX_VALUE;
			double maxX = Double.MIN_VALUE;

			final XYSeries series = new XYSeries("Intersections");
			for (int i = 0; i < graphPoints; ++i) {
				final double x = x_graph_points[i];
				final double y = y_graph_points[i];
				if (Double.isInfinite(y) || Double.isNaN(y))
					continue;
				if (axes == AXES_SEMI_LOG || axes == AXES_LOG_LOG) {
					if (y <= 0)
						continue;
				}
				if (axes == AXES_LOG_LOG) {
					if (x <= 0)
						continue;
				}
				if (x < minX)
					minX = x;
				if (x > maxX)
					maxX = x;
				series.add(x, y);
			}
			data = new XYSeriesCollection(series);

			ValueAxis xAxis = null;
			ValueAxis yAxis = null;
			if (axes == AXES_NORMAL) {
				xAxis = new NumberAxis(xAxisLabel);
				yAxis = new NumberAxis(yAxisLabel);
			} else if (axes == AXES_SEMI_LOG) {
				xAxis = new NumberAxis(xAxisLabel);
				yAxis = new LogAxis(yAxisLabel);
			} else if (axes == AXES_LOG_LOG) {
				xAxis = new LogAxis(xAxisLabel);
				yAxis = new LogAxis(yAxisLabel);
			}

			try {
				xAxis.setRange(minX, maxX);
				if (axes == AXES_NORMAL)
					yAxis.setRange(0, maxY);
				else
					yAxis.setRange(minY, maxY);
			} catch (final IllegalArgumentException iae) {
				yAxis.setAutoRange(true);
			}

			XYItemRenderer renderer = null;
			if (sphereSeparation > 0) {
				renderer = new XYLineAndShapeRenderer();
			} else {
				final XYBarRenderer barRenderer = new XYBarRenderer();
				barRenderer.setShadowVisible(false);
				//barRenderer.setGradientPaintTransformer(null);
				barRenderer.setDrawBarOutline(false);
				barRenderer.setBarPainter(new StandardXYBarPainter());
				renderer = barRenderer;
			}
			renderer.setSeriesVisibleInLegend(0, false);
			final XYPlot plot = new XYPlot(data, xAxis, yAxis, renderer);

			return new JFreeChart(description, plot);
		}

		public int crossingsAtDistanceSquared(final double distanceSquared) {

			int minIndex = 0;
			int maxIndex = n - 1;

			if (distanceSquared < squaredRangeStarts[minIndex])
				return 1;
			else if (distanceSquared > squaredRangeStarts[maxIndex])
				return 0;

			while (maxIndex - minIndex > 1) {

				final int midPoint = (maxIndex + minIndex) / 2;

				if (distanceSquared < squaredRangeStarts[midPoint])
					maxIndex = midPoint;
				else
					minIndex = midPoint;
			}
			return crossingsPastEach[minIndex];
		}

		public ImagePlus makeShollCrossingsImagePlus(final ImagePlus original) {
			final int width = original.getWidth();
			final int height = original.getHeight();
			final int depth = original.getStackSize();
			final Calibration c = original.getCalibration();
			double x_spacing = 1;
			double y_spacing = 1;
			double z_spacing = 1;
			if (c != null) {
				x_spacing = c.pixelWidth;
				y_spacing = c.pixelHeight;
				z_spacing = c.pixelDepth;
			}
			final ImageStack stack = new ImageStack(width, height);
			for (int z = 0; z < depth; ++z) {
				final short[] pixels = new short[width * height];
				for (int y = 0; y < height; ++y) {
					for (int x = 0; x < width; ++x) {
						final double xdiff = x_spacing * x - x_start;
						final double ydiff = y_spacing * y - y_start;
						final double zdiff = z_spacing * z - z_start;
						final double distanceSquared = xdiff * xdiff + ydiff * ydiff + zdiff * zdiff;
						pixels[y * width + x] = (short) crossingsAtDistanceSquared(distanceSquared);
					}
				}
				final ShortProcessor sp = new ShortProcessor(width, height);
				sp.setPixels(pixels);
				stack.addSlice("", sp);
			}
			final ImagePlus result = new ImagePlus(description, stack);
			result.show();
			final IndexColorModel icm = FindConnectedRegions.backgroundAndSpectrum(255);
			stack.setColorModel(icm);
			final ImageProcessor ip = result.getProcessor();
			if (ip != null) {
				ip.setColorModel(icm);
				ip.setMinAndMax(0, maxCrossings);
			}
			result.updateAndDraw();

			if (c != null)
				result.setCalibration(c);
			return result;
		}

		public static void csvQuoteAndPrint(final PrintWriter pw, final Object o) {
			pw.print(PathAndFillManager.stringForCSV("" + o));
		}

		public String getOriginalFilename() {
			final FileInfo originalFileInfo = originalImage.getOriginalFileInfo();
			if (originalFileInfo.directory == null)
				return "[unknown]";
			else
				return new File(originalFileInfo.directory, originalFileInfo.fileName).getAbsolutePath();

		}

		public void exportSummaryToCSV(final File outputFile) throws IOException {
			final String[] headers = new String[] { "Filename", "All paths used", "Paths used", "Sphere separation",
					"Normalization", "Axes", "Max inters. radius", "Max inters.", "Regression coefficient",
					"Regression gradient", "Regression intercept" };

			final PrintWriter pw = new PrintWriter(
					new OutputStreamWriter(new FileOutputStream(outputFile.getAbsolutePath()), "UTF-8"));
			final int columns = headers.length;
			for (int c = 0; c < columns; ++c) {
				csvQuoteAndPrint(pw, headers[c]);
				if (c < (columns - 1))
					pw.print(",");
			}
			pw.print("\r\n");
			csvQuoteAndPrint(pw, getOriginalFilename());
			pw.print(",");
			csvQuoteAndPrint(pw, useAllPaths);
			pw.print(",");
			csvQuoteAndPrint(pw, numberOfPathsUsed);
			pw.print(",");
			csvQuoteAndPrint(pw, sphereSeparation);
			pw.print(",");
			csvQuoteAndPrint(pw, normalizationParameters[normalization]);
			pw.print(",");
			csvQuoteAndPrint(pw, axesParameters[axes]);
			pw.print(",");
			csvQuoteAndPrint(pw, getCriticalValue());
			pw.print(",");
			csvQuoteAndPrint(pw, getDendriteMaximum());
			pw.print(",");
			csvQuoteAndPrint(pw, getShollRegressionCoefficient());
			pw.print(",");
			csvQuoteAndPrint(pw, getRegressionGradient());
			pw.print(",");
			csvQuoteAndPrint(pw, getRegressionIntercept());
			pw.print("\r\n");

			pw.close();
		}

		public void exportDetailToCSV(final File outputFile) throws IOException {
			String[] headers;
			headers = new String[] { "Radius", "Inters.", (twoDimensional) ? "Inters./Area" : "Inters./Volume" };

			final PrintWriter pw = new PrintWriter(
					new OutputStreamWriter(new FileOutputStream(outputFile.getAbsolutePath()), "UTF-8"));
			final int columns = headers.length;
			for (int c = 0; c < columns; ++c) {
				csvQuoteAndPrint(pw, headers[c]);
				if (c < (columns - 1))
					pw.print(",");
			}
			pw.print("\r\n");

			for (int i = 0; i < n_samples; ++i) {
				double normalizedCrossings = -Double.MIN_VALUE;
				final double x = sampled_distances[i];
				final double y = sampled_counts[i];
				final double distanceSquared = x * x;
				if (twoDimensional)
					normalizedCrossings = y / (Math.PI * distanceSquared);
				else
					normalizedCrossings = y / ((4.0 * Math.PI * x * distanceSquared) / 3.0);
				csvQuoteAndPrint(pw, x);
				pw.print(",");
				csvQuoteAndPrint(pw, y);
				pw.print(",");
				csvQuoteAndPrint(pw, normalizedCrossings);
				pw.print("\r\n");
			}
			pw.close();
		}

	}

	public static class ShollPoint implements Comparable<ShollPoint> {
		protected boolean nearer;
		protected double distanceSquared;

		@Override
		public int compareTo(final ShollPoint other) {
			return Double.compare(this.distanceSquared, other.distanceSquared);
		}

		ShollPoint(final double distanceSquared, final boolean nearer) {
			this.distanceSquared = distanceSquared;
			this.nearer = nearer;
		}
	}

	public static void addPathPointsToShollList(final Path p, final double x_start, final double y_start,
			final double z_start, final List<ShollPoint> shollPointsList) {

		for (int i = 0; i < p.points - 1; ++i) {
			final double xdiff_first = p.precise_x_positions[i] - x_start;
			final double ydiff_first = p.precise_y_positions[i] - y_start;
			final double zdiff_first = p.precise_z_positions[i] - z_start;
			final double xdiff_second = p.precise_x_positions[i + 1] - x_start;
			final double ydiff_second = p.precise_y_positions[i + 1] - y_start;
			final double zdiff_second = p.precise_z_positions[i + 1] - z_start;
			final double distanceSquaredFirst = xdiff_first * xdiff_first + ydiff_first * ydiff_first
					+ zdiff_first * zdiff_first;
			final double distanceSquaredSecond = xdiff_second * xdiff_second + ydiff_second * ydiff_second
					+ zdiff_second * zdiff_second;
			shollPointsList.add(new ShollPoint(distanceSquaredFirst, distanceSquaredFirst < distanceSquaredSecond));
			shollPointsList.add(new ShollPoint(distanceSquaredSecond, distanceSquaredFirst >= distanceSquaredSecond));
		}

	}

	ArrayList<ShollPoint> shollPointsAllPaths;
	ArrayList<ShollPoint> shollPointsSelectedPaths;
	PathAndFillManager shollpafm;
	ResultsPanel resultsPanel = new ResultsPanel();

	protected boolean twoDimensional;

	public ShollAnalysisDialog(final String title, final double x_start, final double y_start, final double z_start,
			final PathAndFillManager pafm, final ImagePlus originalImage) {

		super(IJ.getInstance(), title, false);

		this.x_start = x_start;
		this.y_start = y_start;
		this.z_start = z_start;

		this.originalImage = originalImage;
		twoDimensional = (originalImage.getStackSize() == 1);

		shollPointsAllPaths = new ArrayList<>();
		shollPointsSelectedPaths = new ArrayList<>();
		shollpafm = pafm;
		reloadPaths();

		useAllPathsCheckbox.setLabel("Use all " + numberOfAllPaths + " paths in analysis?");
		useSelectedPathsCheckbox.setLabel("Use only the " + numberOfSelectedPaths + " selected paths in analysis?");

		addWindowListener(this);

		setLayout(new GridBagLayout());

		final GridBagConstraints c = new GridBagConstraints();

		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.LINE_START;
		final int margin = 10;
		c.insets = new Insets(margin, margin, 0, margin);
		useAllPathsCheckbox.addItemListener(this);
		add(useAllPathsCheckbox, c);

		++c.gridy;
		c.insets = new Insets(0, margin, margin, margin);
		add(useSelectedPathsCheckbox, c);
		useSelectedPathsCheckbox.addItemListener(this);

		++c.gridy;
		c.insets = new Insets(0, margin, 0, margin);
		buildTypeFilteringMenu();
		add(swcTypesButton, c);
		++c.gridy;
		c.insets = new Insets(0, margin, margin, margin);
		add(filteredTypesWarningLabel, c);
		makePromptInteractive(true);

		++c.gridy;
		c.insets = new Insets(margin, margin, 0, margin);
		add(normalAxes, c);
		normalAxes.addItemListener(this);
		++c.gridy;
		c.insets = new Insets(0, margin, 0, margin);
		add(semiLogAxes, c);
		semiLogAxes.addItemListener(this);
		++c.gridy;
		c.insets = new Insets(0, margin, margin, margin);
		add(logLogAxes, c);
		logLogAxes.addItemListener(this);

		++c.gridy;
		c.insets = new Insets(margin, margin, 0, margin);
		add(noNormalization, c);
		noNormalization.addItemListener(this);
		++c.gridy;
		c.insets = new Insets(0, margin, margin, margin);
		if (twoDimensional)
			normalizationForSphereVolume.setLabel("Normalize for area enclosed by circle");
		else
			normalizationForSphereVolume.setLabel("Normalize for volume enclosed by circle");
		add(normalizationForSphereVolume, c);
		normalizationForSphereVolume.addItemListener(this);

		++c.gridy;
		c.gridx = 0;
		final Panel separationPanel = new Panel();
		separationPanel.add(new Label("Radius step size (0 for continuous sampling)"));
		sampleSeparation.addTextListener(this);
		separationPanel.add(sampleSeparation);
		final String unit = shollpafm.spacing_units;
		if (unit != null && !unit.equals("unknown") && !unit.equals("pixel"))
			separationPanel.add(new Label(unit));
		add(separationPanel, c);

		c.gridx = 0;
		++c.gridy;
		c.insets = new Insets(margin, margin, margin, margin);
		add(resultsPanel, c);

		++c.gridy;
		final Panel buttonsPanel = new Panel();
		buttonsPanel.setLayout(new BorderLayout());
		final Panel topRow = new Panel();
		final Panel middleRow = new Panel();
		final Panel bottomRow = new Panel();

		topRow.add(makeShollImageButton);
		makeShollImageButton.addActionListener(this);

		topRow.add(drawShollGraphButton);
		drawShollGraphButton.addActionListener(this);

		topRow.add(exportProfileButton);
		exportProfileButton.addActionListener(this);

		middleRow.add(analyzeButton);
		analyzeButton.addActionListener(this);

		buttonsPanel.add(topRow, BorderLayout.NORTH);
		buttonsPanel.add(middleRow, BorderLayout.CENTER);
		buttonsPanel.add(bottomRow, BorderLayout.SOUTH);

		add(buttonsPanel, c);

		pack();

		updateResults();

		GUI.center(this);
		setVisible(true);
		toFront();

	}

	private void reloadPaths() {

		// Reset analysis
		numberOfAllPaths = 0;
		numberOfSelectedPaths = 0;
		shollPointsAllPaths.clear();
		shollPointsSelectedPaths.clear();

		// load paths considering only those whose type has been chosen by user
		for (Path p : shollpafm.allPaths) {
			final boolean selected = p.getSelected();
			if (p.getUseFitted()) {
				p = p.fitted;
			} else if (p.fittedVersionOf != null)
				continue;

			if (filteredTypes.contains(Path.getSWCtypeName(p.getSWCType()))) {
				addPathPointsToShollList(p, x_start, y_start, z_start, shollPointsAllPaths);
				++numberOfAllPaths;
				if (selected) {
					addPathPointsToShollList(p, x_start, y_start, z_start, shollPointsSelectedPaths);
					++numberOfSelectedPaths;
				}
			}
		}

	}

	private void buildTypeFilteringMenu() {
		swcTypesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (!swcTypesMenu.isVisible()) {
					final Point p = swcTypesButton.getLocationOnScreen();
					swcTypesMenu.setInvoker(swcTypesButton);
					swcTypesMenu.setLocation((int) p.getX(), (int) p.getY() + swcTypesButton.getHeight());
					swcTypesMenu.setVisible(true);
				} else {
					swcTypesMenu.setVisible(false);
				}
			}
		});
		for (final String swcType : Path.getSWCtypeNames()) {
			final JMenuItem mi = new JCheckBoxMenuItem(swcType, true);
			mi.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					swcTypesMenu.show(swcTypesButton, 0, swcTypesButton.getHeight());
				}
			});
			mi.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(final ItemEvent e) {
					if (filteredTypes.contains(mi.getText()) && !mi.isSelected()) {
						filteredTypes.remove(mi.getText());
					} else if (!filteredTypes.contains(mi.getText()) && mi.isSelected()) {
						filteredTypes.add(mi.getText());
					}
					reloadPaths();
					updateResults();
				}
			});
			swcTypesMenu.add(mi);
		}

	}

	public class ResultsPanel extends Panel {
		Label headingLabel = new Label("Results:");
		String defaultText = "[Not calculated yet]";
		Label criticalValuesLabel = new Label(defaultText, Label.RIGHT);
		Label dendriteMaximumLabel = new Label(defaultText, Label.RIGHT);
		// Label schoenenRamificationIndexLabel = new Label(defaultText);
		Label shollsRegressionCoefficientLabel = new Label(defaultText, Label.RIGHT);
		Label shollsRegressionInterceptLabel = new Label(defaultText, Label.RIGHT);
		Label shollsRegressionRSquaredLabel = new Label(defaultText, Label.RIGHT);

		public ResultsPanel() {
			super();
			setLayout(new GridBagLayout());
			final GridBagConstraints c = new GridBagConstraints();
			c.anchor = GridBagConstraints.LINE_START;
			c.gridx = 0;
			c.gridy = 0;
			c.gridwidth = 2;
			add(headingLabel, c);
			c.anchor = GridBagConstraints.LINE_END;
			c.gridx = 0;
			++c.gridy;
			c.gridwidth = 1;
			add(new Label("Max inters. radius: "), c);
			c.gridx = 1;
			add(criticalValuesLabel, c);
			c.gridx = 0;
			++c.gridy;
			add(new Label("Max inters.: "), c);
			c.gridx = 1;
			add(dendriteMaximumLabel, c);
			// c.gridx = 0;
			// ++ c.gridy;
			// add(new Label("Schoenen Ramification Index:"),c);
			// c.gridx = 1;
			// add(schoenenRamificationIndexLabel,c);
			c.gridx = 0;
			++c.gridy;
			add(new Label("Regression coeff.: "), c);
			c.gridx = 1;
			add(shollsRegressionCoefficientLabel, c);
			c.gridx = 0;
			++c.gridy;
			add(new Label("Regression intercept: "), c);
			c.gridx = 1;
			add(shollsRegressionInterceptLabel, c);
			c.gridx = 0;
			++c.gridy;
			add(new Label("Regression R2: "), c);
			c.gridx = 1;
			add(shollsRegressionRSquaredLabel, c);
		}

		public void updateFromResults(final ShollResults results) {
			dendriteMaximumLabel.setText("" + results.getDendriteMaximum());
			criticalValuesLabel.setText(IJ.d2s(results.getCriticalValue(), 3));
			shollsRegressionCoefficientLabel.setText(IJ.d2s(results.getShollRegressionCoefficient(), -3));
			shollsRegressionInterceptLabel.setText(IJ.d2s(results.getRegressionIntercept(), 3));
			shollsRegressionRSquaredLabel.setText(IJ.d2s(results.getRegressionRSquare(), 3));
		}
	}

	@Override
	public void windowClosing(final WindowEvent e) {
		dispose();
	}

	@Override
	public void windowActivated(final WindowEvent e) {
	}

	@Override
	public void windowDeactivated(final WindowEvent e) {
	}

	@Override
	public void windowClosed(final WindowEvent e) {
	}

	@Override
	public void windowOpened(final WindowEvent e) {
	}

	@Override
	public void windowIconified(final WindowEvent e) {
	}

	@Override
	public void windowDeiconified(final WindowEvent e) {
	}

	/**
	 * @return the path to save current profile
	 */
	private String getExportPath() {
		if (this.exportPath == null && originalImage != null) {
			final FileInfo fi = originalImage.getOriginalFileInfo();
			if (fi != null && fi.directory != null)
				this.exportPath = fi.directory;
		}
		return this.exportPath;
	}

}
