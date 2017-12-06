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

import java.awt.image.ColorModel;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageCanvas;
import ij.gui.StackWindow;
import ij.plugin.RGBStackConverter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import tracing.gui.GuiUtils;
import tracing.hyperpanes.PaneOwner;

public class MultiDThreePanes implements PaneOwner {

	public static final int XY_PLANE = 0; // constant z
	public static final int XZ_PLANE = 1; // constant y
	public static final int ZY_PLANE = 2; // constant x

	protected ImagePlus xy;
	protected ImagePlus xz;
	protected ImagePlus zy;
	protected MultiDThreePanesCanvas xy_canvas;
	protected MultiDThreePanesCanvas xz_canvas;
	protected MultiDThreePanesCanvas zy_canvas;
	protected ImageCanvas original_xy_canvas;
	protected StackWindow xy_window;
	protected StackWindow xz_window;
	protected StackWindow zy_window;
	protected boolean single_pane;

	public MultiDThreePanes() {}

	public void findPointInStack(final int x_in_pane, final int y_in_pane,
		final int plane, final int[] point)
	{

		switch (plane) {
			case XY_PLANE:
				point[0] = x_in_pane;
				point[1] = y_in_pane;
				point[2] = xy.getZ() - 1;
				break;
			case XZ_PLANE:
				point[0] = x_in_pane;
				point[1] = xz.getZ() - 1;
				point[2] = y_in_pane;
				break;
			case ZY_PLANE:
				point[0] = zy.getZ() - 1;
				point[1] = y_in_pane;
				point[2] = x_in_pane;
				break;
		}
	}

	public void findPointInStackPrecise(final double x_in_pane,
		final double y_in_pane, final int plane, final double[] point)
	{

		switch (plane) {
			case XY_PLANE:
				point[0] = x_in_pane;
				point[1] = y_in_pane;
				point[2] = xy.getZ() - 1;
				break;
			case XZ_PLANE:
				point[0] = x_in_pane;
				point[1] = xz.getZ() - 1;
				point[2] = y_in_pane;
				break;
			case ZY_PLANE:
				point[0] = zy.getZ() - 1;
				point[1] = y_in_pane;
				point[2] = x_in_pane;
				break;
		}

	}

	public MultiDThreePanesCanvas createCanvas(final ImagePlus imagePlus,
		final int plane)
	{
		return new MultiDThreePanesCanvas(imagePlus, this, plane);
	}

	@Override
	public void mouseMovedTo(final int off_screen_x, final int off_screen_y,
		final int in_plane, final boolean shift_down)
	{

		final int point[] = new int[3];

		findPointInStack(off_screen_x, off_screen_y, in_plane, point);

		xy_canvas.updatePosition(point[0], point[1], point[2]);
		if (!single_pane) {
			xz_canvas.updatePosition(point[0], point[1], point[2]);
			zy_canvas.updatePosition(point[0], point[1], point[2]);
		}

		if (shift_down) setSlicesAllPanes(point[0], point[1], point[2]);
	}

	@Override
	public void zoom(final boolean in, final int off_screen_x,
		final int off_screen_y, final int in_plane)
	{
		final int point[] = new int[3];
		findPointInStack(off_screen_x, off_screen_y, in_plane, point);
		xy_canvas.realZoom(in, point[0], point[1]);
		if (!single_pane) {
			xz_canvas.realZoom(in, point[0], point[2]);
			zy_canvas.realZoom(in, point[2], point[1]);
		}
	}

	public void setSlicesAllPanes(final int new_x, final int new_y,
		final int new_z)
	{

		xy.setZ(new_z + 1);
		if (!single_pane) {
			xz.setZ(new_y + 1);
			zy.setZ(new_x + 1);
		}
	}

	public void repaintAllPanes() {

		xy_canvas.repaint();
		if (!single_pane) {
			xz_canvas.repaint();
			zy_canvas.repaint();
		}
	}

