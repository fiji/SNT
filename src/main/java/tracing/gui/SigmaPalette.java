
package tracing.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Label;
import java.awt.Scrollbar;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;

import features.HessianEvalueProcessor;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.gui.StackWindow;
import ij.gui.TextRoi;
import ij.process.FloatProcessor;
import stacks.ThreePaneCrop;
import tracing.SNT;
import util.Limits;

public class SigmaPalette extends Thread {

	public static interface SigmaPaletteListener {

		public void newSigmaSelected(double sigma);

		public void newMaximum(double max);

		public void sigmaPaletteClosing();
	}

	private static class PaletteStackWindow extends StackWindow {

		private static final long serialVersionUID = 1L;
		private final SigmaPalette owner;
		private Label label;
		private Scrollbar maxValueScrollbar;
		private boolean manuallyChangedAlready = false;

		public PaletteStackWindow(final ImagePlus imp, final ImageCanvas ic,
			final SigmaPalette owner, final double defaultMax)
		{
			super(imp, ic);
			this.owner = owner;
			addExtraScrollbar(defaultMax);
		}

		private void addExtraScrollbar(final double defaultMaxValue) {
			add(new Label(" ")); // spacer
			label = new Label("Adjust max. (sets multiplier): 0000000");
			add(label);
			updateLabel(defaultMaxValue);
			maxValueScrollbar = new Scrollbar(Scrollbar.HORIZONTAL,
				(int) defaultMaxValue, 1, 1, 350);
			maxValueScrollbar.addAdjustmentListener(new AdjustmentListener() {

				@Override
				public void adjustmentValueChanged(final AdjustmentEvent e) {
					manuallyChangedAlready = true;
					final int newValue = e.getValue();
					maxChanged(newValue);
				}
			});
			add(maxValueScrollbar);
			pack();
		}

		private void updateLabel(final double maxValue) {
			label.setText("Adjust max. (sets multiplier): " + (int)maxValue + "   ");
		}

		private void maxChanged(final double newValue) {
			updateLabel(newValue);
			if (owner != null) {
				owner.setMax(newValue);
			}
		}

		@Override
		public void windowClosing(final WindowEvent e) {
			if (owner != null && owner.listener != null) {
				owner.listener.sigmaPaletteClosing();
			}
			super.windowClosing(e);
		}

		@Override
		public String createSubtitle() {
			return "Sigma preview grid [" + SNT.formatDouble(owner.sigmaValues[0], 1) +
				" - " + SNT.formatDouble(owner.sigmaValues[owner.sigmaValues.length -
					1], 1) + "]";
		}

	}

	private static class PaletteCanvas extends ImageCanvas {

		private static final long serialVersionUID = 1L;
		private final SigmaPalette owner;
		private final int croppedWidth;
		private final int croppedHeight;
		private final int sigmasAcross;
		private final int sigmasDown;

		public PaletteCanvas(final ImagePlus imagePlus, final SigmaPalette owner,
			final int croppedWidth, final int croppedHeight, final int sigmasAcross,
			final int sigmasDown)
		{
			super(imagePlus);
			this.owner = owner;
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
			final int sigmaX = ox / (owner.croppedWidth + 1);
			final int sigmaY = oy / (owner.croppedHeight + 1);
			return new int[] { sigmaX, sigmaY };
		}

		private int sigmaIndexFromMouseEvent(final MouseEvent e) {
			final int[] sigmaXY = getTileXY(e);
			final int sigmaIndex = sigmaXY[1] * sigmasAcross + sigmaXY[0];
			if (sigmaIndex >= 0 && sigmaIndex < owner.sigmaValues.length)
				return sigmaIndex;
			else return -1;
		}

		@Override
		public void mouseMoved(final MouseEvent e) {
			final int sigmaIndex = sigmaIndexFromMouseEvent(e);
			if (sigmaIndex >= 0) {
				setOverlayLabel(Color.MAGENTA, sigmaIndex, getTileXY(e));
			}
		}

		@Override
		public void mouseClicked(final MouseEvent e) {
			final int oldSelectedSigmaIndex = owner.getSelectedSigmaIndex();
			final int sigmaIndex = sigmaIndexFromMouseEvent(e);
			if (sigmaIndex >= 0) {
				if (sigmaIndex == oldSelectedSigmaIndex) owner.setSelectedSigmaIndex(
					-1);
				else {
					owner.setSelectedSigmaIndex(sigmaIndex);
					setOverlayLabel(Color.GREEN, sigmaIndex, getTileXY(e));
				}
			}
		}

