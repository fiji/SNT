/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2017 Fiji developers.
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

package tracing.hyperpanes;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import ij.ImagePlus;
import ij.gui.ImageCanvas;
import tracing.hyperpanes.PaneOwner;
import tracing.hyperpanes.MultiDThreePanes;

public class MultiDThreePanesCanvas extends ImageCanvas {

	private static final long serialVersionUID = 1L;
	protected PaneOwner owner;
	protected int plane;
	private double current_x, current_y, current_z;
	private boolean draw_crosshairs;
	private String cursorText;
	private Color cursorAnnotationsColor;


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

		final boolean draw_string = validCursorText();
		if (!draw_crosshairs && !draw_string) return;
		int x, y;
		if (plane == MultiDThreePanes.XY_PLANE) {
			x = myScreenXD(current_x);
			y = myScreenYD(current_y);
		}
		else if (plane == MultiDThreePanes.XZ_PLANE) {
			x = myScreenXD(current_x);
			y = myScreenYD(current_z);
		}
		else if (plane == MultiDThreePanes.ZY_PLANE) {
			x = myScreenXD(current_z);
			y = myScreenYD(current_y);
		}
		else return;
		g.setColor(getCursorAnnotationsColor());
		if (draw_crosshairs) drawCrosshairs(g, x, y);
		if (draw_string) drawString(g, cursorText, x, y);
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

	protected void drawCrosshairs(final Graphics g,
		final int x_on_screen, final int y_on_screen)
	{
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

	private void drawString(final Graphics g, final String str,
		final int x_on_screen, final int y_on_screen)
	{
		final Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		g2d.drawString(str, x_on_screen, y_on_screen);
	}

	public void updatePosition(final double x, final double y, final double z)
	{
		current_x = x;
		current_y = y;
		current_z = z;
	}

	@Deprecated
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

	public void restoreDefaultCursor() {
		setCursorText(null);
		setCursor(ImageCanvas.defaultCursor);
	}

	/**
	 * Sets the string to be appended to the current cursor.
	 *
	 * @param cursorText the string to be displayed around the cursor
	 */
	public void setCursorText(final String cursorText) {
		this.cursorText = cursorText;
	}

	public void setCursorAnnotationsColor(Color color) {
		this.cursorAnnotationsColor = color;
	}

	public Color getCursorAnnotationsColor() {
		return (cursorAnnotationsColor == null) ? Color.RED : cursorAnnotationsColor;
	}

	private boolean validCursorText() {
		return cursorText != null && !cursorText.trim().isEmpty();
	}

	public void setDrawCrosshairs(final boolean drawCrosshairs) {
		draw_crosshairs = drawCrosshairs;
	}

}
