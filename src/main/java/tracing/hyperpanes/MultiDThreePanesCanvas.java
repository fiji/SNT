/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2006, 2007 Mark Longair */

/*
    This file is part of the ImageJ plugins "Simple Neurite Tracer"
    and "Three Pane Crop".

    The ImageJ plugins "Three Pane Crop" and "Simple Neurite Tracer"
    are free software; you can redistribute them and/or modify them
    under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    The ImageJ plugins "Simple Neurite Tracer" and "Three Pane Crop"
    are distributed in the hope that they will be useful, but WITHOUT
    ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
    License for more details.

    In addition, as a special exception, the copyright holders give
    you permission to combine this program with free software programs or
    libraries that are released under the Apache Public License. 

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package tracing.hyperpanes;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import ij.ImagePlus;
import ij.gui.ImageCanvas;
import stacks.PaneOwner;
import stacks.ThreePanes;

public class MultiDThreePanesCanvas extends ImageCanvas {

	private static final long serialVersionUID = 1L;
	protected PaneOwner owner;
	protected int plane;
	private double current_x, current_y, current_z;
	boolean draw_crosshairs;

	protected MultiDThreePanesCanvas(final ImagePlus imagePlus,
		final PaneOwner owner, final int plane)
	{
		super(imagePlus);
		this.owner = owner;
		this.plane = plane;
	}

	protected MultiDThreePanesCanvas(final ImagePlus imagePlus, final int plane) {
		super(imagePlus);
		this.plane = plane;
	}

	static public Object newThreePanesCanvas(final ImagePlus imagePlus,
		final PaneOwner owner, final int plane)
	{
		return new MultiDThreePanesCanvas(imagePlus, owner, plane);
	}

	public void setPaneOwner(final PaneOwner owner) {
		this.owner = owner;
	}

	protected void drawOverlay(final Graphics g) {

		if (draw_crosshairs) {

			if (plane == ThreePanes.XY_PLANE) {
				final int x = myScreenXD(current_x);
				final int y = myScreenYD(current_y);
				drawCrosshairs(g, Color.red, x, y);
			}
			else if (plane == ThreePanes.XZ_PLANE) {
				final int x = myScreenXD(current_x);
				final int y = myScreenYD(current_z);
				drawCrosshairs(g, Color.red, x, y);
			}
			else if (plane == ThreePanes.ZY_PLANE) {
				final int x = myScreenXD(current_z);
				final int y = myScreenYD(current_y);
				drawCrosshairs(g, Color.red, x, y);
			}
		}
	}

	@Override
	public void paint(final Graphics g) {
		super.paint(g);
		drawOverlay(g);
	}

	@Override
	public void mouseClicked(final MouseEvent e) {}

	@Override
	public void mouseMoved(final MouseEvent e) {

		super.mouseMoved(e);

		final double off_screen_x = offScreenX(e.getX());
		final double off_screen_y = offScreenY(e.getY());

		final boolean shift_key_down = (e.getModifiersEx() &
			InputEvent.SHIFT_DOWN_MASK) != 0;

		owner.mouseMovedTo((int) off_screen_x, (int) off_screen_y, plane,
			shift_key_down);
	}

	public void realZoom(final boolean in, final int x, final int y) {
		if (in) super.zoomIn(screenX(x), screenY(y));
		else super.zoomOut(screenX(x), screenY(y));
	}

	@Override
	public void zoomIn(final int sx, final int sy) {
		owner.zoom(true, offScreenX(sx), offScreenY(sy), plane);
	}

	@Override
	public void zoomOut(final int sx, final int sy) {
		owner.zoom(false, offScreenX(sx), offScreenY(sy), plane);
	}

	protected void drawCrosshairs(final Graphics g, final Color c,
		final int x_on_screen, final int y_on_screen)
	{
		g.setColor(c);
		final int hairLength = 8;
		g.drawLine(x_on_screen, y_on_screen + 1, x_on_screen, y_on_screen +
			(hairLength - 1));
		g.drawLine(x_on_screen, y_on_screen - 1, x_on_screen, y_on_screen -
			(hairLength - 1));
		g.drawLine(x_on_screen + 1, y_on_screen, x_on_screen + (hairLength - 1),
			y_on_screen);
		g.drawLine(x_on_screen - 1, y_on_screen, x_on_screen - (hairLength - 1),
			y_on_screen);
	}

	public void setCrosshairs(final double x, final double y, final double z,
		final boolean display)
	{
		current_x = x;
		current_y = y;
		current_z = z;
		draw_crosshairs = display;
	}

	/* These are the "a pixel is not a little square" versions of
	   these methods.  (It's not so easy to do anything about the
	   box filter reconstruction.)
	*/

	/** Converts a screen x-coordinate to an offscreen x-coordinate. */
	public int myOffScreenX(final int sx) {
		return srcRect.x + (int) ((sx - magnification / 2) / magnification);
	}

	/** Converts a screen y-coordinate to an offscreen y-coordinate. */
	public int myOffScreenY(final int sy) {
		return srcRect.y + (int) ((sy - magnification / 2) / magnification);
	}

	/**
	 * Converts a screen x-coordinate to a floating-point offscreen x-coordinate.
	 */
	public double myOffScreenXD(final int sx) {
		return srcRect.x + (sx - magnification / 2) / magnification;
	}

	/**
	 * Converts a screen y-coordinate to a floating-point offscreen y-coordinate.
	 */
	public double myOffScreenYD(final int sy) {
		return srcRect.y + (sy - magnification / 2) / magnification;
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public int myScreenX(final int ox) {
		return (int) Math.round((ox - srcRect.x) * magnification + magnification /
			2);
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public int myScreenY(final int oy) {
		return (int) Math.round((oy - srcRect.y) * magnification + magnification /
			2);
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public int myScreenXD(final double ox) {
		return (int) Math.round((ox - srcRect.x) * magnification + magnification /
			2);
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public int myScreenYD(final double oy) {
		return (int) Math.round((oy - srcRect.y) * magnification + magnification /
			2);
	}

}
