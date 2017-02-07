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

import java.applet.Applet;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.util.Vector;
import java.util.concurrent.Callable;

import org.scijava.vecmath.Color3f;

import client.ArchiveClient;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.YesNoCancelDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import ij3d.Content;
import ij3d.ContentConstants;
import ij3d.Image3DUniverse;
import util.BatchOpener;
import util.RGBToLuminance;

/* Note on terminology:

      "traces" files are made up of "paths".  Paths are non-branching
      sequences of adjacent points (including diagonals) in the image.
      Branches and joins are supported by attributes of paths that
      specify that they begin on (or end on) other paths.

 */

public class Simple_Neurite_Tracer extends SimpleNeuriteTracer implements PlugIn {

	/* These will be set by SNTPrefs */
	protected boolean forceGrayscale;
	protected boolean look4oofFile;
	protected boolean look4tubesFile;
	protected boolean look4tracesFile;

	@Override
	public void run(final String ignoredArguments) {

		/*
		 * The useful macro options are:
		 *
		 * imagefilename=<FILENAME> tracesfilename=<FILENAME> use_3d
		 * use_three_pane
		 */

		final String macroOptions = Macro.getOptions();

		String macroImageFilename = null;
		String macroTracesFilename = null;

		if (macroOptions != null) {
			macroImageFilename = Macro.getValue(macroOptions, "imagefilename", null);
			macroTracesFilename = Macro.getValue(macroOptions, "tracesfilename", null);
		}

		final Applet applet = IJ.getApplet();
		if (applet != null) {
			archiveClient = new ArchiveClient(applet, macroOptions);
		}

		if (archiveClient != null)
			archiveClient.closeChannelsWithTag("nc82");

		try {

			ImagePlus currentImage = null;
			if (macroImageFilename == null) {
				currentImage = IJ.getImage();
			} else {
				currentImage = BatchOpener.openFirstChannel(macroImageFilename);
				if (currentImage == null) {
					SNT.error("Opening the image file specified in the macro parameters (" + macroImageFilename
							+ ") failed.");
					return;
				}
				currentImage.show();
			}

			if (currentImage == null) {
				SNT.error("There's no current image to trace.");
				return;
			}

			// Check this isn't a composite image or hyperstack:
			if (currentImage.getNFrames() > 1) {
				SNT.error("This plugin only works with single 2D/3D image,\nnot multiple images in a time series.");
				return;
			}

			if (currentImage.getNChannels() > 1) {
				SNT.error("This plugin only works with single channel images: Use\n"
						+ "'Image>Color>Split Channels' and choose the channel\n"
						+ "to be traced, then re-run the plugin");
				return;
			}

			if (currentImage.getStackSize() == 1)
				singleSlice = true;

			imageType = currentImage.getType();

			if (imageType == ImagePlus.COLOR_RGB) {
				final YesNoCancelDialog queryRGB = new YesNoCancelDialog(IJ.getInstance(), "Convert RGB image",
						"Convert this RGB image to an 8-bit luminance image first?\n \n"
								+ "(If you want to trace a particular channel instead: Cancel,\n"
								+ "run \"Image>Color>Split Channels\", and re-run the plugin).");
				if (!queryRGB.yesPressed()) {
					return;
				}

				currentImage = RGBToLuminance.convertToLuminance(currentImage);
				currentImage.show();
				imageType = currentImage.getType();
			} else if (!singleSlice && (imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32)) {
				final YesNoCancelDialog query16to8 = new YesNoCancelDialog(IJ.getInstance(), "Convert 16 bit image?",
						"This image is " + currentImage.getBitDepth() + "-bit. You can trace it using "
								+ currentImage.getBitDepth() + "-bit values, but if you want\n"
								+ "to use the 3D viewer during tracing, you must convert it to 8-bit first.\n \n"
								+ "The 3D Viewer is a powerful visualization tool but it may sub-perform on\n"
								+ "slower machines. Note that you can trace this image without any conversion:\n"
								+ "Once it has been traced, its tracings can be imported and renderered in\n"
								+ "the 3D viewer at any later time. \n \n"
								+ "Use 3D Viewer right now and convert stack to 8-bit?");
				if (query16to8.yesPressed()) {
					new StackConverter(currentImage).convertToGray8();
					imageType = currentImage.getType();
				} else if (query16to8.cancelPressed())
					return;
			}

			width = currentImage.getWidth();
			height = currentImage.getHeight();
			depth = currentImage.getStackSize();

			final Calibration calibration = currentImage.getCalibration();
			if (calibration != null) {
				x_spacing = calibration.pixelWidth;
				y_spacing = calibration.pixelHeight;
				z_spacing = calibration.pixelDepth;
				spacing_units = calibration.getUnits();
				if (spacing_units == null || spacing_units.length() == 0)
					spacing_units = "" + calibration.getUnit();
			}

			final SNTPrefs prefs = new SNTPrefs(this);
			prefs.loadStartupPrefs();

			final GenericDialog gd = new GenericDialog("Simple Neurite Tracer (v" + SNT.VERSION + ")");
			gd.setInsets(0, 0, 0);
			final Font font = new Font("SansSerif", Font.BOLD, 12);
			gd.setInsets(0, 0, 0);
			gd.addMessage("Tracing of " + currentImage.getTitle() + (singleSlice ? " (2D):" : " (3D):"), font);
			gd.addCheckbox("Enforce non-inverted grayscale LUT", forceGrayscale);

			String extraMemoryNeeded3P = " (will use an extra: ";
			final int bitDepth = currentImage.getBitDepth();
			final int byteDepth = bitDepth == 24 ? 4 : bitDepth / 8;
			final long megaBytesExtra3P = (((long) width) * height * depth * byteDepth * 2) / (1024 * 1024);
			extraMemoryNeeded3P += megaBytesExtra3P + "MiB of memory)";
			gd.addCheckbox("Use_three_pane view? " + extraMemoryNeeded3P, singleSlice ? false : !single_pane);
			gd.addCheckbox("Look_for_Tubeness \".tubes.tif\" pre-processed file?", look4tubesFile);
			gd.addCheckbox("Look_for_Tubular_Geodesics \".oof.ext\" pre-processed file?",
					singleSlice ? false : look4oofFile);
			gd.addCheckbox("Look_for_previously_traced data (\".traces\" file)?", look4tracesFile);
			boolean showed3DViewerOption = false;
			Image3DUniverse universeToUse = null;
			String[] choices3DViewer = null;
			int defaultResamplingFactor = 1;
			int resamplingFactor = 1;

			if (!singleSlice) {
				final boolean java3DAvailable = haveJava3D();
				defaultResamplingFactor = guessResamplingFactor();
				resamplingFactor = defaultResamplingFactor;
				if (!java3DAvailable) {
					final String message = "(Java3D doesn't seem to be available, so no 3D viewer option is available.)";
					if (verbose)
						SNT.log(message);
					gd.addMessage(message);
				} else if (currentImage.getBitDepth() != 8) {
					final String message = "(3D viewer option is only currently available for 8 bit images)";
					if (verbose)
						SNT.log(message);
					gd.addMessage(message);
				} else {
					showed3DViewerOption = true;
					choices3DViewer = new String[Image3DUniverse.universes.size() + 2];
					final String no3DViewerString = "No 3D view";
					final String useNewString = "Create New 3D Viewer";
					choices3DViewer[choices3DViewer.length - 2] = useNewString;
					choices3DViewer[choices3DViewer.length - 1] = no3DViewerString;
					for (int i = 0; i < choices3DViewer.length - 2; ++i) {
						final String contentsString = Image3DUniverse.universes.get(i).allContentsString();
						String shortContentsString;
						if (contentsString.length() == 0)
							shortContentsString = "[Empty]";
						else
							shortContentsString = contentsString.substring(0,
									Math.min(40, contentsString.length() - 1));
						choices3DViewer[i] = "3D viewer [" + i + "] containing " + shortContentsString;
					}
					gd.addMessage(""); // spacer
					gd.addChoice("Choice of 3D Viewer:", choices3DViewer,
							(use3DViewer) ? useNewString : no3DViewerString);
					gd.addNumericField("Resampling factor:", defaultResamplingFactor, 0, 3,
							"(can be left at the default)");
				}
			}
			// Disable options not suitable to 2D images
			final Vector<?> cbxs = gd.getCheckboxes();
			((java.awt.Checkbox) cbxs.get(1)).setEnabled(!singleSlice); // three
																		// pane
			((java.awt.Checkbox) cbxs.get(3)).setEnabled(!singleSlice); // tubular
																		// geodesics

			gd.showDialog();
			if (gd.wasCanceled())
				return;

			forceGrayscale = gd.getNextBoolean();
			single_pane = !gd.getNextBoolean();
			look4tubesFile = gd.getNextBoolean();
			look4oofFile = gd.getNextBoolean();
			look4tracesFile = gd.getNextBoolean();
			prefs.saveStartupPrefs();

			use3DViewer = !singleSlice && showed3DViewerOption;
			if (use3DViewer) {
				final String chosenViewer = gd.getNextChoice();
				int chosenIndex;
				for (chosenIndex = 0; chosenIndex < choices3DViewer.length; ++chosenIndex)
					if (choices3DViewer[chosenIndex].equals(chosenViewer))
						break;
				if (chosenIndex == choices3DViewer.length - 2) {
					use3DViewer = true;
					universeToUse = null;
				} else if (chosenIndex == choices3DViewer.length - 1) {
					use3DViewer = false;
					universeToUse = null;
				} else {
					use3DViewer = true;
					universeToUse = Image3DUniverse.universes.get(chosenIndex);
				}
				final double rawResamplingFactor = gd.getNextNumber();
				resamplingFactor = (int) Math.round(rawResamplingFactor);
				if (resamplingFactor < 1) {
					SNT.error("The resampling factor " + rawResamplingFactor + " was invalid - \n"
							+ "using the default of " + defaultResamplingFactor + " instead.");
					resamplingFactor = defaultResamplingFactor;
				}
			}

			// Turn it grey, since I find that helpful:
			if (forceGrayscale) {
				final ImageProcessor imageProcessor = currentImage.getProcessor();
				final byte[] reds = new byte[256];
				final byte[] greens = new byte[256];
				final byte[] blues = new byte[256];
				for (int i = 0; i < 256; ++i) {
					reds[i] = (byte) i;
					greens[i] = (byte) i;
					blues[i] = (byte) i;
				}
				final IndexColorModel cm = new IndexColorModel(8, 256, reds, greens, blues);
				imageProcessor.setColorModel(cm);
				if (currentImage.getStackSize() > 1)
					currentImage.getStack().setColorModel(cm);
				currentImage.updateAndRepaintWindow();
			}

			pathAndFillManager = new PathAndFillManager(this);
			file_info = currentImage.getOriginalFileInfo();

			// Look for a possible .oof.nrrd file:
			if (!singleSlice && look4oofFile && file_info != null) {
				final String beforeExtension = stripExtension(file_info.fileName);
				if (beforeExtension != null) {
					final File possibleOOFFile = new File(file_info.directory, beforeExtension + ".oof.nrrd");
					if (possibleOOFFile.exists()) {
						oofFile = possibleOOFFile;
					}
				}
			}

			if (look4tubesFile && file_info != null) {
				final String originalFileName = file_info.fileName;
				if (verbose)
					SNT.log("originalFileName was: " + originalFileName);
				if (originalFileName != null) {
					final int lastDot = originalFileName.lastIndexOf(".");
					if (lastDot > 0) {
						final String tubesFileName = stripExtension(originalFileName) + ".tubes.tif";
						ImagePlus tubenessImage = null;
						final File tubesFile = new File(file_info.directory, tubesFileName);
						if (verbose)
							SNT.log("Testing for the existence of " + tubesFile.getAbsolutePath());
						if (tubesFile.exists()) {
							final long megaBytesExtra = (((long) width) * height * depth * 4) / (1024 * 1024);
							final String extraMemoryNeeded = megaBytesExtra + "MiB";
							final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), "Confirm",
									"A tubeness file (" + tubesFile.getName() + ") exists.  Load this file?\n"
											+ "(This would use an extra " + extraMemoryNeeded + " of memory.)");
							if (d.cancelPressed())
								return;
							else if (d.yesPressed()) {
								IJ.showStatus("Loading tubes file.");
								tubenessImage = BatchOpener.openFirstChannel(tubesFile.getAbsolutePath());
								if (verbose)
									SNT.log("Loaded the tubeness file");
								if (tubenessImage == null) {
									SNT.error("Failed to load tubes image from " + tubesFile.getAbsolutePath()
											+ " although it existed");
									return;
								}
								if (tubenessImage.getType() != ImagePlus.GRAY32) {
									SNT.error("The tubeness file must be a 32 bit float image - "
											+ tubesFile.getAbsolutePath() + " was not.");
									return;
								}
								final int depth = tubenessImage.getStackSize();
								final ImageStack tubenessStack = tubenessImage.getStack();
								tubeness = new float[depth][];
								for (int z = 0; z < depth; ++z) {
									final FloatProcessor fp = (FloatProcessor) tubenessStack.getProcessor(z + 1);
									tubeness[z] = (float[]) fp.getPixels();
								}
							}
						}
					}
				}
			}

