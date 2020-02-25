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

import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;

import sc.fiji.snt.Tree;

/**
 * Extension of {@link ChartFrame} with convenience methods for plot annotations.
 *
 * @author Tiago Ferreira
 */
public class SNTChart extends ChartFrame {

	private static final long serialVersionUID = 5245298401153759551L;
	private static final Color BACKGROUND_COLOR = null;

	protected SNTChart(final String title, final JFreeChart chart) {
		this(title, chart, new Dimension(400, 400));
	}

	public SNTChart(String title, JFreeChart chart, Dimension preferredSize) {
		super(title, chart);
		chart.setBackgroundPaint(null);
		chart.setAntiAlias(true);
		chart.setTextAntiAlias(true);
		final ChartPanel cp = new ChartPanel(chart);
		// Tweak: Ensure chart is always drawn and not scaled to avoid rendering
		// artifacts
		cp.setMinimumDrawWidth(0);
		cp.setMaximumDrawWidth(Integer.MAX_VALUE);
		cp.setMinimumDrawHeight(0);
		cp.setMaximumDrawHeight(Integer.MAX_VALUE);
		cp.setBackground(BACKGROUND_COLOR);
		setBackground(Color.WHITE); // provided contrast to otherwise transparent background
		setPreferredSize(preferredSize);
		pack();
	}

	private XYPlot getPlot() {
		return getChartPanel().getChart().getXYPlot();
	}

	public void annotateXline(final double xValue, final String label) {
		final Marker marker = new ValueMarker(xValue);
		marker.setPaint(Color.BLACK);
		marker.setLabelBackgroundColor(new Color(255,255,255,0));
		if (label != null)
			marker.setLabel(label);
		marker.setLabelAnchor(RectangleAnchor.TOP_LEFT);
		marker.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
		marker.setLabelFont(getPlot().getDomainAxis().getTickLabelFont());
		getPlot().addDomainMarker(marker);
	}

	public void annotateYline(final double yValue, final String label) {
		final Marker marker = new ValueMarker(yValue);
		marker.setPaint(Color.BLACK);
		marker.setLabelBackgroundColor(new Color(255,255,255,0));
		if (label != null)
			marker.setLabel(label);
		marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
		marker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
		marker.setLabelFont(getPlot().getRangeAxis().getTickLabelFont());
		getPlot().addRangeMarker(marker);
	}

	public void annotatePoint(final double x, final double y, final String label) {
		final XYPointerAnnotation annot = new XYPointerAnnotation(label, x, y, -Math.PI / 2.0);
		final Font font = getPlot().getDomainAxis().getTickLabelFont();
		annot.setLabelOffset(font.getSize());
		annot.setFont(font);
		getPlot().addAnnotation(annot);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void show() {
		super.show();
	}

	/* IDE debug method */
	public static void main(final String[] args) {
		final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
		final TreeStatistics treeStats = new TreeStatistics(tree);
		final SNTChart chart = treeStats.getHistogram("contraction");
		chart.annotatePoint(0.5, 0.15, "No data here");
		chart.annotateXline(0.275, "Start of slope");
		chart.annotateYline(0.050, "5% mark");
		chart.show();
	}

}
