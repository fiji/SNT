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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import org.scijava.vecmath.Point2d;

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
	private String cursorText; // text to be rendered near cursor
	private String canvasText; // text to be rendered NW corner of canvas
	private Color annotationsColor;
	private boolean waveInteractionsToIJ;


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

	protected void drawOverlay(final Graphics2D g) {
		drawCanvasText(g, canvasText);
		final boolean draw_string = validString(cursorText);
		if (!draw_crosshairs && !draw_string) return;
		final Point2d pos = getCursorPos();
		g.setColor(getAnnotationsColor());
		if (draw_crosshairs) drawCrosshairs(g, pos.x, pos.y);
		if (draw_string) drawString(g, cursorText, (float)pos.x, (float)pos.y);
	}

	/**
	 * @return the current X,Y position of the mouse cursor
	 */
	public Point2d getCursorPos() {
		double x,y;
		if (plane == MultiDThreePanes.XY_PLANE) {
			x = myScreenXDprecise(current_x);
			y = myScreenYDprecise(current_y);
		}
		else if (plane == MultiDThreePanes.XZ_PLANE) {
			x = myScreenXDprecise(current_x);
			y = myScreenYDprecise(current_z);
		}
		else if (plane == MultiDThreePanes.ZY_PLANE) {
			x = myScreenXDprecise(current_z);
			y = myScreenYDprecise(current_y);
		}
		else throw new IllegalArgumentException("Unknow pane");
		return new Point2d(x,y);
	}

	protected Graphics2D getGraphics2D(final Graphics g) {
		final Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return g2;
	}

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

	protected void drawCrosshairs(final Graphics2D g,
		final double x_on_screen, final double y_on_screen)
	{
		final int hairLength = 8;
		g.draw(new Line2D.Double(x_on_screen, y_on_screen, x_on_screen, y_on_screen + hairLength));
		g.draw(new Line2D.Double(x_on_screen, y_on_screen - 1, x_on_screen, y_on_screen - hairLength));
		g.draw(new Line2D.Double(x_on_screen + 1, y_on_screen, x_on_screen + hairLength, y_on_screen));
		g.draw(new Line2D.Double(x_on_screen - 1, y_on_screen, x_on_screen - hairLength, y_on_screen));
	}

	private void drawString(final Graphics2D g, final String str,
		final float x_on_screen, final float y_on_screen)
	{
		g.drawString(str, x_on_screen, y_on_screen);
	}

	private void drawCanvasText(final Graphics2D g, final String text) {
		if (!validString(text)) return;
		final int edge = 4;
		final double size = Math.max(9, Math.min(18 * magnification, 30));
		final Font font = new Font("SansSerif", Font.PLAIN, 18).deriveFont(
			(float) size);
		final FontMetrics fm = getFontMetrics(font);
		final double w = fm.stringWidth(text) + edge;
		final double h = fm.getHeight() + edge;
		g.setColor(new Color(120, 120, 120, 100));
		g.fill(new Rectangle2D.Double(0, 0, w, h));
		g.setFont(font);
		g.setColor(getAnnotationsColor());
		g.drawString(text, edge / 2, edge / 2 + fm.getAscent());
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
		updateCursor(x,y,z);
		setDrawCrosshairs(display);
	}

	public void updateCursor(final double x, final double y, final double z)
	{
		current_x = x;
		current_y = y;
		current_z = z;
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
		return (int) Math.round(myScreenXDprecise(ox));
	}

	/**
	 * Converts an offscreen x-coordinate to a screen x-coordinate with
	 * floating-point precision.
	 */
	public double myScreenXDprecise(final double ox) {
		return (ox - srcRect.x) * magnification + magnification / 2;
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public int myScreenYD(final double oy) {
		return (int) Math.round(myScreenYDprecise(oy));
	}

	/**
	 * Converts an offscreen y-coordinate to a screen y-coordinate with
	 * floating-point precision.
	 */
	public double myScreenYDprecise(final double oy) {
		return (oy - srcRect.y) * magnification + magnification / 2;
	}

	public void restoreDefaultCursor() {
		setCursorText(null);
		setCursor(ImageCanvas.defaultCursor);
	}

	/**
	 * Sets the string to be rendered on canvas' upper left corner.
	 *
	 * @param label the string to be displayed
	 */
	public void setCanvasLabel(final String label) {
		canvasText = label;
	}

	/**
	 * Sets the string to be appended to the current cursor.
	 *
	 * @param cursorText the string to be displayed around the cursor
	 */
	public void setCursorText(final String cursorText) {
		this.cursorText = cursorText;
	}

	public void setAnnotationsColor(Color color) {
		this.annotationsColor = color;
	}

	public Color getAnnotationsColor() {
		return (annotationsColor == null) ? Color.RED : annotationsColor;
	}

	/**
	 * @return whether SNT is being notified of mouse/key events
	 */
	public boolean isEventsDisabled() {
		return waveInteractionsToIJ;
	}

	/**
	 * Sets whether mouse and key events should be waived back to IJ.
	 *
	 * @param disable If true, SNT will not be notified of mouse/keyboard events
	 */
	public void disableEvents(boolean disable) {
		waveInteractionsToIJ = disable;
	}

	private boolean validString(String string) {
		return string != null && !string.trim().isEmpty();
	}

	public void setDrawCrosshairs(final boolean drawCrosshairs) {
		draw_crosshairs = drawCrosshairs;
	}

}
