/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2020 Fiji developers.
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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.util.HashMap;

import ij.ImagePlus;
import ij.gui.ImageCanvas;

@SuppressWarnings("serial")
class NormalPlaneCanvas extends ImageCanvas {

	HashMap<Integer, Integer> indexToValidIndex = new HashMap<>();

	public NormalPlaneCanvas(final ImagePlus imp, final SimpleNeuriteTracer plugin, final double[] centre_x_positions,
			final double[] centre_y_positions, final double[] radiuses, final double[] scores,
			final double[] modeRadiuses, final double[] angles, final boolean[] valid, final Path fittedPath) {
		super(imp);
		tracerPlugin = plugin;
		this.centre_x_positions = centre_x_positions;
		this.centre_y_positions = centre_y_positions;
		this.radiuses = radiuses;
		this.scores = scores;
		this.modeRadiuses = modeRadiuses;
		this.angles = angles;
		this.valid = valid;
		this.fittedPath = fittedPath;
		for (int i = 0; i < scores.length; ++i)
			if (scores[i] > maxScore)
				maxScore = scores[i];
		int a = 0;
		for (int i = 0; i < valid.length; ++i) {
			if (valid[i]) {
				indexToValidIndex.put(i, a);
				++a;
			}
		}
	}

	double maxScore = -1;

	double[] centre_x_positions;
	double[] centre_y_positions;
	double[] radiuses;
	double[] scores;
	double[] modeRadiuses;
	boolean[] valid;
	double[] angles;

	Path fittedPath;

	SimpleNeuriteTracer tracerPlugin;

	/* Keep another Graphics for double-buffering... */

	private int backBufferWidth;
	private int backBufferHeight;

	private Graphics backBufferGraphics;
	private Image backBufferImage;

	private void resetBackBuffer() {

		if (backBufferGraphics != null) {
			backBufferGraphics.dispose();
			backBufferGraphics = null;
		}

		if (backBufferImage != null) {
			backBufferImage.flush();
			backBufferImage = null;
		}

		backBufferWidth = getSize().width;
		backBufferHeight = getSize().height;

		backBufferImage = createImage(backBufferWidth, backBufferHeight);
		backBufferGraphics = backBufferImage.getGraphics();
	}

	@Override
	public void paint(final Graphics g) {

		if (backBufferWidth != getSize().width || backBufferHeight != getSize().height || backBufferImage == null
				|| backBufferGraphics == null)
			resetBackBuffer();

		super.paint(backBufferGraphics);
		drawOverlay(backBufferGraphics);
		g.drawImage(backBufferImage, 0, 0, this);
	}

	int last_slice = -1;

	// FIXME: drawOverlay in ImageCanvas arrived after I wrote this code, I
	// think
	protected void drawOverlay(final Graphics g) {

		final int z = imp.getCurrentSlice() - 1;

		if (z != last_slice) {
			final Integer fittedIndex = indexToValidIndex.get(z);
			if (fittedIndex != null) {
				final int px = fittedPath.getXUnscaled(fittedIndex.intValue());
				final int py = fittedPath.getYUnscaled(fittedIndex.intValue());
				final int pz = fittedPath.getZUnscaled(fittedIndex.intValue());
				tracerPlugin.setSlicesAllPanes(px, py, pz);
				tracerPlugin.setCrosshair(px, py, pz);
				last_slice = z;
			}
		}

		if (valid[z])
			g.setColor(Color.RED);
		else
			g.setColor(Color.MAGENTA);

		SNT.log("radiuses[" + z + "] is: " + radiuses[z]);

		final int x_top_left = screenXD(centre_x_positions[z] - radiuses[z]);
		final int y_top_left = screenYD(centre_y_positions[z] - radiuses[z]);

		g.fillRect(screenXD(centre_x_positions[z]) - 2, screenYD(centre_y_positions[z]) - 2, 5, 5);

		final int diameter = screenXD(centre_x_positions[z] + radiuses[z])
				- screenXD(centre_x_positions[z] - radiuses[z]);

		g.drawOval(x_top_left, y_top_left, diameter, diameter);

		final double proportion = scores[z] / maxScore;
		final int drawToX = (int) (proportion * (imp.getWidth() - 1));
		if (valid[z])
			g.setColor(Color.GREEN);
		else
			g.setColor(Color.RED);
		g.fillRect(screenX(0), screenY(0), screenX(drawToX) - screenX(0), screenY(2) - screenY(0));

		final int modeOvalX = screenXD(imp.getWidth() / 2.0 - modeRadiuses[z]);
		final int modeOvalY = screenYD(imp.getHeight() / 2.0 - modeRadiuses[z]);
		final int modeOvalDiameter = screenXD(imp.getWidth() / 2.0 + modeRadiuses[z]) - modeOvalX;

		g.setColor(Color.YELLOW);
		g.drawOval(modeOvalX, modeOvalY, modeOvalDiameter, modeOvalDiameter);

		// Show the angle between this one and the other two
		// so we can see where the path is "pinched":
		g.setColor(Color.GREEN);
		final double h = (imp.getWidth() * 3) / 8.0;
		final double centreX = imp.getWidth() / 2.0;
		final double centreY = imp.getHeight() / 2.0;
		final double halfAngle = angles[z] / 2;
		final double rightX = centreX + h * Math.sin(halfAngle);
		final double rightY = centreY - h * Math.cos(halfAngle);
		final double leftX = centreX + h * Math.sin(-halfAngle);
		final double leftY = centreX - h * Math.cos(halfAngle);
		g.drawLine(screenXD(centreX), screenYD(centreY), screenXD(rightX), screenYD(rightY));
		g.drawLine(screenXD(centreX), screenYD(centreY), screenXD(leftX), screenYD(leftY));
	}

}