	public void closeAndReset() {
		if (!single_pane) {
			zy.close();
			xz.close();
		}
		if (original_xy_canvas != null && xy != null && xy.getImage() != null) {
			xy_window = new StackWindow(xy, original_xy_canvas);
			xy_canvas.add(ij.Menus.getPopupMenu());
		}
	}

	/**
	 * Assesses whether enough memory exists to analyze an image. A dialog message
	 * warning the user is displayed if assessment was negative.
	 *
	 * @param imagePlus the image to be loaded by the plugin
	 * @param memoryMultipleNeeded the memory cutoff multiplier. E.g., 3 means
	 *          that the user will need 3x the memory used by the supplied
	 *          ImagePlus in order to not be warned about insufficient memory
	 * @return true, if sufficient memory was detected, false otherwise
	 */
	public boolean memoryCheck(final ImagePlus imagePlus,
		final int memoryMultipleNeeded)
	{

		final long sizeOfImagePlus = imagePlus.getWidth() * imagePlus.getHeight() *
			imagePlus.getStackSize() * (imagePlus.getBitDepth() / 8);

		final long bytesNeededEstimate = (memoryMultipleNeeded + 1) *
			sizeOfImagePlus;

		System.gc();
		final long maxMemory = Runtime.getRuntime().maxMemory();

		final boolean sufficientMem = bytesNeededEstimate > maxMemory;
		if (!sufficientMem) {
			error("It looks as if the amount of memory required for the " +
				"three pane view (" + (bytesNeededEstimate / (1024 * 1024)) +
				"MiB) exceeds the maximum memory available (" + (maxMemory / (1024 *
					1024)) + "MiB)");
		}
		return sufficientMem;
	}

	public static String imageTypeToString(final int type) {
		String result;
		switch (type) {
			case ImagePlus.GRAY8:
				result = "GRAY8 (8-bit grayscale (unsigned))";
				break;
			case ImagePlus.GRAY16:
				result = "GRAY16 (16-bit grayscale (unsigned))";
				break;
			case ImagePlus.GRAY32:
				result = "GRAY32 (32-bit floating-point grayscale)";
				break;
			case ImagePlus.COLOR_256:
				result = "COLOR_256 (8-bit indexed color)";
				break;
			case ImagePlus.COLOR_RGB:
				result = "COLOR_RGB (32-bit RGB color)";
				break;
			default:
				result = "Unknown (value: " + type + ")";
				break;
		}
		return result;
	}

	public void reloadZYXZpanes(final int frame) {
		if (single_pane) return; // nothing to reload
		if (xy == null) throw new IllegalArgumentException(
			"reload() called withou initialization");
		initialize(xy, frame);
		repaintAllPanes();
	}

	public void initialize(final ImagePlus imagePlus) {
		initialize(imagePlus, 1);
	}

//	public void initialize(final ImagePlus imagePlus, final int frame) {
//		initialize(imagePlus, frame, true);
//	}

