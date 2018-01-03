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

package tracing.legacy;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import ij.gui.ColorChooser;
import tracing.SimpleNeuriteTracer;
import tracing.gui.SWCColor;

@Deprecated
@SuppressWarnings("all")
public class PathColorsCanvas extends Canvas implements MouseListener {

	SimpleNeuriteTracer plugin;

	public PathColorsCanvas(final SimpleNeuriteTracer plugin, final int width, final int height) {
		this.plugin = plugin;
		addMouseListener(this);
		setSize(width, height);
		selectedColor = plugin.selectedColor;
		deselectedColor = plugin.deselectedColor;
	}

	private Color selectedColor;
	private Color deselectedColor;

	@Override
	public void update(final Graphics g) {
		paint(g);
	}

	@Override
	public void paint(final Graphics g) {
		final int width = getWidth();
		final int height = getHeight();
		final int leftWidth = width / 2;
		g.setColor(selectedColor);
		g.fillRect(0, 0, leftWidth, height);
		g.setColor(contrastColor(selectedColor));
		g.drawString("Selected", 3, height - 3);
		g.setColor(deselectedColor);
		g.fillRect(leftWidth, 0, width - leftWidth, height);
		g.setColor(contrastColor(deselectedColor));
		g.drawString("Deselected", leftWidth + 3, height - 3);
	}

	private Color contrastColor(final Color c) {
		return SWCColor.contrastColor(c);
	}

	@Override
	public void mouseClicked(final MouseEvent e) {
		final int x = e.getX();
		ColorChooser chooser;
		if (x < getWidth() / 2) {
			chooser = new ColorChooser("Colour for selected paths", selectedColor, false);
			final Color newColor = chooser.getColor();
			if (newColor == null)
				return;
			selectedColor = newColor;
			plugin.setSelectedColor(newColor);
		} else {
			chooser = new ColorChooser("Colour for deselected paths", deselectedColor, false);
			final Color newColor = chooser.getColor();
			if (newColor == null)
				return;
			deselectedColor = newColor;
			plugin.setDeselectedColor(newColor);
		}
		repaint();
	}

	@Override
	public void mouseEntered(final MouseEvent e) {
	}

	@Override
	public void mouseExited(final MouseEvent e) {
	}

	@Override
	public void mousePressed(final MouseEvent e) {
	}

	@Override
	public void mouseReleased(final MouseEvent e) {
	}
}
