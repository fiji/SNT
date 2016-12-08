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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import ij.IJ;
import ij.ImagePlus;
import stacks.ThreePanes;

@SuppressWarnings("serial")
public class InteractiveTracerCanvas extends TracerCanvas {

	static final boolean verbose = SimpleNeuriteTracer.verbose;

	boolean fillTransparent = false;

	Color transparentGreen = new Color(0, 128, 0, 128);

	public void setFillTransparent(final boolean transparent) {
		this.fillTransparent = transparent;
	}

	// -------------------------------------------------------------

	private final SimpleNeuriteTracer tracerPlugin;

	public SimpleNeuriteTracer getTracerPlugin() {
		return tracerPlugin;
	}

	InteractiveTracerCanvas(final ImagePlus imp, final SimpleNeuriteTracer plugin, final int plane,
			final PathAndFillManager pathAndFillManager) {
		super(imp, plugin, plane, pathAndFillManager);
		tracerPlugin = plugin;
		// SimpleNeuriteTracer.toastKeyListeners( IJ.getInstance(),
		// "InteractiveTracerCanvas constructor" );
		// addKeyListener( this );
	}

	private Path unconfirmedSegment;
	private Path currentPath;
	private boolean lastPathUnfinished;

	public void setPathUnfinished(final boolean unfinished) {
		this.lastPathUnfinished = unfinished;
	}

	public void setTemporaryPath(final Path path) {
		this.unconfirmedSegment = path;
	}

	public void setCurrentPath(final Path path) {
		this.currentPath = path;
	}

	public void toggleJustNearSlices() {
		just_near_slices = !just_near_slices;
	}

	public void fakeMouseMoved(final boolean shift_pressed, final boolean join_modifier_pressed) {
		tracerPlugin.mouseMovedTo(last_x_in_pane_precise, last_y_in_pane_precise, plane, shift_pressed,
				join_modifier_pressed);
	}

	public void clickAtMaxPoint() {
		final int[] p = new int[3];
		tracerPlugin.findPointInStack((int) Math.round(last_x_in_pane_precise),
				(int) Math.round(last_y_in_pane_precise), plane, p);
		tracerPlugin.clickAtMaxPoint(p[0], p[1], plane);
		tracerPlugin.setSlicesAllPanes(p[0], p[1], p[2]);
	}

	public void startShollAnalysis() {
		if (pathAndFillManager.anySelected()) {
			final double[] p = new double[3];
			tracerPlugin.findPointInStackPrecise(last_x_in_pane_precise, last_y_in_pane_precise, plane, p);
			final PointInImage pointInImage = pathAndFillManager.nearestJoinPointOnSelectedPaths(p[0], p[1], p[2]);
			new ShollAnalysisDialog("Sholl analysis for tracing of " + tracerPlugin.getImagePlus().getTitle(),
					pointInImage.x, pointInImage.y, pointInImage.z, pathAndFillManager, tracerPlugin.getImagePlus());
		} else {
			IJ.error("You must have a path selected in order to start Sholl analysis");
		}
	}

	public void selectNearestPathToMousePointer(final boolean addToExistingSelection) {

		if (pathAndFillManager.size() == 0) {
			IJ.error("There are no paths yet, so you can't select one with 'g'");
			return;
		}

		final double[] p = new double[3];
		tracerPlugin.findPointInStackPrecise(last_x_in_pane_precise, last_y_in_pane_precise, plane, p);

		final double diagonalLength = tracerPlugin.getStackDiagonalLength();

		/*
		 * Find the nearest point on any path - we'll select that path...
		 */

		final NearPoint np = pathAndFillManager.nearestPointOnAnyPath(p[0] * tracerPlugin.x_spacing,
				p[1] * tracerPlugin.y_spacing, p[2] * tracerPlugin.z_spacing, diagonalLength);

		if (np == null) {
			IJ.error("BUG: No nearby path was found within " + diagonalLength + " of the pointer");
			return;
		}

		final Path path = np.getPath();

		/*
		 * FIXME: in fact shift-G for multiple selections doesn't work, since in
		 * ImageJ that's a shortcut for taking a screenshot. Holding down
		 * control doesn't work since that's already used to restrict the
		 * cross-hairs to the selected path. Need to find some way around this
		 * ...
		 */

		tracerPlugin.selectPath(path, addToExistingSelection);
	}

	@Override
	public void mouseMoved(final MouseEvent e) {

		if (!tracerPlugin.isReady())
			return;

		final int rawX = e.getX();
		final int rawY = e.getY();

		last_x_in_pane_precise = myOffScreenXD(rawX);
		last_y_in_pane_precise = myOffScreenYD(rawY);

		final boolean mac = IJ.isMacintosh();

		final boolean shift_key_down = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
		final boolean joiner_modifier_down = mac ? ((e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0)
				: ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0);

		super.mouseMoved(e);

		tracerPlugin.mouseMovedTo(last_x_in_pane_precise, last_y_in_pane_precise, plane, shift_key_down,
				joiner_modifier_down);
	}