	public void initialize(final ImagePlus imagePlus, final int frame)
	{
		if (frame > imagePlus.getNFrames()) throw new IllegalArgumentException(
			"Invalid frame: " + frame);
		xy = imagePlus;
		final boolean rgb_panes = xy.getNChannels() > 1 || xy.isComposite();
		final int width = xy.getWidth();
		final int height = xy.getHeight();
		final int stackSize = xy.getNSlices();
		int type;
		original_xy_canvas = (imagePlus.getWindow() == null) ? null : imagePlus
			.getWindow().getCanvas();

		ImagePlus xyMonoChannel;

		if (rgb_panes) {
			xyMonoChannel = xy.createHyperStack(null, 1, xy.getNSlices(), xy
				.getNFrames(), 24);
			final RGBStackConverter converter = new RGBStackConverter();
			converter.convertHyperstack(xy, xyMonoChannel);
			type = ImagePlus.COLOR_RGB;
		}
		else {
			xyMonoChannel = xy;
			type = xy.getType();
		}
		final ImageStack xy_stack = xyMonoChannel.getStack();

		ColorModel cm = null;

		// FIXME: should we save the LUT for other image types?
		if (type == ImagePlus.COLOR_256) cm = xy_stack.getColorModel();

		if (!single_pane) {

			final String title = (xy.getNFrames() > 0) ? "[T" + frame + "] " + xy
				.getShortTitle() : xy.getShortTitle();
				final int zy_width = stackSize;
				final int zy_height = height;
				final ImageStack zy_stack = new ImageStack(zy_width, zy_height);

				final int xz_width = width;
				final int xz_height = stackSize;
				final ImageStack xz_stack = new ImageStack(xz_width, xz_height);

				/* Just load in the complete stack for simplicity's
				 * sake... */

				final byte[][] slices_data_b = new byte[stackSize][];
				final int[][] slices_data_i = new int[stackSize][];
				final float[][] slices_data_f = new float[stackSize][];
				final short[][] slices_data_s = new short[stackSize][];

				for (int z = 0; z < stackSize; ++z) {
					final int pos = xyMonoChannel.getStackIndex(1, z + 1, frame);
					switch (type) {
						case ImagePlus.GRAY8:
						case ImagePlus.COLOR_256:
							slices_data_b[z] = (byte[]) xy_stack.getPixels(pos);
							break;
						case ImagePlus.GRAY16:
							slices_data_s[z] = (short[]) xy_stack.getPixels(pos);
							break;
						case ImagePlus.COLOR_RGB:
							slices_data_i[z] = (int[]) xy_stack.getPixels(pos);
							break;
						case ImagePlus.GRAY32:
							slices_data_f[z] = (float[]) xy_stack.getPixels(pos);
							break;
					}
				}

				// Create the ZY slices:
				switch (type) {

					case ImagePlus.GRAY8:
					case ImagePlus.COLOR_256:

						for (int x_in_original = 0; x_in_original < width; ++x_in_original) {

							final byte[] sliceBytes = new byte[zy_width * zy_height];

							for (int z_in_original =
									0; z_in_original < stackSize; ++z_in_original)
							{
								for (int y_in_original =
										0; y_in_original < height; ++y_in_original)
								{

									final int x_in_left = z_in_original;
									final int y_in_left = y_in_original;

									sliceBytes[y_in_left * zy_width + x_in_left] =
											slices_data_b[z_in_original][y_in_original * width +
											                             x_in_original];
								}
							}

							final ByteProcessor bp = new ByteProcessor(zy_width, zy_height);
							bp.setPixels(sliceBytes);
							zy_stack.addSlice(null, bp);
							showStatus(x_in_original, width, "Generating XZ planes...");
						}
						break;

					case ImagePlus.GRAY16:

						for (int x_in_original = 0; x_in_original < width; ++x_in_original) {

							final short[] sliceShorts = new short[zy_width * zy_height];

							for (int z_in_original =
									0; z_in_original < stackSize; ++z_in_original)
							{
								for (int y_in_original =
										0; y_in_original < height; ++y_in_original)
								{

									final int x_in_left = z_in_original;
									final int y_in_left = y_in_original;

									sliceShorts[y_in_left * zy_width + x_in_left] =
											slices_data_s[z_in_original][y_in_original * width +
											                             x_in_original];
								}
							}

							final ShortProcessor sp = new ShortProcessor(zy_width, zy_height);
							sp.setPixels(sliceShorts);
							zy_stack.addSlice(null, sp);
							showStatus(x_in_original, width, "Generating XZ planes...");
						}
						break;

					case ImagePlus.COLOR_RGB:

						for (int x_in_original = 0; x_in_original < width; ++x_in_original) {

							final int[] sliceInts = new int[zy_width * zy_height];

							for (int z_in_original =
									0; z_in_original < stackSize; ++z_in_original)
							{
								for (int y_in_original =
										0; y_in_original < height; ++y_in_original)
								{

									final int x_in_left = z_in_original;
									final int y_in_left = y_in_original;

									sliceInts[y_in_left * zy_width + x_in_left] =
											slices_data_i[z_in_original][y_in_original * width +
											                             x_in_original];
								}
							}

							final ColorProcessor cp = new ColorProcessor(zy_width, zy_height);
							cp.setPixels(sliceInts);
							zy_stack.addSlice(null, cp);
							showStatus(x_in_original, width, "Generating XZ planes...");
						}
						break;

					case ImagePlus.GRAY32:

						for (int x_in_original = 0; x_in_original < width; ++x_in_original) {

							final float[] sliceFloats = new float[zy_width * zy_height];

							for (int z_in_original =
									0; z_in_original < stackSize; ++z_in_original)
							{
								for (int y_in_original =
										0; y_in_original < height; ++y_in_original)
								{

									final int x_in_left = z_in_original;
									final int y_in_left = y_in_original;

									sliceFloats[y_in_left * zy_width + x_in_left] =
											slices_data_f[z_in_original][y_in_original * width +
											                             x_in_original];
								}
							}

							final FloatProcessor fp = new FloatProcessor(zy_width, zy_height);
							fp.setPixels(sliceFloats);
							zy_stack.addSlice(null, fp);
							showStatus(x_in_original, width, "Generating XZ planes...");
						}
						break;

				}

				if (type == ImagePlus.COLOR_256) {
					if (cm != null) {
						zy_stack.setColorModel(cm);
					}
				}

				if (zy == null)
					zy = new ImagePlus("ZY " + title, zy_stack);
				zy.setStack(zy_stack);
				zy.setTitle("ZY " + title);

				// Create the XZ slices:
				switch (type) {

					case ImagePlus.GRAY8:
					case ImagePlus.COLOR_256:

						for (int y_in_original = 0; y_in_original < height; ++y_in_original) {

							final byte[] sliceBytes = new byte[xz_width * xz_height];

							for (int z_in_original =
									0; z_in_original < stackSize; ++z_in_original)
							{

								// Now we can copy a complete row from
								// the original image to the XZ slice:

								final int y_in_top = z_in_original;

								System.arraycopy(slices_data_b[z_in_original], y_in_original *
									width, sliceBytes, y_in_top * xz_width, width);

							}

							final ByteProcessor bp = new ByteProcessor(xz_width, xz_height);
							bp.setPixels(sliceBytes);
							xz_stack.addSlice(null, bp);

							showStatus(y_in_original, width, "Generating ZY planes...");
						}
						break;

					case ImagePlus.GRAY16:

						for (int y_in_original = 0; y_in_original < height; ++y_in_original) {

							final short[] sliceShorts = new short[xz_width * xz_height];

							for (int z_in_original =
									0; z_in_original < stackSize; ++z_in_original)
							{

								// Now we can copy a complete row from
								// the original image to the XZ slice:

								final int y_in_top = z_in_original;

								System.arraycopy(slices_data_s[z_in_original], y_in_original *
									width, sliceShorts, y_in_top * xz_width, width);

							}

							final ShortProcessor sp = new ShortProcessor(xz_width, xz_height);
							sp.setPixels(sliceShorts);
							xz_stack.addSlice(null, sp);

							showStatus(y_in_original, width, "Generating ZY planes...");
						}
						break;

					case ImagePlus.COLOR_RGB:

						for (int y_in_original = 0; y_in_original < height; ++y_in_original) {

							final int[] sliceInts = new int[xz_width * xz_height];

							for (int z_in_original =
									0; z_in_original < stackSize; ++z_in_original)
							{

								// Now we can copy a complete row from
								// the original image to the XZ slice:

								final int y_in_top = z_in_original;

								System.arraycopy(slices_data_i[z_in_original], y_in_original *
									width, sliceInts, y_in_top * xz_width, width);

							}

							final ColorProcessor cp = new ColorProcessor(xz_width, xz_height);
							cp.setPixels(sliceInts);
							xz_stack.addSlice(null, cp);

							showStatus(y_in_original, width, "Generating ZY planes...");
						}
						break;

					case ImagePlus.GRAY32:

						for (int y_in_original = 0; y_in_original < height; ++y_in_original) {

							final float[] sliceFloats = new float[xz_width * xz_height];

							for (int z_in_original =
									0; z_in_original < stackSize; ++z_in_original)
							{

								// Now we can copy a complete row from
								// the original image to the XZ slice:

								final int y_in_top = z_in_original;

								System.arraycopy(slices_data_f[z_in_original], y_in_original *
									width, sliceFloats, y_in_top * xz_width, width);

							}

							final FloatProcessor fp = new FloatProcessor(xz_width, xz_height);
							fp.setPixels(sliceFloats);
							xz_stack.addSlice(null, fp);

							showStatus(y_in_original, width, "Generating ZY planes...");
						}
						break;

				}

				if (type == ImagePlus.COLOR_256) {
					if (cm != null) {
						xz_stack.setColorModel(cm);
					}
				}
				if (xz == null)
					xz = new ImagePlus("XZ " + title, xz_stack);
				xz.setStack(xz_stack);
				xz.setTitle("XZ " + title);
				showStatus(0, 0, "Generating ZY planes...");

		}

		System.gc();

		if (xy_canvas == null)
			xy_canvas = createCanvas(xy, XY_PLANE);
		if (!single_pane && xz_canvas == null)
			xz_canvas = createCanvas(xz, XZ_PLANE);
		if (!single_pane && zy_canvas == null)
			zy_canvas = createCanvas(zy, ZY_PLANE);

		if (xy_window == null)
			xy_window = new StackWindow(xy, xy_canvas);
		if (!single_pane && xz_window == null)
			xz_window = new StackWindow(xz, xz_canvas);
		if (!single_pane && zy_window == null)
			zy_window = new StackWindow(zy, zy_canvas);

	}

