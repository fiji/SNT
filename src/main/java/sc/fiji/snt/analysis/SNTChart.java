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

import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;

/**
 * Extension of {@link ChartFrame} with convenience methods for plot annotations.
 *
 * @author Tiago Ferreira
 */
public class SNTChart extends ChartFrame {

	private static final long serialVersionUID = 5245298401153759551L;
	private static final Color BACKGROUND_COLOR = null;

	protected SNTChart(final String title, final JFreeChart chart) {
		super(title, chart);
		getChartPanel().setBackground(BACKGROUND_COLOR);
		setPreferredSize(new Dimension(400, 400));
		setBackground(Color.WHITE); // provided contrast to otherwise transparent background
		pack();
	}

	private XYPlot getPlot() {
		return getChartPanel().getChart().getXYPlot();
	}

	public void annotateXline(final double xValue, final String label) {
		final Marker marker = new ValueMarker(xValue);
		marker.setPaint(Color.BLACK);
		marker.setLabelBackgroundColor(Color.WHITE);
		if (label != null)
			marker.setLabel(label);
		marker.setLabelAnchor(RectangleAnchor.TOP_LEFT);
		marker.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
		getPlot().addDomainMarker(marker);
	}

	public void annotateYline(final double yValue, final String label) {
		final Marker marker = new ValueMarker(yValue);
		marker.setPaint(Color.BLACK);
		marker.setLabelBackgroundColor(Color.WHITE);
		if (label != null)
			marker.setLabel(label);
		marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
		marker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
		getPlot().addRangeMarker(marker);
	}

	public void annotatePoint(final double x, final double y, final String label) {
		// final XYTextAnnotation annot = new XYTextAnnotation(label, x, y);
		final XYPointerAnnotation annot = new XYPointerAnnotation(label, x, y, -Math.PI / 2.0);
		getPlot().addAnnotation(annot);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void show() {
		super.show();
	}
}
