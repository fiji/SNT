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

package tracing.gui;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;

import features.HessianEvalueProcessor;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.gui.TextRoi;
import ij.process.FloatProcessor;
import stacks.ThreePaneCrop;
import tracing.SNT;
import util.Limits;

/**
 * This class implements SNT's v3 'Sigma wizard'. It relies heavily on java.awt
 * because it extends IJ1's StackWindow. It was ported from
 * {@link features.SigmaPalette} now deprecated.
 */
public class SigmaPalette extends Thread {

	/**
	 * Classes implementing this interface can monitor how users interact with the
	 * 'Sigma wizard'.
	 */
	public static interface SigmaPaletteListener {

		/**
		 * Notifies listeners that the user OKed the sigma wizard.
		 *
		 * @param sigma
		 *            the user's chosen value for sigma
		 * @param multiplier
		 *            the user's chosen value for the multiplier
		 */
		public void sigmaPaletteOKed(double sigma, double multiplier);

		/**
		 * Notifies listeners that the user canceled the sigma wizard.
		 */
		public void sigmaPaletteCanceled();
	}

	private class PaletteStackWindow extends StackWindow {

		private static final long serialVersionUID = 1L;
		private Label label;
		private ScrollbarWithLabel maxValueScrollbar;
		private boolean manuallyChangedAlready = false;

		public PaletteStackWindow(final ImagePlus imp, final ImageCanvas ic, final double defaultMax) {
			super(imp, ic);
			buildMaxValueScrollbar(defaultMax);
			add(maxValueScrollbar);
			buildLabel();
			add(label);
			add(buttonPanel());
			pack();
		}

