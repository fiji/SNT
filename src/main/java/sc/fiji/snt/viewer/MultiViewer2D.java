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

package sc.fiji.snt.viewer;

import java.awt.GridLayout;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JFrame;

import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.ImageTitle;
import org.jfree.chart.title.PaintScaleLegend;
import org.scijava.Context;
import org.scijava.ui.UIService;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import net.imagej.plot.PlotService;
import net.imglib2.display.ColorTable;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.TreeColorMapper;
import sc.fiji.snt.util.PointInImage;

/**
 * Class for rendering {@link Tree}s as 2D plots that can be exported as SVG,
 * PNG or PDF.
 *
 * @author Tiago Ferreira
 */
public class MultiViewer2D {

	private final List<Viewer2D> viewers;
	private List<ChartPanel> rowPanels;
	private int gridCols;
	private Viewer2D colorLegendViewer;
	private PaintScaleLegend legend;
	private ImageTitle legendSpacer;
	private JFrame frame;
	private boolean gridVisible;
	private boolean axesVisible;
	private boolean outlineVisible;

	public MultiViewer2D(final List<Viewer2D> viewers) {
		if (viewers == null)
			throw new IllegalArgumentException("Cannot instantiate a grid from a null list of viewers");
		this.viewers = viewers;
		guessLayout();
		setAxesVisible(true);
		setGridlinesVisible(true);
		setOutlineVisible(true);
	}

	private void guessLayout() {
		gridCols = (int) Math.ceil(viewers.size() / 2);
	}

	public void setLayoutColumns(final int cols) {
		if (cols < 0) {
			guessLayout();
		} else {
			gridCols = cols;
		}
	}

	public void setGridlinesVisible(final boolean visible) {
		gridVisible = visible;
	}

	public void setAxesVisible(final boolean visible) {
		axesVisible = visible;
	}

	public void setOutlineVisible(final boolean visible) {
		outlineVisible = visible;
	}

	public void setColorBarLegend(final ColorTable colorTable, final double min, final double max) {
		colorLegendViewer = viewers.get(viewers.size() - 1);
		final Viewer2D colorLegendViewer = new Viewer2D(
				new Context(PlotService.class, LUTService.class, UIService.class));
		legend = colorLegendViewer.getPaintScaleLegend(colorTable, min, max);
		legendSpacer = getLegendSpacer(legend);
	}

	public void setColorBarLegend(final String colorTable, final double min, final double max) {
		colorLegendViewer = viewers.get(viewers.size() - 1);
		final Viewer2D colorLegendViewer = new Viewer2D(
				new Context(PlotService.class, LUTService.class, UIService.class));
		legend = colorLegendViewer.getPaintScaleLegend(colorTable, min, max);
		legendSpacer = getLegendSpacer(legend);
	}

