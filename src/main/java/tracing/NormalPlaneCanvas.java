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

package tracing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;

import org.scijava.display.DisplayService;

import ij.ImagePlus;
import ij.gui.StackWindow;
import tracing.hyperpanes.MultiDThreePanes;

@SuppressWarnings("serial")
class NormalPlaneCanvas extends TracerCanvas {

	private static final char DEG = '\u00b0';
	private static final Color COLOR_VALID_FIT = Color.GREEN;
	private static final Color COLOR_INVALID_FIT = Color.RED;
	private static final Color COLOR_MODE = Color.ORANGE;

	private double maxScore = Double.MIN_VALUE;
	private double minScore = Double.MAX_VALUE;
	private int last_slice = -1;
	private int last_editable_node = -1;

	private final double[] centre_x_positions;
	private final double[] centre_y_positions;
	private final double[] radiuses;
	private final double[] modeRadiuses;
	private final double[] scores;
	private final boolean[] valid;
	private final double[] angles;

	private final Path fittedPath;
	private final SimpleNeuriteTracer tracerPlugin;
	private final HashMap<Integer, Integer> indexToValidIndex = new HashMap<>();

	protected NormalPlaneCanvas(final ImagePlus imp,
		final SimpleNeuriteTracer plugin, final double[] centre_x_positions,
		final double[] centre_y_positions, final double[] radiuses,
		final double[] scores, final double[] modeRadiuses, final double[] angles,
		final boolean[] valid, final Path fittedPath)
	{
		super(imp, plugin, MultiDThreePanes.XY_PLANE, plugin
			.getPathAndFillManager());

		tracerPlugin = plugin;
		this.centre_x_positions = centre_x_positions;
		this.centre_y_positions = centre_y_positions;
		this.radiuses = radiuses;
		this.scores = scores;
		this.modeRadiuses = modeRadiuses;
		this.angles = angles;
		this.valid = valid;
		this.fittedPath = fittedPath;
		for (int i = 0; i < scores.length; ++i) {
			if (scores[i] > maxScore) maxScore = scores[i];
			if (scores[i] < minScore) minScore = scores[i];
		}
		int a = 0;
		for (int i = 0; i < valid.length; ++i) {
			if (valid[i]) {
				indexToValidIndex.put(i, a);
				++a;
			}
		}

		// Make ImageCanvas fully independent from SNT
		disableEvents(true);
		setDrawCrosshairs(false);
		// setAnnotationsColor(Color.BLUE);

	}

	@Override
	protected void drawOverlay(final Graphics2D g) {

		final int z = imp.getZ() - 1;
		final Color fitColor = (valid[z]) ? COLOR_VALID_FIT : COLOR_INVALID_FIT;

		// build label
		final double proportion = (scores[z] - minScore) / (maxScore - minScore);
		super.setAnnotationsColor(fitColor);
		setCanvasLabel(String.format("r=%s score=%s", SNT.formatDouble(radiuses[z],
			2), SNT.formatDouble(proportion, 2)));

		// mark center
		g.setStroke(new BasicStroke(2));
		g.setColor(fitColor);
		g.fill(new Rectangle2D.Double(myScreenXDprecise(centre_x_positions[z]) - 2,
			myScreenYDprecise(centre_y_positions[z]) - 2, 5, 5));

		// show diameter
		final double x_top_left = myScreenXDprecise(centre_x_positions[z] -
			radiuses[z]);
		final double y_top_left = myScreenYDprecise(centre_y_positions[z] -
			radiuses[z]);
		final double diameter = myScreenXDprecise(centre_x_positions[z] +
			radiuses[z]) - myScreenXDprecise(centre_x_positions[z] - radiuses[z]);
		g.draw(new Ellipse2D.Double(x_top_left, y_top_left, diameter, diameter));

		// report angle
		final StringBuilder sb = new StringBuilder();
		sb.append(SNT.formatDouble(Math.toDegrees(angles[z]), 1)).append(DEG);
		if (!valid[z]) sb.append("  Fit discarded");
		g.drawString(sb.toString(), (float) myScreenXDprecise(0),
			(float) myScreenYDprecise(imp.getHeight() - 1));

		// show mode
		g.setColor(COLOR_MODE);
		final double modeOvalX = myScreenXDprecise(imp.getWidth() / 2.0 -
			modeRadiuses[z]);
		final double modeOvalY = myScreenYDprecise(imp.getHeight() / 2.0 -
			modeRadiuses[z]);
		final double modeOvalDiameter = myScreenXDprecise(imp.getWidth() / 2.0 +
			modeRadiuses[z]) - modeOvalX;
		g.draw(new Ellipse2D.Double(modeOvalX, modeOvalY, modeOvalDiameter,
			modeOvalDiameter));

		// Show the angle between this one and the other two
		// so we can see where the path is "pinched":
		final double h = imp.getWidth();
		final double centreX = imp.getWidth() / 2.0;
		final double centreY = imp.getHeight() / 2.0;
		final double halfAngle = angles[z] / 2;
		final double rightX = centreX + h * Math.sin(halfAngle);
		final double rightY = centreY - h * Math.cos(halfAngle);
		final double leftX = centreX + h * Math.sin(-halfAngle);
		final double leftY = centreX - h * Math.cos(halfAngle);
		g.setStroke(new BasicStroke(1));
		g.draw(new Line2D.Double(myScreenXDprecise(centreX), myScreenYDprecise(
			centreY), myScreenXDprecise(rightX), myScreenYDprecise(rightY)));
		g.draw(new Line2D.Double(myScreenXDprecise(centreX), myScreenYDprecise(
			centreY), myScreenXDprecise(leftX), myScreenYDprecise(leftY)));

		super.drawOverlay(g); // draw canvas label
		if (!syncWithTracingCanvas() || z == last_slice) {
			// fittedPath.setEditableNode(-1);
			return;
		}

		final Integer fittedIndex = indexToValidIndex.get(z);
		if (fittedIndex != null) {
			final int px = fittedPath.getXUnscaled(fittedIndex.intValue());
			final int py = fittedPath.getYUnscaled(fittedIndex.intValue());
			final int pz = fittedPath.getZUnscaled(fittedIndex.intValue());
			tracerPlugin.setZPositionAllPanes(px, py, pz);
			last_slice = z;
			last_editable_node = fittedIndex.intValue();
			fittedPath.setEditableNode(last_editable_node);
		}

	}

	private boolean syncWithTracingCanvas() {
		return (tracerPlugin.isUIready() && tracerPlugin
			.getUIState() == SNTUI.EDITING_MODE);
	}

	protected void showImage() {
		final DisplayService displayService = tracerPlugin.getContext().getService(
			DisplayService.class);
		final StackWindow win = new StackWindow(imp, this);
		while (magnification < 8)
			zoomIn(0, 0);
		win.addWindowListener(new WindowAdapter() {

			@Override
			public void windowActivated(final WindowEvent e) {
				if (syncWithTracingCanvas()) {
					tracerPlugin.selectPath(fittedPath, false);
				}
			}
		});
		displayService.createDisplay(win);
	}

}