	/* If a user clicks on a point in one of the panes, it's
	   sometimes useful to consider all points in the column that
	   point is in perpendicular to the plane.  This method
	   returns the x, y and z coordinates of all the points in
	   that column. */

	public int[][] findAllPointsAlongLine(final int x_in_pane,
		final int y_in_pane, final int plane)
	{
		int n = -1;

		switch (plane) {
			case XY_PLANE:
				n = xy.getNSlices();
				break;
			case XZ_PLANE:
				n = xz.getNSlices();
				break;
			case ZY_PLANE:
				n = zy.getNSlices();
				break;
		}

		final int[][] result = new int[n][3];

		switch (plane) {
			case XY_PLANE:
				for (int z = 0; z < n; ++z) {
					result[z] = new int[] { x_in_pane, y_in_pane, z };
				}
				break;
			case XZ_PLANE:
				for (int y = 0; y < n; ++y) {
					result[y] = new int[] { x_in_pane, y, y_in_pane };
				}
				break;
			case ZY_PLANE:
				for (int x = 0; x < n; ++x) {
					result[x] = new int[] { x, y_in_pane, x_in_pane };
				}
				break;
		}

		return result;
	}

	/** IDE debug method **/
	public static void main(final String[] args) {
		if (ij.IJ.getInstance() == null) new ij.ImageJ();
		final String path = "/Applications/IJ/samples/Spindly-GFP.zip";
		final ImagePlus imp = ij.IJ.openImage(path);
		// imp.setActiveChannels("01");
		final MultiDThreePanes mdp = new MultiDThreePanes();
		mdp.single_pane = false;
		mdp.initialize(imp, 20);
		mdp.reloadZYXZpanes(30);
	}

	@Override
	public void showStatus(int progress, int maximum, String message) {
		// Do nothing by default
	}

	@Override
	public void error(String error) {
		GuiUtils.errorPrompt(error);
	}

}