		private Panel buttonPanel() {
			final Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.CENTER, 0, 0));
			final Button cButton = new Button("Cancel");
			final int width = (int) getLayout().preferredLayoutSize(this).getWidth() / 2 - (2 * ImageWindow.HGAP);
			final int height = 25;
			cButton.setPreferredSize(new Dimension(width, height));
			cButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					dismiss();
				}
			});
			buttonPanel.add(cButton);
			final Button aButton = new Button("Apply");
			aButton.setPreferredSize(new Dimension(width, height));
			aButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					apply();
				}
			});
			buttonPanel.add(aButton);
			return buttonPanel;
		}

		private void buildMaxValueScrollbar(final double defaultMaxValue) {
			maxValueScrollbar = new ScrollbarWithLabel(this, (int) defaultMaxValue, 1, 1, 601, '\u00D7');
			maxValueScrollbar.setFocusable(false); // prevents scroll bar from
													// blinking on Windows
			maxValueScrollbar.setUnitIncrement(1);
			maxValueScrollbar.setBlockIncrement(1);
			maxValueScrollbar.addAdjustmentListener(new AdjustmentListener() {

				@Override
				public void adjustmentValueChanged(final AdjustmentEvent e) {
					manuallyChangedAlready = true;
					final int newValue = e.getValue();
					maxChanged(newValue);
				}
			});
		}

		private void buildLabel() {
			label = new Label(" ", Label.CENTER);
			final int width = (int) getLayout().preferredLayoutSize(this).getWidth();
			label.setPreferredSize(new Dimension(width, 12));
			updateLabel();
		}

		private void updateLabel() {
			label.setText("\u03C3 = " + getSelectedSigma() + "; \u00D7 = "
					+ SNT.formatDouble(getSelectedMultiplier(), 2) + "; Maximum: " + (int) selectedMax);
		}

		private void maxChanged(final double newValue) {
			setMax(newValue);
			updateLabel();
		}

		@Override
		public void windowClosing(final WindowEvent e) {
			dismiss();
		}

		@Override
		public String createSubtitle() {
			String label = "Sigma preview grid: \u03C3=" + getMouseOverSigma();
			if (zSelector != null)
				label += "  z=" + zSelector.getValue();
			return label;
		}

	}

	private class PaletteCanvas extends ImageCanvas {

		private static final long serialVersionUID = 1L;
		private final int croppedWidth;
		private final int croppedHeight;
		private final int sigmasAcross;
		private final int sigmasDown;

		public PaletteCanvas(final ImagePlus imagePlus, final int croppedWidth, final int croppedHeight,
				final int sigmasAcross, final int sigmasDown) {
			super(imagePlus);
			this.croppedWidth = croppedWidth;
			this.croppedHeight = croppedHeight;
			this.sigmasAcross = sigmasAcross;
			this.sigmasDown = sigmasDown;
		}

		private int[] getTileXY(final MouseEvent e) {
			final int sx = e.getX();
			final int sy = e.getY();
			final int ox = offScreenX(sx);
			final int oy = offScreenY(sy);
			final int sigmaX = ox / (croppedWidth + 1);
			final int sigmaY = oy / (croppedHeight + 1);
			return new int[] { sigmaX, sigmaY };
		}

		private int sigmaIndexFromMouseEvent(final MouseEvent e) {
			final int[] sigmaXY = getTileXY(e);
			final int sigmaIndex = sigmaXY[1] * sigmasAcross + sigmaXY[0];
			if (sigmaIndex >= 0 && sigmaIndex < sigmaValues.length)
				return sigmaIndex;
			else
				return -1;
		}

		@Override
		public void mouseMoved(final MouseEvent e) {
			mouseMovedSigmaIndex = sigmaIndexFromMouseEvent(e);
			if (mouseMovedSigmaIndex >= 0) {
				paletteWindow.repaint(); // call createSubtitle()
				setOverlayLabel(mouseMovedSigmaIndex, getTileXY(e));
			}
		}

		@Override
		public void mouseReleased(final MouseEvent e) {
			final int sigmaIndex = sigmaIndexFromMouseEvent(e);
			if (sigmaIndex >= 0) {
				setSelectedSigmaIndex(sigmaIndex);
				paletteWindow.repaint(); // call createSubtitle()
				setOverlayLabel(sigmaIndex, getTileXY(e));
			}
		}

		/* Keep another Graphics for double-buffering: */
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
			drawOverlayGrid(backBufferGraphics);
			g.drawImage(backBufferImage, 0, 0, this);
		}

		private void drawOverlayGrid(final Graphics g) {
			g.setColor(java.awt.Color.MAGENTA);
			final int width = imp.getWidth();
			final int height = imp.getHeight();

			// Draw the vertical lines:
			for (int i = 0; i <= sigmasAcross; ++i) {
				final int x = i * (croppedWidth + 1);
				final int screen_x = screenX(x);
				g.drawLine(screen_x, screenY(0), screen_x, screenY(height - 1));
			}

			// Draw the horizontal lines:
			for (int j = 0; j <= sigmasDown; ++j) {
				final int y = j * (croppedHeight + 1);
				final int screen_y = screenY(y);
				g.drawLine(screenX(0), screen_y, screenX(width - 1), screen_y);
			}

			// If there's a selected sigma, highlight that in green:
			final int selectedSigmaIndex = getSelectedSigmaIndex();

			if (selectedSigmaIndex >= 0 && selectedSigmaIndex < sigmaValues.length) {
				g.setColor(java.awt.Color.GREEN);
				final int sigmaY = selectedSigmaIndex / sigmasAcross;
				final int sigmaX = selectedSigmaIndex % sigmasAcross;
				final int leftX = screenX(sigmaX * (croppedWidth + 1));
				final int rightX = screenX((sigmaX + 1) * (croppedWidth + 1));
				final int topY = screenY(sigmaY * (croppedHeight + 1));
				final int bottomY = screenY((sigmaY + 1) * (croppedHeight + 1));
				g.drawLine(leftX, topY, rightX, topY);
				g.drawLine(leftX, topY, leftX, bottomY);
				g.drawLine(leftX, bottomY, rightX, bottomY);
				g.drawLine(rightX, bottomY, rightX, topY);
			}
		}
	}

	private class KeyListener extends KeyAdapter {

		@Override
		public void keyPressed(final KeyEvent e) {
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
				dismiss();
			} else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
				apply();
			}
		}
	}

	private double[] sigmaValues;
	private int croppedWidth;
	private int croppedHeight;
	private int croppedDepth;
	private SigmaPaletteListener listener;
	private PaletteStackWindow paletteWindow;
	private ImagePlus paletteImage;
	private PaletteCanvas paletteCanvas;

	private int selectedSigmaIndex = 0;
	private int mouseMovedSigmaIndex = -1;
	private double selectedMax = Double.NaN;
	private int x_min, x_max, y_min, y_max, z_min, z_max;
	private HessianEvalueProcessor hep;
	private double defaultMax;
	private int sigmasAcross;
	private int sigmasDown;
	private int initial_z;
	private ImagePlus image;

	/**
	 * Attaches a listener to the current wizard.
	 *
	 * @param listener
	 *            the SigmaPaletteListener listener
	 */
	public void setListener(final SigmaPaletteListener listener) {
		this.listener = listener;
	}

	private void setMax(final double max) {
		selectedMax = max;
		if (paletteImage != null) {
			paletteImage.getProcessor().setMinAndMax(0, max);
			paletteImage.updateAndDraw();
		}
	}

	private double getMultiplierFromMax(final double max) {
		return 256 / max; // (max == 1) ? 1 : 256 / max;
	}

	private int getSelectedSigmaIndex() {
		return selectedSigmaIndex;
	}

	private double getSelectedSigma() {
		if (selectedSigmaIndex > -1 && selectedSigmaIndex < sigmaValues.length)
			return sigmaValues[selectedSigmaIndex];
		return Double.NaN;
	}

	private String getMouseOverSigma() {
		if (mouseMovedSigmaIndex > -1 && mouseMovedSigmaIndex < sigmaValues.length)
			return SNT.formatDouble(sigmaValues[mouseMovedSigmaIndex], 2);
		return "NaN";
	}

	private double getSelectedMultiplier() {
		return getMultiplierFromMax(selectedMax);
	}

	private void setSelectedSigmaIndex(final int selectedSigmaIndex) {
		this.selectedSigmaIndex = selectedSigmaIndex;
		paletteWindow.updateLabel();
		paletteImage.updateAndDraw();
	}

	private void setOverlayLabel(final int sigmaIndex, final int[] xyTile) {
		final String label = "\u03C3 = " + SNT.formatDouble(sigmaValues[sigmaIndex], 1);
		final TextRoi roi = new TextRoi(xyTile[0] * croppedWidth + 2, xyTile[1] * croppedHeight + 2, label);
		roi.setStrokeColor((getSelectedSigmaIndex() == sigmaIndex) ? Color.GREEN : Color.MAGENTA);
		roi.setAntialiased(true);
		paletteImage.setOverlay(new Overlay(roi));
	}

	/**
	 * Displays the Sigma wizard in a separate thread.
	 *
	 * @param image
	 *            the 2D/3D image serving data to the wizard
	 * @param x_min
	 *            image boundary for choice grid
	 * @param x_max
	 *            image boundary for choice grid
	 * @param y_min
	 *            image boundary for choice grid
	 * @param y_max
	 *            image boundary for choice grid
	 * @param z_min
	 *            image boundary for choice grid
	 * @param z_max
	 *            image boundary for choice grid
	 * @param hep
	 *            the HessianEvalueProcessor generating the preview
	 * @param sigmaValues
	 *            the desired range of sigma values for choice grid
	 * @param defaultMax
	 *            the default image maximum (setting the multiplier)
	 * @param sigmasAcross
	 *            the number of columns in choice grid
	 * @param sigmasDown
	 *            the number of rows in choice grid
	 * @param initial_z
	 *            the default z-position
	 */
	public void makePalette(final ImagePlus image, final int x_min, final int x_max, final int y_min, final int y_max,
			final int z_min, final int z_max, final HessianEvalueProcessor hep, final double[] sigmaValues,
			final double defaultMax, final int sigmasAcross, final int sigmasDown, final int initial_z) {

		if (sigmaValues.length > sigmasAcross * sigmasDown) {
			throw new IllegalArgumentException("A " + sigmasAcross + "x" + sigmasDown
					+ " layout is not large enough for " + sigmaValues + " + 1 images");
		}

		this.image = image;
		this.x_min = x_min;
		this.x_max = x_max;
		this.y_min = y_min;
		this.y_max = y_max;
		this.z_min = z_min;
		this.z_max = z_max;
		this.hep = hep;
		this.sigmaValues = sigmaValues;
		this.defaultMax = defaultMax;
		this.sigmasAcross = sigmasAcross;
		this.sigmasDown = sigmasDown;
		this.initial_z = initial_z;
		start();
	}

	private void dismiss() {
		if (listener != null)
			listener.sigmaPaletteCanceled();
		paletteWindow.dispose();
	}

	private void apply() {
		if (listener != null) {
			listener.sigmaPaletteOKed(getSelectedSigma(), getSelectedMultiplier());
		}
		paletteWindow.dispose();
	}

	private void copyIntoPalette(final ImagePlus smallImage, final ImagePlus paletteImage, final int offsetX,
			final int offsetY) {
		final int largerWidth = paletteImage.getWidth();
		final int depth = paletteImage.getStackSize();
		if (depth != smallImage.getStackSize())
			throw new IllegalArgumentException("In copyIntoPalette(), depths don't match");
		final int smallWidth = smallImage.getWidth();
		final int smallHeight = smallImage.getHeight();
		final ImageStack paletteStack = paletteImage.getStack();
		final ImageStack smallStack = smallImage.getStack();
		// Make sure the minimum and maximum are sensible in the small stack:
		for (int z = 0; z < depth; ++z) {
			final float[] smallPixels = (float[]) smallStack.getProcessor(z + 1).getPixels();
			final float[] palettePixels = (float[]) paletteStack.getProcessor(z + 1).getPixels();
			for (int y = 0; y < smallHeight; ++y) {
				final int smallIndex = y * smallWidth;
				System.arraycopy(smallPixels, smallIndex, palettePixels, (offsetY + y) * largerWidth + offsetX,
						smallWidth);
			}
		}
	}

	@Override
	public void run() {

		final ImagePlus cropped = ThreePaneCrop.performCrop(image, x_min, x_max, y_min, y_max, z_min, z_max, false);

		croppedWidth = (x_max - x_min) + 1;
		croppedHeight = (y_max - y_min) + 1;
		croppedDepth = (z_max - z_min) + 1;

		final int paletteWidth = croppedWidth * sigmasAcross + (sigmasAcross + 1);
		final int paletteHeight = croppedHeight * sigmasDown + (sigmasDown + 1);

		final ImageStack newStack = new ImageStack(paletteWidth, paletteHeight);
		for (int z = 0; z < croppedDepth; ++z) {
			final FloatProcessor fp = new FloatProcessor(paletteWidth, paletteHeight);
			newStack.addSlice("", fp);
		}
		paletteImage = new ImagePlus("Pick Sigma and Multiplier", newStack);
		setMax(defaultMax);

		paletteCanvas = new PaletteCanvas(paletteImage, croppedWidth, croppedHeight, sigmasAcross, sigmasDown);
		paletteWindow = new PaletteStackWindow(paletteImage, paletteCanvas, defaultMax);
		paletteCanvas.addKeyListener(new KeyListener());
		paletteCanvas.requestFocusInWindow(); // required to trigger keylistener
												// events
		paletteImage.setZ(initial_z - z_min + 1);
		setOverlayLabel(0, new int[] { 0, 0 });

		for (int sigmaIndex = 0; sigmaIndex < sigmaValues.length; ++sigmaIndex) {
			final int sigmaY = sigmaIndex / sigmasAcross;
			final int sigmaX = sigmaIndex % sigmasAcross;
			final int offsetX = sigmaX * (croppedWidth + 1) + 1;
			final int offsetY = sigmaY * (croppedHeight + 1) + 1;
			final double sigma = sigmaValues[sigmaIndex];
			hep.setSigma(sigma);
			final ImagePlus processed = hep.generateImage(cropped);
			if (!paletteWindow.manuallyChangedAlready) {
				final float[] limits = Limits.getStackLimits(processed);
				final int suggestedMax = (int) limits[1];
				paletteWindow.maxValueScrollbar.setValue(suggestedMax);
				paletteWindow.maxChanged(suggestedMax);
			}
			copyIntoPalette(processed, paletteImage, offsetX, offsetY);
			paletteImage.updateAndDraw();

		}

	}

}