	public void save(final String filePath) {
		final File f = new File(filePath);
		f.mkdirs();
		if (rowPanels == null) {
			// assemble rowPanels;
			frame = getJFrame();
		}
		int i = 1;
		for (final ChartPanel cPanel : rowPanels) {
			try {
				final OutputStream out = new FileOutputStream(filePath + "-" + i + ".png");
				ChartUtils.writeChartAsPNG(out, cPanel.getChart(), cPanel.getWidth(), cPanel.getHeight());
				i++;
			} catch (final IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public JFrame show() {
		frame = getJFrame();
		frame.setTitle("Multi-Pane Reconstruction Plotter");
		frame.setVisible(true);
		return frame;
	}

	private JFrame getJFrame() {
		// make all plots the same size
		int w = Integer.MIN_VALUE;
		int h = Integer.MIN_VALUE;
		for (final Viewer2D viewer : viewers) {
			if (viewer.plot == null)
				continue;
			if (viewer.plot.getPreferredWidth() > w)
				w = viewer.plot.getPreferredWidth();
			if (viewer.plot.getPreferredHeight() > h)
				h = viewer.plot.getPreferredHeight();
		}
		for (final Viewer2D viewer : viewers)
			viewer.setPreferredSize(w, h);

		final List<List<Viewer2D>> rows = new ArrayList<>();
		rowPanels = new ArrayList<>();
		for (int i = 0; i < viewers.size(); i += gridCols) {
			rows.add(viewers.subList(i, Math.min(i + gridCols, viewers.size())));
		}
		final JFrame frame = new JFrame();
		final GridLayout gridLayout = new GridLayout(rows.size(), 1);
		frame.setLayout(gridLayout);
		for (final List<Viewer2D> row : rows) {
			final JFreeChart rowChart = getMergedChart(row, "col");
			final ChartPanel cPanel = getChartPanel(rowChart);
			frame.add(cPanel);
			rowPanels.add(cPanel);
		}
		frame.pack();
		return frame;
	}

	private JFreeChart getMergedChart(final Collection<Viewer2D> viewers, final String style) {
		JFreeChart result;
		if (style != null && style.toLowerCase().startsWith("c")) { // column
			final CombinedRangeXYPlot mergedPlot = new CombinedRangeXYPlot();
			for (final Viewer2D viewer : viewers) {
				final XYPlot plot = viewer.getChart().getXYPlot();
				plot.getDomainAxis().setVisible(axesVisible);
				plot.getRangeAxis().setVisible(axesVisible);
				plot.setDomainGridlinesVisible(gridVisible);
				plot.setRangeGridlinesVisible(gridVisible);
				plot.setOutlineVisible(outlineVisible);
				mergedPlot.add(plot, 1);
			}
			result = new JFreeChart(null, mergedPlot);
		} else {
			final CombinedDomainXYPlot mergedPlot = new CombinedDomainXYPlot();
			for (final Viewer2D viewer : viewers) {
				mergedPlot.add(viewer.getChart().getXYPlot(), 1);
			}
			result = new JFreeChart(null, mergedPlot);
		}
		if (legend != null && legendSpacer != null) {
			if (viewers.contains(colorLegendViewer)) {
				result.addSubtitle(legend);
			} else {
				result.addSubtitle(legendSpacer);
			}
		}
		return result;
	}

	private ImageTitle getLegendSpacer(final PaintScaleLegend legend) {
		final int LEGEND_BAR_WIDTH = 35; // HACK: empirically determined!
		final ImageTitle spacer = new ImageTitle(new BufferedImage(LEGEND_BAR_WIDTH, 2, ColorSpace.TYPE_RGB));
		spacer.setVisible(legend.isVisible());
		spacer.setPosition(legend.getPosition());
		spacer.setBounds(legend.getBounds());
		spacer.setMargin(legend.getMargin());
		spacer.setPadding(legend.getPadding());
		spacer.setWidth(legend.getWidth());
		spacer.setHeight(legend.getHeight());
		return spacer;
	}

	private ChartPanel getChartPanel(final JFreeChart chart) {
		final ChartFrame cFrame = new ChartFrame("", chart);
		chart.setBackgroundPaint(null); // transparent
		chart.getPlot().setBackgroundPaint(null); // transparent
		cFrame.getChartPanel().setBackground(null); // transparent
		return cFrame.getChartPanel();
	}

	/* IDE debug method */
	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		final List<Tree> trees = new SNTService().demoTrees();
		TreeColorMapper mapper = new TreeColorMapper(ij.context());
		for (Tree tree : trees) {
			tree.rotate(Tree.Z_AXIS, 210);
			final PointInImage root = tree.getRoot();
			tree.translate(-root.getX(), -root.getY(), -root.getZ());
			mapper.map(tree, TreeColorMapper.TAG_FILENAME, "Ice.lut");
		}
		final MultiViewer2D viewer = mapper.getMultiViewer();
		viewer.setLayoutColumns(2);
		viewer.setGridlinesVisible(false);
		viewer.setOutlineVisible(false);
		viewer.setAxesVisible(false);
		viewer.show();
	}

}