		private void setOverlayLabel(final Color color, final int sigmaIndex,
			final int[] xyTile)
		{
			final String label = "\u03C3 = " + SNT.formatDouble(
				owner.sigmaValues[sigmaIndex], 1);
			final TextRoi roi = new TextRoi(xyTile[0] * owner.croppedWidth + 2,
				xyTile[1] * owner.croppedHeight + 2, label);
			roi.setStrokeColor(color);
			roi.setAntialiased(true);
			owner.paletteImage.setOverlay(new Overlay(roi));
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

			if (backBufferWidth != getSize().width ||
				backBufferHeight != getSize().height || backBufferImage == null ||
				backBufferGraphics == null) resetBackBuffer();

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
			final int selectedSigmaIndex = owner.getSelectedSigmaIndex();

			if (selectedSigmaIndex >= 0 &&
				selectedSigmaIndex < owner.sigmaValues.length)
			{
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

	private double[] sigmaValues;
	private int croppedWidth;
	private int croppedHeight;
	private int croppedDepth;
	private SigmaPaletteListener listener;
	private ImagePlus paletteImage;
	private int selectedSigmaIndex = -1;
	private int x_min, x_max, y_min, y_max, z_min, z_max;
	private HessianEvalueProcessor hep;
	private double defaultMax;
	private int sigmasAcross;
	private int sigmasDown;
	private int initial_z;
	private ImagePlus image;

	public void setListener(final SigmaPaletteListener newListener) {
		listener = newListener;
	}

	public void setMax(final double max) {
		if (paletteImage != null) {
			paletteImage.getProcessor().setMinAndMax(0, max);
			paletteImage.updateAndDraw();
		}
		if (listener != null) listener.newMaximum(max);
	}

	private int getSelectedSigmaIndex() {
		return selectedSigmaIndex;
	}

	private void setSelectedSigmaIndex(final int selectedSigmaIndex) {
		this.selectedSigmaIndex = selectedSigmaIndex;
		if (listener != null && selectedSigmaIndex >= 0) listener.newSigmaSelected(
			sigmaValues[selectedSigmaIndex]);
		paletteImage.updateAndDraw();
	}

	public void makePalette(final ImagePlus image, final int x_min,
		final int x_max, final int y_min, final int y_max, final int z_min,
		final int z_max, final HessianEvalueProcessor hep,
		final double[] sigmaValues, final double defaultMax, final int sigmasAcross,
		final int sigmasDown, final int initial_z)
	{
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

	private void copyIntoPalette(final ImagePlus smallImage,
		final ImagePlus paletteImage, final int offsetX, final int offsetY)
	{
		final int largerWidth = paletteImage.getWidth();
		final int depth = paletteImage.getStackSize();
		if (depth != smallImage.getStackSize()) throw new RuntimeException(
			"In copyIntoPalette(), depths don't match");
		final int smallWidth = smallImage.getWidth();
		final int smallHeight = smallImage.getHeight();
		final ImageStack paletteStack = paletteImage.getStack();
		final ImageStack smallStack = smallImage.getStack();
		// Make sure the minimum and maximum are sensible in the small stack:
		for (int z = 0; z < depth; ++z) {
			final float[] smallPixels = (float[]) smallStack.getProcessor(z + 1)
				.getPixels();
			final float[] palettePixels = (float[]) paletteStack.getProcessor(z + 1)
				.getPixels();
			for (int y = 0; y < smallHeight; ++y) {
				final int smallIndex = y * smallWidth;
				System.arraycopy(smallPixels, smallIndex, palettePixels, (offsetY + y) *
					largerWidth + offsetX, smallWidth);
			}
		}
	}

	@Override
	public void run() {

		if (sigmaValues.length > sigmasAcross * sigmasDown) {
			throw new IllegalArgumentException("A " + sigmasAcross + "x" +
				sigmasDown + " layout is not large enough for " + sigmaValues +
				" + 1 images");
		}

		final ImagePlus cropped = ThreePaneCrop.performCrop(image, x_min, x_max,
			y_min, y_max, z_min, z_max, false);

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

		final PaletteCanvas paletteCanvas = new PaletteCanvas(paletteImage, this,
			croppedWidth, croppedHeight, sigmasAcross, sigmasDown);
		final PaletteStackWindow paletteWindow = new PaletteStackWindow(
			paletteImage, paletteCanvas, this, defaultMax);

		paletteImage.setSlice((initial_z - z_min) + 1);

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