			final Overlay currentImageOverlay = currentImage.getOverlay();
			initialize(currentImage);
			xy.setOverlay(currentImageOverlay);

			xy_tracer_canvas = (InteractiveTracerCanvas) xy_canvas;
			xz_tracer_canvas = (InteractiveTracerCanvas) xz_canvas;
			zy_tracer_canvas = (InteractiveTracerCanvas) zy_canvas;

			prefs.loadPluginPrefs();
			setupTrace = true;

			final Simple_Neurite_Tracer thisPlugin = this;
			resultsDialog = SwingSafeResult.getResult(new Callable<NeuriteTracerResultsDialog>() {
				@Override
				public NeuriteTracerResultsDialog call() {
					return new NeuriteTracerResultsDialog("Tracing for: " + xy.getShortTitle(), thisPlugin,
							applet != null);
				}
			});

			/*
			 * FIXME: this could be changed to add 'this', and move the small
			 * implementation out of NeuriteTracerResultsDialog into this class.
			 */
			pathAndFillManager.addPathAndFillListener(this);

			if ((x_spacing == 0.0) || (y_spacing == 0.0) || (z_spacing == 0.0)) {

				SNT.error("One dimension of the calibration information was zero: (" + x_spacing + "," + y_spacing + ","
						+ z_spacing + ")");
				return;

			}

