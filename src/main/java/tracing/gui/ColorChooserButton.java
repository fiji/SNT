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

package tracing.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * This class generates a JButton holding a color chooser. It is based on
 * https://stackoverflow.com/a/30433662 released under cc-by-sa
 */
public class ColorChooserButton extends JButton {

	private static final long serialVersionUID = 1L;
	private Color current;
	private ColorChangedListener listener;

	public ColorChooserButton(final Color c) {
		this(c, null);
	}

	public ColorChooserButton(final Color c, final String label) {
		this(c, label, 1d);
	}

	public ColorChooserButton(final Color c, final String label,
		double scaleFactor)
	{
		this(c, label, scaleFactor, SwingConstants.LEFT);
	}
	public ColorChooserButton(final Color c, final String label,
		double scaleFactor, int textPosition)
	{
		super(label);
		if (scaleFactor != 1d) {
			setFont(getFont().deriveFont((float) (getFont().getSize() *
				scaleFactor)));
			final Insets margin = getMargin();
			if (margin != null) {
				setMargin(new Insets((int) (margin.top * scaleFactor),
					(int) (margin.left * scaleFactor), (int) (margin.bottom *
						scaleFactor), (int) (margin.right * scaleFactor)));
			}
		}
		setSelectedColor(c);
		final JButton thisButton = this;
		setVerticalTextPosition(SwingConstants.CENTER);
		setHorizontalTextPosition(textPosition);
		setIconTextGap(getFontMetrics(getFont()).stringWidth("  "));
		addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				if (arg0 == null) return;
				final String title = (thisButton.getName() == null) ? "Choose new color"
					: "New " + thisButton.getName();
				final Color newColor = new GuiUtils(SwingUtilities.getRoot(thisButton))
					.getColor(title, getSelectedColor(), "HSB");
				setSelectedColor(newColor);

			}
		});
	}

	public Color getSelectedColor() {
		return current;
	}

	public void setSelectedColor(final Color newColor) {
		setSelectedColor(newColor, true);
	}

	public void setSelectedColor(final Color newColor, final boolean notify) {
		if (newColor == null) return;
		current = newColor;
		final int h = getFontMetrics(getFont()).getAscent();
		setIcon(createIcon(current, h * 2, h));
		repaint();
		if (notify && listener != null) listener.colorChanged(newColor);
	}

	public void addColorChangedListener(final ColorChangedListener listener) {
		this.listener = listener;
	}

	private ImageIcon createIcon(final Color main, final int width,
		final int height)
	{
		final BufferedImage image = new BufferedImage(width, height,
			java.awt.image.BufferedImage.TYPE_INT_RGB);
		final Graphics2D graphics = image.createGraphics();
		graphics.setColor(main);
		graphics.fillRect(0, 0, width, height);
		graphics.setXORMode(Color.DARK_GRAY);
		graphics.drawRect(0, 0, width - 1, height - 1);
		image.flush();
		final ImageIcon icon = new ImageIcon(image);
		return icon;
	}

	public interface ColorChangedListener2 {

		public void colorChanged(Color newColor);
	}

	public static void main(final String[] args) {
		final javax.swing.JFrame f = new javax.swing.JFrame();
		f.setLayout(new java.awt.FlowLayout());
		final ColorChooserButton colorChooser1 = new ColorChooserButton(Color.WHITE,
			"Selected");
		colorChooser1.setName("Color for Selected Paths");
		colorChooser1.addColorChangedListener(new ColorChangedListener() {

			@Override
			public void colorChanged(final Color newColor) {
				System.out.print(colorChooser1);
				System.out.print(newColor);
			}
		});
		f.add(colorChooser1);
		final ColorChooserButton colorChooser2 = new ColorChooserButton(Color.RED,
			"Deselected");
		colorChooser2.setName("Color for Deselected Paths");
		colorChooser2.addColorChangedListener(new ColorChangedListener() {

			@Override
			public void colorChanged(final Color newColor) {
				System.out.print(colorChooser2);
				System.out.print(newColor);
			}
		});
		f.add(colorChooser2);

		f.pack();
		f.setVisible(true);
	}
}