	double last_x_in_pane_precise = Double.MIN_VALUE;
	double last_y_in_pane_precise = Double.MIN_VALUE;

	@Override
	public void mouseEntered(final MouseEvent e) {

		if (!tracerPlugin.isReady())
			return;

		if (tracerPlugin.autoCanvasActivation) {
			imp.getWindow().toFront();
			requestFocusInWindow();
		}

	}

	@Override
	public void mouseClicked(final MouseEvent e) {

		if (!tracerPlugin.isReady())
			return;

		final int currentState = tracerPlugin.resultsDialog.getState();

		if (currentState == NeuriteTracerResultsDialog.LOADING || currentState == NeuriteTracerResultsDialog.SAVING
				|| currentState == NeuriteTracerResultsDialog.IMAGE_CLOSED) {

			// Do nothing

		} else if (currentState == NeuriteTracerResultsDialog.WAITING_FOR_SIGMA_POINT) {

			tracerPlugin.launchPaletteAround(myOffScreenX(e.getX()), myOffScreenY(e.getY()), imp.getCurrentSlice() - 1);

		} else if (currentState == NeuriteTracerResultsDialog.WAITING_FOR_SIGMA_CHOICE) {

			IJ.error("You must close the sigma palette to continue");

		} else if (tracerPlugin.setupTrace) {
			final boolean join = IJ.isMacintosh() ? e.isAltDown() : e.isControlDown();
			tracerPlugin.clickForTrace(myOffScreenXD(e.getX()), myOffScreenYD(e.getY()), plane, join);
		} else
			IJ.error("BUG: No operation chosen");
	}

	protected void drawSquare(final Graphics g, final PointInImage p, final Color fillColor, final Color edgeColor,
			final int side) {

		int x, y;

		if (plane == ThreePanes.XY_PLANE) {
			x = myScreenXD(p.x / tracerPlugin.x_spacing);
			y = myScreenYD(p.y / tracerPlugin.y_spacing);
		} else if (plane == ThreePanes.XZ_PLANE) {
			x = myScreenXD(p.x / tracerPlugin.x_spacing);
			y = myScreenYD(p.z / tracerPlugin.z_spacing);
		} else { // plane is ThreePanes.ZY_PLANE
			x = myScreenXD(p.z / tracerPlugin.z_spacing);
			y = myScreenYD(p.y / tracerPlugin.y_spacing);
		}

		final int rectX = x - side / 2;
		final int rectY = y - side / 2;

		g.setColor(fillColor);
		g.fillRect(rectX, rectY, side, side);

		if (edgeColor != null) {
			g.setColor(edgeColor);
			g.drawRect(rectX, rectY, side, side);
		}
	}

	@Override
	protected void drawOverlay(final Graphics g) {

		if (tracerPlugin.loading)
			return;

		final boolean drawDiametersXY = tracerPlugin.getDrawDiametersXY();
		final int sliceZeroIndexed = imp.getCurrentSlice() - 1;
		int eitherSideParameter = eitherSide;
		if (!just_near_slices)
			eitherSideParameter = -1;

		final FillerThread filler = tracerPlugin.filler;
		if (filler != null) {
			filler.setDrawingColors(fillTransparent ? transparentGreen : Color.GREEN,
					fillTransparent ? transparentGreen : Color.GREEN);
			filler.setDrawingThreshold(filler.getThreshold());
		}

		super.drawOverlay(g);

		final double magnification = getMagnification();
		int pixel_size = magnification < 1 ? 1 : (int) magnification;
		if (magnification >= 4)
			pixel_size = (int) (magnification / 2);

		final int spotDiameter = 5 * pixel_size;

		if (unconfirmedSegment != null) {
			unconfirmedSegment.drawPathAsPoints(this, g, Color.BLUE, plane, drawDiametersXY, sliceZeroIndexed,
					eitherSideParameter);

			if (unconfirmedSegment.endJoins != null) {

				final int n = unconfirmedSegment.size();
				final PointInImage p = unconfirmedSegment.getPointInImage(n - 1);
				drawSquare(g, p, Color.BLUE, Color.GREEN, spotDiameter);
			}
		}

		final Path currentPathFromTracer = tracerPlugin.getCurrentPath();

		if (currentPathFromTracer != null) {
			currentPathFromTracer.drawPathAsPoints(this, g, Color.RED, plane, drawDiametersXY, sliceZeroIndexed,
					eitherSideParameter);

			if (lastPathUnfinished && currentPath.size() == 0) {

				final PointInImage p = new PointInImage(tracerPlugin.last_start_point_x * tracerPlugin.x_spacing,
						tracerPlugin.last_start_point_y * tracerPlugin.y_spacing,
						tracerPlugin.last_start_point_z * tracerPlugin.z_spacing);

				Color edgeColour = null;
				if (currentPathFromTracer.startJoins != null)
					edgeColour = Color.GREEN;

				drawSquare(g, p, Color.BLUE, edgeColour, spotDiameter);
			}
		}

	}
}