			{
				final ImageStack s = xy.getStack();
				switch (imageType) {
				case ImagePlus.GRAY8:
				case ImagePlus.COLOR_256:
					slices_data_b = new byte[depth][];
					for (int z = 0; z < depth; ++z)
						slices_data_b[z] = (byte[]) s.getPixels(z + 1);
					stackMin = 0;
					stackMax = 255;
					break;
				case ImagePlus.GRAY16:
					slices_data_s = new short[depth][];
					for (int z = 0; z < depth; ++z)
						slices_data_s[z] = (short[]) s.getPixels(z + 1);
					IJ.showStatus("Finding stack minimum / maximum");
					for (int z = 0; z < depth; ++z) {
						for (int y = 0; y < height; ++y)
							for (int x = 0; x < width; ++x) {
								final short v = slices_data_s[z][y * width + x];
								if (v < stackMin)
									stackMin = v;
								if (v > stackMax)
									stackMax = v;
							}
						IJ.showProgress(z / (float) depth);
					}
					IJ.showProgress(1.0);
					break;
				case ImagePlus.GRAY32:
					slices_data_f = new float[depth][];
					for (int z = 0; z < depth; ++z)
						slices_data_f[z] = (float[]) s.getPixels(z + 1);
					IJ.showStatus("Finding stack minimum / maximum");
					for (int z = 0; z < depth; ++z) {
						for (int y = 0; y < height; ++y)
							for (int x = 0; x < width; ++x) {
								final float v = slices_data_f[z][y * width + x];
								if (v < stackMin)
									stackMin = v;
								if (v > stackMax)
									stackMax = v;
							}
						IJ.showProgress(z / (float) depth);
					}
					IJ.showProgress(1.0);
					break;
				}
			}

			final QueueJumpingKeyListener xy_listener = new QueueJumpingKeyListener(this, xy_tracer_canvas);
			setAsFirstKeyListener(xy_tracer_canvas, xy_listener);
			setAsFirstKeyListener(xy_window, xy_listener);

			if (!single_pane) {

				xz.setDisplayRange(xy.getDisplayRangeMin(), xy.getDisplayRangeMax());
				zy.setDisplayRange(xy.getDisplayRangeMin(), xy.getDisplayRangeMax());

				final QueueJumpingKeyListener xz_listener = new QueueJumpingKeyListener(this, xz_tracer_canvas);
				setAsFirstKeyListener(xz_tracer_canvas, xz_listener);
				setAsFirstKeyListener(xz_window, xz_listener);

				final QueueJumpingKeyListener zy_listener = new QueueJumpingKeyListener(this, zy_tracer_canvas);
				setAsFirstKeyListener(zy_tracer_canvas, zy_listener);
				setAsFirstKeyListener(zy_window, zy_listener);

			}

			if (use3DViewer) {

				boolean reusing;
				if (universeToUse == null) {
					reusing = false;
					univ = new Image3DUniverse(512, 512);
				} else {
					reusing = true;
					univ = universeToUse;
				}
				univ.setUseToFront(false);
				univ.addUniverseListener(pathAndFillManager);
				if (!reusing) {
					univ.show();
				}
				final boolean[] channels = { true, true, true };

				final String title = "Image for tracing [" + currentImage.getTitle() + "]";
				final String contentName = univ.getSafeContentName(title);
				// univ.resetView();
				final Content c = univ.addContent(xy, new Color3f(Color.white), contentName, 10, // threshold
						channels, resamplingFactor, ContentConstants.VOLUME);
				c.setLocked(true);
				c.setTransparency(0.5f);
				if (!reusing)
					univ.resetView();
				univ.setAutoAdjustView(false);
				final PointSelectionBehavior psb = new PointSelectionBehavior(univ, this);
				univ.addInteractiveBehavior(psb);
				univ.getWindow().addWindowListener(new WindowAdapter() {
					@Override
					public void windowClosed(final WindowEvent e) {
						resultsDialog.threeDViewerMenuItem.setEnabled(false);
						resultsDialog.colorImageChoice.setEnabled(false);
						resultsDialog.paths3DChoice.setEnabled(false);
					}
				});
			}

			resultsDialog.displayOnStarting();

			File tracesFileToLoad = null;
			final boolean macroLoading = macroTracesFilename != null;
			if (macroLoading) {
				tracesFileToLoad = new File(macroTracesFilename);
			} else if (look4tracesFile && file_info != null) {
				final String filenameBeforeExtension = stripExtension(file_info.fileName);
				if (filenameBeforeExtension != null) {
					tracesFileToLoad = new File(file_info.directory, filenameBeforeExtension + ".traces");
				}
			}
			if (tracesFileToLoad == null)
				return;
			if (tracesFileToLoad.exists()) {
				resultsDialog.changeState(NeuriteTracerResultsDialog.LOADING);
				pathAndFillManager.loadGuessingType(tracesFileToLoad.getAbsolutePath());
				resultsDialog.changeState(NeuriteTracerResultsDialog.WAITING_TO_START_PATH);
			} else if (macroLoading) {
				SNT.error("The traces file suggested by the macro parameters does not exist:\n" + macroTracesFilename);
			}

		} finally {
			IJ.getInstance().addKeyListener(IJ.getInstance());

		}
	}
}
