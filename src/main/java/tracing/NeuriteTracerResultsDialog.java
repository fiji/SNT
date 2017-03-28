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

import java.awt.BorderLayout;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import features.SigmaPalette;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.HTMLDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.StackWindow;
import ij.gui.YesNoCancelDialog;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij3d.ImageWindow3D;
import sholl.Sholl_Analysis;
import stacks.ThreePanes;

@SuppressWarnings("serial")
public class NeuriteTracerResultsDialog extends JDialog implements ActionListener, WindowListener, ItemListener,
		TextListener, SigmaPalette.SigmaPaletteListener, ImageListener, ChangeListener {

	public static final boolean verbose = SimpleNeuriteTracer.verbose;

	public PathWindow pw;
	public FillWindow fw;

	protected JMenuBar menuBar;
	protected JMenu fileMenu;
	protected JMenu analysisMenu;
	protected JMenu viewMenu;

	protected JMenuItem loadMenuItem;
	protected JMenuItem loadLabelsMenuItem;
	protected JMenuItem saveMenuItem;
	protected JMenuItem exportCSVMenuItem;
	protected JMenuItem exportAllSWCMenuItem;
	protected JMenuItem quitMenuItem;

	protected JMenuItem makeLineStackMenuItem;
	protected JMenuItem pathsToROIsMenuItem;
	protected JMenuItem exportCSVMenuItemAgain;
	protected JMenuItem sendToTrakEM2;

	protected JCheckBoxMenuItem mipOverlayMenuItem;
	protected JCheckBoxMenuItem drawDiametersXYMenuItem;
	protected JCheckBoxMenuItem autoActivationMenuItem;

	protected JCheckBoxMenuItem xyCanvasMenuItem;
	protected JCheckBoxMenuItem zyCanvasMenuItem;
	protected JCheckBoxMenuItem xzCanvasMenuItem;
	protected JCheckBoxMenuItem threeDViewerMenuItem;
	protected JMenuItem arrangeWindowsMenuItem;

	// These are the states that the UI can be in:

	static final int WAITING_TO_START_PATH = 0;
	static final int PARTIAL_PATH = 1;
	static final int SEARCHING = 2;
	static final int QUERY_KEEP = 3;
	static final int LOGGING_POINTS = 4;
	static final int DISPLAY_EVS = 5;
	static final int FILLING_PATHS = 6;
	static final int CALCULATING_GAUSSIAN = 7;
	static final int WAITING_FOR_SIGMA_POINT = 8;
	static final int WAITING_FOR_SIGMA_CHOICE = 9;
	static final int SAVING = 10;
	static final int LOADING = 11;
	static final int FITTING_PATHS = 12;
	static final int IMAGE_CLOSED = 13;

	static final String[] stateNames = { "WAITING_TO_START_PATH", "PARTIAL_PATH", "SEARCHING", "QUERY_KEEP",
			"LOGGING_POINTS", "DISPLAY_EVS", "FILLING_PATHS", "CALCULATING_GAUSSIAN", "WAITING_FOR_SIGMA_POINT",
			"WAITING_FOR_SIGMA_CHOICE", "SAVING", "LOADING", "FITTING_PATHS", "IMAGE CLOSED" };

	static final String SEARCHING_STRING = "Searching for path between points...";

	protected volatile int currentState;

	public int getCurrentState() {
		return currentState;
	}

	final protected SimpleNeuriteTracer plugin;

	protected JPanel statusPanel;
	protected JLabel statusText;
	protected JButton keepSegment, junkSegment;
	protected JButton cancelSearch;

	protected JPanel pathActionPanel;
	protected JButton completePath;
	protected JButton cancelPath;

	protected JComboBox<String> viewPathChoice;
	protected String projectionChoice = "Projected through all slices";
	protected String partsNearbyChoice = "Parts in nearby slices [5]";

	protected TextField nearbyField;

	protected PathColorsCanvas pathColorsCanvas;
	protected JCheckBox enforceDefaultColors;
	protected JComboBox<String> colorImageChoice;
	protected String noColorImageString = "[None]";
	protected ImagePlus currentColorImage;

	protected JCheckBox justShowSelected;

	protected JComboBox<String> paths3DChoice;
	protected String[] paths3DChoicesStrings = { "BUG", "As surface reconstructions", "As lines",
			"As lines and discs" };

	protected JCheckBox useTubularGeodesics;

	protected JCheckBox preprocess;
	protected JCheckBox usePreprocessed;

	protected volatile double currentSigma;
	protected volatile double currentMultiplier;

	protected JLabel currentSigmaAndMultiplierLabel;

	protected JButton editSigma;
	protected JButton sigmaWizard;

	protected JButton showCorrespondencesToButton;

	protected JButton uploadButton;
	protected JButton fetchButton;

	protected JButton showOrHidePathList;
	protected JButton showOrHideFillList;

	protected JCheckBox useSnapWindow;
	protected JSpinner snapWindowXYsizeSpinner;
	protected JSpinner snapWindowZsizeSpinner;

	// ------------------------------------------------------------------------
	// Implementing the ImageListener interface:

	@Override
	public void imageOpened(final ImagePlus imp) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				updateColorImageChoice();
			}
		});
	}

	// Called when an image is closed
	@Override
	public void imageClosed(final ImagePlus imp) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				updateColorImageChoice();
				if (plugin.getImagePlus() == imp)
					changeState(NeuriteTracerResultsDialog.IMAGE_CLOSED);
			}
		});
	}

	@Override
	public void imageUpdated(final ImagePlus imp) {
		/*
		 * This is called whenever ImagePlus.updateAndDraw is called - i.e.
		 * potentially very often
		 */
	}

	// ------------------------------------------------------------------------

	protected void updateStatusText(final String newStatus) {
		assert SwingUtilities.isEventDispatchThread();
		statusText.setText("<html><strong>" + newStatus + "</strong></html>");
	}

	volatile boolean ignoreColorImageChoiceEvents = false;
	volatile boolean ignorePreprocessEvents = false;

	synchronized protected void updateColorImageChoice() {
		assert SwingUtilities.isEventDispatchThread();

		ignoreColorImageChoiceEvents = true;

		// Try to preserve the old selection:
		final String oldSelection = (String) colorImageChoice.getSelectedItem();

		colorImageChoice.removeAllItems();

		int j = 0;
		colorImageChoice.addItem(noColorImageString);

		int selectedIndex = 0;

		final int[] wList = WindowManager.getIDList();
		if (wList != null) {
			for (int i = 0; i < wList.length; i++) {
				final ImagePlus imp = WindowManager.getImage(wList[i]);
				j++;
				final String title = imp.getTitle();
				colorImageChoice.addItem(title);
				if (title == oldSelection)
					selectedIndex = j;
			}
		}

		colorImageChoice.setSelectedIndex(selectedIndex);

		ignoreColorImageChoiceEvents = false;

		// This doesn't trigger an item event
		checkForColorImageChange();
	}

	public static boolean similarCalibrations(final Calibration a, final Calibration b) {
		double ax = 1, ay = 1, az = 1;
		double bx = 1, by = 1, bz = 1;
		if (a != null) {
			ax = a.pixelWidth;
			ay = a.pixelHeight;
			az = a.pixelDepth;
		}
		if (b != null) {
			bx = b.pixelWidth;
			by = b.pixelHeight;
			bz = b.pixelDepth;
		}
		final double pixelWidthDifference = Math.abs(ax - bx);
		final double pixelHeightDifference = Math.abs(ay - by);
		final double pixelDepthDifference = Math.abs(az - bz);
		final double epsilon = 0.000001;
		if (pixelWidthDifference > epsilon)
			return false;
		if (pixelHeightDifference > epsilon)
			return false;
		if (pixelDepthDifference > epsilon)
			return false;
		return true;
	}

	synchronized protected void checkForColorImageChange() {
		final String selectedTitle = (String) colorImageChoice.getSelectedItem();

		ImagePlus intendedColorImage = null;
		if (selectedTitle != null && !selectedTitle.equals(noColorImageString)) {
			intendedColorImage = WindowManager.getImage(selectedTitle);
		}

		if (intendedColorImage != currentColorImage) {
			if (intendedColorImage != null) {
				final ImagePlus image = plugin.getImagePlus();
				final Calibration calibration = plugin.getImagePlus().getCalibration();
				final Calibration colorImageCalibration = intendedColorImage.getCalibration();
				if (!similarCalibrations(calibration, colorImageCalibration)) {
					SNT.error("Warning: the calibration of '" + intendedColorImage.getTitle()
							+ "' is different from the image you're tracing ('" + image.getTitle()
							+ "')'\nThis may produce unexpected results.");
				}
				if (!(intendedColorImage.getWidth() == image.getWidth()
						&& intendedColorImage.getHeight() == image.getHeight()
						&& intendedColorImage.getStackSize() == image.getStackSize()))
					SNT.error("Warning: the dimensions (in voxels) of '" + intendedColorImage.getTitle()
							+ "' is different from the image you're tracing ('" + image.getTitle()
							+ "')'\nThis may produce unexpected results.");
			}
			currentColorImage = intendedColorImage;
			plugin.setColorImage(currentColorImage);
		}
	}

	@Override
	public void newSigmaSelected(final double sigma) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setSigma(sigma, false);
			}
		});
	}

	@Override
	public void newMaximum(final double max) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				final double multiplier = 256 / max;
				setMultiplier(multiplier);
			}
		});
	}

	// ------------------------------------------------------------------------

	volatile protected int preGaussianState;
	volatile protected int preSigmaPaletteState;

	public void gaussianCalculated(final boolean succeeded) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (!succeeded) {
					ignorePreprocessEvents = true;
					preprocess.setSelected(false);
					ignorePreprocessEvents = false;
				}
				changeState(preGaussianState);
				if (preprocess.isSelected()) {
					editSigma.setEnabled(false);
					sigmaWizard.setEnabled(false);
				} else {
					editSigma.setEnabled(true);
					sigmaWizard.setEnabled(true);
				}
			}
		});
	}

	public void setMultiplier(final double multiplier) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				currentMultiplier = multiplier;
				updateLabel();
			}
		});
	}

	public void setSigma(final double sigma, final boolean mayStartGaussian) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				currentSigma = sigma;
				updateLabel();
				if (mayStartGaussian) {
					if (preprocess.isSelected()) {
						SNT.error("[BUG] The preprocess checkbox should never be on when setSigma is called");
					} else {
						// Turn on the checkbox:
						ignorePreprocessEvents = true;
						preprocess.setSelected(true);
						ignorePreprocessEvents = false;
						/*
						 * ... according to the documentation this doesn't
						 * generate an event, so we manually turn on the
						 * Gaussian calculation
						 */
						turnOnHessian();
					}
				}
			}
		});
	}

	protected void turnOnHessian() {
		preGaussianState = currentState;
		plugin.enableHessian(true);
	}

	protected DecimalFormat threeDecimalPlaces = new DecimalFormat("0.0000");
	protected DecimalFormat threeDecimalPlacesScientific = new DecimalFormat("0.00E00");

	protected String formatDouble(final double value) {
		final double absValue = Math.abs(value);
		if (absValue < 0.01 || absValue >= 1000)
			return threeDecimalPlacesScientific.format(value);
		return threeDecimalPlaces.format(value);
	}

	protected void updateLabel() {
		assert SwingUtilities.isEventDispatchThread();
		currentSigmaAndMultiplierLabel.setText(
				"\u03C3 = " + formatDouble(currentSigma) + ", Multiplier = " + formatDouble(currentMultiplier));
	}

	public double getSigma() {
		return currentSigma;
	}

	public double getMultiplier() {
		return currentMultiplier;
	}

	protected void exitRequested() {
		assert SwingUtilities.isEventDispatchThread();

		// FIXME: check that everything is saved...

		if (plugin.pathsUnsaved()) {

			final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), "Really quit?",
					"There are unsaved paths. Do you really want to quit?");

			if (!d.yesPressed())
				return;

		}

		plugin.cancelSearch(true);
		plugin.notifyListeners(new SNTEvent(SNTEvent.QUIT));
		new SNTPrefs(plugin).savePluginPrefs();
		pw.dispose();
		fw.dispose();
		dispose();
		plugin.closeAndReset();
	}

	protected void disableImageDependentComponents() {
		assert SwingUtilities.isEventDispatchThread();
		loadMenuItem.setEnabled(false);
		loadLabelsMenuItem.setEnabled(false);
		keepSegment.setEnabled(false);
		junkSegment.setEnabled(false);
		cancelSearch.setEnabled(false);
		completePath.setEnabled(false);
		cancelPath.setEnabled(false);
		editSigma.setEnabled(false);
		sigmaWizard.setEnabled(false);
		preprocess.setEnabled(false);
		useTubularGeodesics.setEnabled(false);
		fw.setEnabledNone();
		pw.fillOutSetEnabled(false);

	}

	protected void disableEverything() {

		assert SwingUtilities.isEventDispatchThread();

		disableImageDependentComponents();

		pw.setButtonsEnabled(false);

		statusText.setEnabled(false);
		viewPathChoice.setEnabled(false);
		paths3DChoice.setEnabled(false);

		exportCSVMenuItem.setEnabled(false);
		exportAllSWCMenuItem.setEnabled(false);
		exportCSVMenuItemAgain.setEnabled(false);
		sendToTrakEM2.setEnabled(false);
		pathsToROIsMenuItem.setEnabled(false);
		saveMenuItem.setEnabled(false);
		if (uploadButton != null) {
			uploadButton.setEnabled(false);
			fetchButton.setEnabled(false);
		}
		quitMenuItem.setEnabled(false);
	}

	public void changeState(final int newState) {

		if (verbose)
			SNT.log("changeState to: " + stateNames[newState]);

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				switch (newState) {

				case WAITING_TO_START_PATH:
					updateStatusText("Click somewhere to start a new path...");
					disableEverything();
					pw.setButtonsEnabled(true);
					// Fake a selection change in the path tree:
					pw.valueChanged(null);

					cancelSearch.setVisible(false);
					keepSegment.setVisible(false);
					junkSegment.setVisible(false);

					viewPathChoice.setEnabled(isStackAvailable());
					nearbyField.setEnabled(nearbySlices());
					paths3DChoice.setEnabled(isThreeDViewerAvailable());
					preprocess.setEnabled(true);
					useTubularGeodesics.setEnabled(plugin.oofFileAvailable());

					editSigma.setEnabled(!preprocess.isSelected());
					sigmaWizard.setEnabled(!preprocess.isSelected());

					fw.setEnabledWhileNotFilling();

					loadLabelsMenuItem.setEnabled(true);

					saveMenuItem.setEnabled(true);
					loadMenuItem.setEnabled(true);
					exportCSVMenuItem.setEnabled(true);
					exportAllSWCMenuItem.setEnabled(true);
					exportCSVMenuItemAgain.setEnabled(true);
					sendToTrakEM2.setEnabled(plugin.anyListeners());
					pathsToROIsMenuItem.setEnabled(true);
					if (uploadButton != null) {
						uploadButton.setEnabled(true);
						fetchButton.setEnabled(true);
					}

					quitMenuItem.setEnabled(true);

					break;

				case PARTIAL_PATH:
					updateStatusText("Now select a point further along that structure...");
					disableEverything();

					cancelSearch.setVisible(false);
					keepSegment.setVisible(false);
					junkSegment.setVisible(false);

					if (plugin.justFirstPoint())
						completePath.setEnabled(false);
					else
						completePath.setEnabled(true);
					cancelPath.setEnabled(true);

					viewPathChoice.setEnabled(isStackAvailable());
					paths3DChoice.setEnabled(isStackAvailable());
					preprocess.setEnabled(true);
					useTubularGeodesics.setEnabled(plugin.oofFileAvailable());

					editSigma.setEnabled(!preprocess.isSelected());
					sigmaWizard.setEnabled(!preprocess.isSelected());

					quitMenuItem.setEnabled(false);

					break;

				case SEARCHING:
					updateStatusText("Searching for path between points...");
					disableEverything();

					// cancelSearch.setText("Abandon search");
					cancelSearch.setEnabled(true);
					cancelSearch.setVisible(true);
					keepSegment.setVisible(false);
					junkSegment.setVisible(false);

					completePath.setEnabled(false);
					cancelPath.setEnabled(false);

					quitMenuItem.setEnabled(true);

					break;

				case QUERY_KEEP:
					updateStatusText("Keep this new path segment?");
					disableEverything();

					keepSegment.setEnabled(true);
					junkSegment.setEnabled(true);

					cancelSearch.setVisible(false);
					keepSegment.setVisible(true);
					junkSegment.setVisible(true);

					break;

				case FILLING_PATHS:
					updateStatusText("Filling out from neuron...");
					disableEverything();

					fw.setEnabledWhileFilling();

					break;

				case FITTING_PATHS:
					updateStatusText("Fitting volumes around neurons...");
					disableEverything();
					break;

				case CALCULATING_GAUSSIAN:
					updateStatusText("Calculating Gaussian...");
					disableEverything();

					cancelSearch.setText("Cancel");
					cancelSearch.setEnabled(true);
					cancelSearch.setVisible(true);
					keepSegment.setVisible(true);
					junkSegment.setVisible(true);

					break;

				case WAITING_FOR_SIGMA_POINT:
					updateStatusText("Click on a neuron in the image");
					disableEverything();
					break;

				case WAITING_FOR_SIGMA_CHOICE:
					updateStatusText("Close the sigma palette window to continue");
					disableEverything();
					break;

				case LOADING:
					updateStatusText("Loading...");
					disableEverything();
					break;

				case SAVING:
					updateStatusText("Saving...");
					disableEverything();
					break;

				case IMAGE_CLOSED:
					updateStatusText("Tracing image is no longer available...");
					disableImageDependentComponents();
					plugin.discardFill(false);
					quitMenuItem.setEnabled(true);
					break;

				default:
					SNT.error("BUG: switching to an unknown state");
					return;
				}

				pack();

				plugin.repaintAllPanes();
			}

		});

		currentState = newState;
	}

	public int getState() {
		return currentState;
	}

	private boolean isStackAvailable() {
		return plugin != null && !plugin.singleSlice;
	}

	private boolean isThreeDViewerAvailable() {
		return plugin != null && !plugin.singleSlice && plugin.use3DViewer;
	}

	// ------------------------------------------------------------------------

	@Override
	public void windowClosing(final WindowEvent e) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				exitRequested();
			}
		});
	}

	@Override
	public void windowActivated(final WindowEvent e) {
	}

	@Override
	public void windowDeactivated(final WindowEvent e) {
	}

	@Override
	public void windowClosed(final WindowEvent e) {
	}

	@Override
	public void windowOpened(final WindowEvent e) {
	}

	@Override
	public void windowIconified(final WindowEvent e) {
	}

	@Override
	public void windowDeiconified(final WindowEvent e) {
	}

	private final PathAndFillManager pathAndFillManager;

	protected boolean launchedByArchive;

	public NeuriteTracerResultsDialog(final String title, final SimpleNeuriteTracer plugin,
			final boolean launchedByArchive) {

		super(IJ.getInstance(), title, false);
		assert SwingUtilities.isEventDispatchThread();

		new ClarifyingKeyListener().addKeyAndContainerListenerRecursively(this);

		this.plugin = plugin;
		final SimpleNeuriteTracer thisPlugin = plugin;
		this.launchedByArchive = launchedByArchive;

		pathAndFillManager = plugin.getPathAndFillManager();

		// Create the menu bar and menus:

		menuBar = new JMenuBar();

		fileMenu = new JMenu("File");
		menuBar.add(fileMenu);

		menuBar.add(tracingMenu());

		analysisMenu = new JMenu("Analysis");
		menuBar.add(analysisMenu);

		viewMenu = new JMenu("View");
		menuBar.add(viewMenu);

		menuBar.add(helpMenu());

		loadMenuItem = new JMenuItem("Load Traces / (e)SWC File...");
		loadMenuItem.addActionListener(this);
		fileMenu.add(loadMenuItem);

		loadLabelsMenuItem = new JMenuItem("Load Labels (AmiraMesh) File...");
		loadLabelsMenuItem.addActionListener(this);
		fileMenu.add(loadLabelsMenuItem);

		fileMenu.addSeparator();
		saveMenuItem = new JMenuItem("Save Traces File...");
		saveMenuItem.addActionListener(this);
		fileMenu.add(saveMenuItem);
		exportAllSWCMenuItem = new JMenuItem("Save All Paths as SWC...");
		exportAllSWCMenuItem.addActionListener(this);
		fileMenu.add(exportAllSWCMenuItem);

		fileMenu.addSeparator();
		exportCSVMenuItem = new JMenuItem("Export Path Properties...");
		exportCSVMenuItem.addActionListener(this);
		fileMenu.add(exportCSVMenuItem);

		sendToTrakEM2 = new JMenuItem("Send to TrakEM2");
		sendToTrakEM2.addActionListener(this);
		fileMenu.add(sendToTrakEM2);

		fileMenu.addSeparator();
		quitMenuItem = new JMenuItem("Quit");
		quitMenuItem.addActionListener(this);
		fileMenu.add(quitMenuItem);

		pathsToROIsMenuItem = new JMenuItem("Convert Paths to ROIs...");
		pathsToROIsMenuItem.addActionListener(this);
		analysisMenu.add(pathsToROIsMenuItem);
		analysisMenu.addSeparator();
		exportCSVMenuItemAgain = new JMenuItem("Measure Paths...");
		exportCSVMenuItemAgain.addActionListener(this);
		analysisMenu.add(exportCSVMenuItemAgain);
		makeLineStackMenuItem = new JMenuItem("Render/Analyze Skeletonized Paths...");
		makeLineStackMenuItem.addActionListener(this);
		analysisMenu.add(makeLineStackMenuItem);
		analysisMenu.addSeparator();
		analysisMenu.add(shollAnalysisHelpMenuItem());

		xyCanvasMenuItem = new JCheckBoxMenuItem("Hide XY View");
		xyCanvasMenuItem.addItemListener(this);
		viewMenu.add(xyCanvasMenuItem);
		zyCanvasMenuItem = new JCheckBoxMenuItem("Hide ZY View");
		zyCanvasMenuItem.setEnabled(!plugin.getSinglePane());
		zyCanvasMenuItem.addItemListener(this);
		viewMenu.add(zyCanvasMenuItem);
		xzCanvasMenuItem = new JCheckBoxMenuItem("Hide XZ View");
		xzCanvasMenuItem.setEnabled(!plugin.getSinglePane());
		xzCanvasMenuItem.addItemListener(this);
		viewMenu.add(xzCanvasMenuItem);
		threeDViewerMenuItem = new JCheckBoxMenuItem("Hide 3D View");
		threeDViewerMenuItem.setEnabled(plugin.use3DViewer);
		threeDViewerMenuItem.addItemListener(this);
		viewMenu.add(threeDViewerMenuItem);
		viewMenu.addSeparator();
		arrangeWindowsMenuItem = new JMenuItem("Arrange Views");
		arrangeWindowsMenuItem.addActionListener(this);
		viewMenu.add(arrangeWindowsMenuItem);

		setJMenuBar(menuBar);

		addWindowListener(this);

		getContentPane().setLayout(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;

		c.insets = new Insets(4, 4, 0, 0);
		getContentPane().add(separator("Instructions:"), c);
		++c.gridy;
		c.insets = new Insets(0, 10, 0, 0);

		{ /* Add the status panel */

			statusPanel = new JPanel();
			statusPanel.setLayout(new BorderLayout());
			statusText = new JLabel("");
			statusText.setOpaque(true);
			statusText.setForeground(Color.black);
			statusText.setBackground(Color.white);
			updateStatusText("Initial status text");
			statusText.setBorder(new EmptyBorder(5, 5, 5, 5));
			statusPanel.add(statusText, BorderLayout.CENTER);

			keepSegment = new JButton("Yes [y]");
			junkSegment = new JButton("No [n]");
			cancelSearch = new JButton("Abandon Search [Esc]");

			keepSegment.addActionListener(this);
			junkSegment.addActionListener(this);
			cancelSearch.addActionListener(this);

			final JPanel statusChoicesPanel = new JPanel();
			/*
			 * statusChoicesPanel.setLayout( new GridBagLayout() );
			 * GridBagConstraints cs = new GridBagConstraints(); cs.weightx = 1;
			 * cs.gridx = 0; cs.gridy = 0; cs.anchor =
			 * GridBagConstraints.LINE_START;
			 * statusChoicesPanel.add(keepSegment,cs); cs.gridx = 1; cs.gridy =
			 * 0; cs.anchor = GridBagConstraints.LINE_START;
			 * statusChoicesPanel.add(junkSegment,cs); cs.gridx = 2; cs.gridy =
			 * 0; cs.anchor = GridBagConstraints.LINE_START;
			 * statusChoicesPanel.add(cancelSearch,cs);
			 */
			statusChoicesPanel.add(keepSegment);
			statusChoicesPanel.add(junkSegment);
			statusChoicesPanel.add(cancelSearch);
			statusChoicesPanel.setLayout(new FlowLayout());
			statusPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
			statusPanel.add(statusChoicesPanel, BorderLayout.SOUTH);
			c.insets = new Insets(4, 0, 0, 0);
			getContentPane().add(statusPanel, c);
		}

		{ /* Add the panel of actions to take on half-constructed paths */

			pathActionPanel = new JPanel();
			completePath = new JButton("Finish Path [f]");
			cancelPath = new JButton("Cancel Path [c]");
			completePath.addActionListener(this);
			cancelPath.addActionListener(this);
			pathActionPanel.add(completePath);
			pathActionPanel.add(cancelPath);
			pathActionPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

			c.insets = new Insets(0, 0, 0, 0);
			++c.gridy;
			getContentPane().add(pathActionPanel, c);
		}

		c.insets = new Insets(4, 10, 10, 10);

		++c.gridy;
		addSeparator("Tracing:", c);
		++c.gridy;
		getContentPane().add(tracingPanel(), c);

		++c.gridy;
		addSeparator("Rendering:", c);
		{
			final JPanel viewOptionsPanel = new JPanel();
			viewOptionsPanel.setLayout(new GridBagLayout());
			final GridBagConstraints vop_c = new GridBagConstraints();
			vop_c.anchor = GridBagConstraints.LINE_START;
			vop_c.insets = new Insets(0, 0, 0, 0);
			vop_c.gridx = 0;
			vop_c.gridy = 0;
			vop_c.weightx = 1.0;
			vop_c.fill = GridBagConstraints.BOTH;

			paths3DChoice = new JComboBox<>();
			for (int choice = 1; choice < paths3DChoicesStrings.length; ++choice)
				paths3DChoice.addItem(paths3DChoicesStrings[choice]);
			paths3DChoice.addItemListener(this);
			paths3DChoice.setEnabled(isThreeDViewerAvailable());

			vop_c.gridx = 0;
			viewOptionsPanel.add(leftAlignedLabel("View paths (3D):", isThreeDViewerAvailable()), vop_c);
			vop_c.gridx = 1;
			viewOptionsPanel.add(paths3DChoice, vop_c);

			viewPathChoice = new JComboBox<>();
			viewPathChoice.addItem(projectionChoice);
			viewPathChoice.addItem(partsNearbyChoice);
			viewPathChoice.addItemListener(this);
			viewPathChoice.setEnabled(isStackAvailable());

			final JPanel nearbyPanel = new JPanel();
			nearbyPanel.setLayout(new BorderLayout());
			nearbyPanel.add(leftAlignedLabel("(up to ", isStackAvailable()), BorderLayout.WEST);
			nearbyField = new TextField("2", 2);
			nearbyField.addTextListener(this);
			nearbyPanel.add(nearbyField, BorderLayout.CENTER);
			nearbyPanel.add(leftAlignedLabel(" slices to each side)", isStackAvailable()), BorderLayout.EAST);
			nearbyField.setEnabled(isStackAvailable());
			++vop_c.gridy;
			vop_c.gridx = 0;
			viewOptionsPanel.add(leftAlignedLabel("View paths (2D):", isStackAvailable()), vop_c);
			vop_c.gridx = 1;
			viewOptionsPanel.add(viewPathChoice, vop_c);
			++vop_c.gridy;
			viewOptionsPanel.add(nearbyPanel, vop_c);
			justShowSelected = new JCheckBox("Show only selected paths", plugin.showOnlySelectedPaths);
			justShowSelected.addItemListener(this);

			vop_c.gridwidth = GridBagConstraints.REMAINDER;
			vop_c.gridx = 0;
			++vop_c.gridy;
			viewOptionsPanel.add(justShowSelected, vop_c);

			++c.gridy;
			getContentPane().add(viewOptionsPanel, c);
		}

		++c.gridy;
		addSeparator("Labelling of Paths:", c);

		{

			final JLabel flatColorLabel = new JLabel("Default Colors (click to change):");
			flatColorLabel.setHorizontalAlignment(SwingConstants.CENTER);
			flatColorLabel.setBorder(new EmptyBorder(0, 0, 2, 0));
			pathColorsCanvas = new PathColorsCanvas(thisPlugin, 150, 18);

			final JLabel imageColorLabel = leftAlignedLabel("3D Viewer: Use colors / labels from:",
					isThreeDViewerAvailable());
			imageColorLabel.setHorizontalAlignment(SwingConstants.CENTER);
			imageColorLabel.setBorder(new EmptyBorder(6, 0, 0, 0));

			colorImageChoice = new JComboBox<>();
			updateColorImageChoice();
			colorImageChoice.addActionListener(this);
			ImagePlus.addImageListener(this);
			colorImageChoice.setEnabled(isThreeDViewerAvailable());

			final JPanel pathOptionsPanel = new JPanel();
			pathOptionsPanel.setLayout(new GridBagLayout());
			final GridBagConstraints pop_c = singleColumnConstrains();

			pathOptionsPanel.add(flatColorLabel, pop_c);
			++pop_c.gridy;
			pop_c.insets = new Insets(0, 4, 0, 4);
			pathOptionsPanel.add(pathColorsCanvas, pop_c);
			pop_c.insets = new Insets(0, 0, 0, 0);

			enforceDefaultColors = new JCheckBox("Enforce default colors (ignore customizations)",
					!plugin.displayCustomPathColors);
			enforceDefaultColors.addItemListener(this);
			++pop_c.gridy;
			pathOptionsPanel.add(enforceDefaultColors, pop_c);

			++pop_c.gridy;
			pathOptionsPanel.add(imageColorLabel, pop_c);
			++pop_c.gridy;
			pathOptionsPanel.add(colorImageChoice, pop_c);

			++c.gridy;
			getContentPane().add(pathOptionsPanel, c);
		}

		++c.gridy;
		addSeparator("Segmentation:", c);

		{ /*
			 * Add the panel with other options - preprocessing and the view of
			 * paths
			 */

			final JPanel otherOptionsPanel = new JPanel();
			otherOptionsPanel.setLayout(new GridBagLayout());
			final GridBagConstraints oop_c = singleColumnConstrains();

			useTubularGeodesics = new JCheckBox("Use Tubular Geodesics");
			useTubularGeodesics.addItemListener(this);
			++oop_c.gridy;
			otherOptionsPanel.add(useTubularGeodesics, oop_c);

			preprocess = new JCheckBox("Hessian-based analysis");
			preprocess.addItemListener(this);
			++oop_c.gridy;
			otherOptionsPanel.add(preprocess, oop_c);

			usePreprocessed = new JCheckBox("Use Tubeness preprocessed image");
			usePreprocessed.addItemListener(this);
			usePreprocessed.setEnabled(thisPlugin.tubeness != null);
			++oop_c.gridy;
			otherOptionsPanel.add(usePreprocessed, oop_c);

			currentSigmaAndMultiplierLabel = new JLabel();
			currentSigmaAndMultiplierLabel.setHorizontalAlignment(SwingConstants.CENTER);
			++oop_c.gridy;
			otherOptionsPanel.add(currentSigmaAndMultiplierLabel, oop_c);
			setSigma(thisPlugin.getMinimumSeparation(), false);
			setMultiplier(4);
			updateLabel();

			final JPanel sigmaButtonPanel = new JPanel();

			editSigma = SNT.smallButton("Pick Sigma Manually");
			editSigma.addActionListener(this);
			sigmaButtonPanel.add(editSigma);

			sigmaWizard = SNT.smallButton("Pick Sigma Visually");
			sigmaWizard.addActionListener(this);
			sigmaButtonPanel.add(sigmaWizard);

			++oop_c.gridy;
			otherOptionsPanel.add(sigmaButtonPanel, oop_c);

			++c.gridy;
			getContentPane().add(otherOptionsPanel, c);
		}

		{
			final JPanel hideWindowsPanel = new JPanel();
			showOrHidePathList = new JButton("Show / Hide Path List");
			showOrHidePathList.addActionListener(this);
			showOrHideFillList = new JButton("Show / Hide Fill List");
			showOrHideFillList.addActionListener(this);
			hideWindowsPanel.add(showOrHidePathList);
			hideWindowsPanel.add(showOrHideFillList);
			c.fill = GridBagConstraints.HORIZONTAL;
			++c.gridy;
			getContentPane().add(hideWindowsPanel, c);
		}

		pack();

		pw = new PathWindow(pathAndFillManager, thisPlugin, getX() + getWidth(), getY());
		pathAndFillManager.addPathAndFillListener(pw);

		fw = new FillWindow(pathAndFillManager, thisPlugin, getX() + getWidth(), getY() + pw.getHeight());
		pathAndFillManager.addPathAndFillListener(fw);

		changeState(WAITING_TO_START_PATH);
	}

	private JPanel tracingPanel() {

		final JPanel tracingOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		useSnapWindow = new JCheckBox("Enable cursor [s]napping within: XY", plugin.snapCursor);
		useSnapWindow.setBorder(new EmptyBorder(0, 0, 0, 0));
		useSnapWindow.addItemListener(this);
		tracingOptionsPanel.add(useSnapWindow);

		final SpinnerModel xy_model = new SpinnerNumberModel(plugin.cursorSnapWindowXY * 2,
				SimpleNeuriteTracer.MIN_SNAP_CURSOR_WINDOW_XY, SimpleNeuriteTracer.MAX_SNAP_CURSOR_WINDOW_XY * 2, 2);
		snapWindowXYsizeSpinner = new JSpinner(xy_model);
		((DefaultEditor) snapWindowXYsizeSpinner.getEditor()).getTextField().setEditable(false);
		snapWindowXYsizeSpinner.addChangeListener(this);
		tracingOptionsPanel.add(snapWindowXYsizeSpinner);

		final JLabel z_spinner_label = leftAlignedLabel("Z", isStackAvailable());
		z_spinner_label.setBorder(new EmptyBorder(0, 2, 0, 0));
		tracingOptionsPanel.add(z_spinner_label);
		final SpinnerModel z_model = new SpinnerNumberModel(plugin.cursorSnapWindowZ * 2,
				SimpleNeuriteTracer.MIN_SNAP_CURSOR_WINDOW_Z, SimpleNeuriteTracer.MAX_SNAP_CURSOR_WINDOW_Z * 2, 2);
		snapWindowZsizeSpinner = new JSpinner(z_model);
		((DefaultEditor) snapWindowZsizeSpinner.getEditor()).getTextField().setEditable(false);
		snapWindowZsizeSpinner.addChangeListener(this);
		snapWindowZsizeSpinner.setEnabled(isStackAvailable());
		tracingOptionsPanel.add(snapWindowZsizeSpinner);
		// tracingOptionsPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
		return tracingOptionsPanel;
	}

	private GridBagConstraints singleColumnConstrains() {
		final GridBagConstraints cp = new GridBagConstraints();
		cp.anchor = GridBagConstraints.LINE_START;
		cp.gridwidth = GridBagConstraints.REMAINDER;
		cp.fill = GridBagConstraints.HORIZONTAL;
		cp.insets = new Insets(0, 0, 0, 0);
		cp.weightx = 1.0;
		cp.gridx = 0;
		cp.gridy = 0;
		return cp;
	}

	private void addSeparator(final String heading, final GridBagConstraints c) {
		final Insets previousInsets = c.insets;
		c.insets = new Insets(8, 4, 0, 0);
		getContentPane().add(separator(heading), c);
		c.insets = previousInsets;
	}

	private JLabel separator(final String text) {
		final JLabel label = leftAlignedLabel(text, true);
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));
		return label;
	}

	private JLabel leftAlignedLabel(final String text, final boolean enabled) {
		final JLabel label = new JLabel(text);
		if (!enabled)
			label.setForeground(sholl.gui.Utils.getDisabledComponentColor());
		return label;
	}

	protected void displayOnStarting() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				arrangeDialogs();
				arrangeCanvases();
				setVisible(true);
				setPathListVisible(true, false);
				setFillListVisible(false);
				plugin.getWindow(ThreePanes.XY_PLANE).toFront();
			}
		});
	}

	public void showMouseThreshold(final float t) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				String newStatus = null;
				if (t < 0) {
					newStatus = "Cursor position not reached by search yet";
				} else {
					newStatus = "Cursor position: Distance from path is " + fw.df4.format(t);
				}
				fw.fillStatus.setText(newStatus);
			}
		});
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		assert SwingUtilities.isEventDispatchThread();

		final Object source = e.getSource();

		/*
		 * if( source == uploadButton ) { plugin.uploadTracings(); } else if(
		 * source == fetchButton ) { plugin.getTracings( true ); } else
		 */ if (source == saveMenuItem && !noPathsError()) {

			final FileInfo info = plugin.file_info;
			SaveDialog sd;

			if (info == null) {

				sd = new SaveDialog("Save traces as...", "image", ".traces");

			} else {

				final String fileName = info.fileName;
				final String directory = info.directory;

				String suggestedSaveFilename;

				suggestedSaveFilename = fileName;

				sd = new SaveDialog("Save traces as...", directory, suggestedSaveFilename, ".traces");
			}

			String savePath;
			if (sd.getFileName() == null) {
				return;
			}
			savePath = sd.getDirectory() + sd.getFileName();

			final File file = new File(savePath);
			if (file.exists()) {
				if (!IJ.showMessageWithCancel("Save traces file...",
						"The file " + savePath + " already exists.\n" + "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Saving traces to " + savePath);

			final int preSavingState = currentState;
			changeState(SAVING);
			try {
				pathAndFillManager.writeXML(savePath, plugin.useCompressedXML);
			} catch (final IOException ioe) {
				IJ.showStatus("Saving failed.");
				SNT.error("Writing traces to '" + savePath + "' failed: " + ioe);
				changeState(preSavingState);
				return;
			}
			changeState(preSavingState);
			IJ.showStatus("Saving completed.");

			plugin.unsavedPaths = false;

		} else if (source == loadMenuItem) {

			if (plugin.pathsUnsaved()) {
				final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), "Warning",
						"There are unsaved paths. Do you really want to load new traces?");

				if (!d.yesPressed())
					return;
			}

			final int preLoadingState = currentState;
			changeState(LOADING);
			plugin.loadTracings();
			changeState(preLoadingState);

		} else if (source == exportAllSWCMenuItem && !noPathsError()) {

			if (pathAndFillManager.usingNonPhysicalUnits()) {
				final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), "Warning",
						"These tracings were obtained from a spatially uncalibrated image.\n"
								+ "The SWC specification assumes all coordinates to be in " + IJ.micronSymbol + "m.\n"
								+ "Do you really want to proceed with the SWC export?");
				if (!d.yesPressed())
					return;
			}

			final FileInfo info = plugin.file_info;
			SaveDialog sd;

			if (info == null) {

				sd = new SaveDialog("Export all as SWC...", "exported", "");

			} else {

				String suggestedFilename;
				final int extensionIndex = info.fileName.lastIndexOf(".");
				if (extensionIndex == -1)
					suggestedFilename = info.fileName;
				else
					suggestedFilename = info.fileName.substring(0, extensionIndex);

				sd = new SaveDialog("Export all as SWC...", info.directory, suggestedFilename + "-exported", "");
			}

			String savePath;
			if (sd.getFileName() == null) {
				return;
			}
			savePath = sd.getDirectory() + sd.getFileName();
			if (verbose)
				SNT.log("Got savePath: " + savePath);
			if (!pathAndFillManager.checkOKToWriteAllAsSWC(savePath))
				return;
			pathAndFillManager.exportAllAsSWC(savePath);

		} else if ((source == exportCSVMenuItem || source == exportCSVMenuItemAgain) && !noPathsError()) {

			final FileInfo info = plugin.file_info;
			SaveDialog sd;

			if (info == null) {

				sd = new SaveDialog("Export Paths as CSV...", "traces", ".csv");

			} else {

				sd = new SaveDialog("Export Paths as CSV...", info.directory, info.fileName, ".csv");
			}

			String savePath;
			if (sd.getFileName() == null) {
				return;
			}
			savePath = sd.getDirectory() + sd.getFileName();

			final File file = new File(savePath);
			if (file.exists()) {
				if (!IJ.showMessageWithCancel("Export as CSV...",
						"The file " + savePath + " already exists.\n" + "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Exporting as CSV to " + savePath);

			final int preExportingState = currentState;
			changeState(SAVING);
			// Export here...
			try {
				pathAndFillManager.exportToCSV(file);
				if (source == exportCSVMenuItemAgain) {
					final ResultsTable rt = ResultsTable.open(savePath);
					rt.show(sd.getFileName());
				}
			} catch (final IOException ioe) {
				IJ.showStatus("Exporting failed.");
				SNT.error("Writing traces to '" + savePath + "' failed:\n" + ioe);
				changeState(preExportingState);
				return;
			}
			IJ.showStatus("Export complete.");
			changeState(preExportingState);

		} else if (source == sendToTrakEM2) {

			plugin.notifyListeners(new SNTEvent(SNTEvent.SEND_TO_TRAKEM2));

		} else if (source == showCorrespondencesToButton) {

			// Ask for the traces file to show correspondences to:

			String fileName = null;
			String directory = null;

			OpenDialog od;
			od = new OpenDialog("Select other traces file...", directory, null);

			fileName = od.getFileName();
			directory = od.getDirectory();

			if (fileName != null) {

				final File tracesFile = new File(directory, fileName);
				if (!tracesFile.exists()) {
					SNT.error("The file '" + tracesFile.getAbsolutePath() + "' does not exist.");
					return;
				}

				/* FIXME: test code: */

				// File tracesFile = new
				// File("/media/LaCie/corpus/flybrain/Data/1/lo15r202.fitted.traces");
				// File fittedTracesFile = new
				// File("/media/LaCie/corpus/flybrain/Data/1/LO15R202.traces");

				// plugin.showCorrespondencesTo( tracesFile, Color.YELLOW, 2.5
				// );
				// plugin.showCorrespondencesTo( fittedTracesFile, Color.RED,
				// 2.5 );

				plugin.showCorrespondencesTo(tracesFile, Color.YELLOW, 2.5);

				/* end of FIXME */

			}

		} else if (source == loadLabelsMenuItem) {

			plugin.loadLabels();

		} else if (source == makeLineStackMenuItem && !noPathsError()) {

			final SkeletonPlugin skelPlugin = new SkeletonPlugin(plugin);
			skelPlugin.run();

		} else if (source == pathsToROIsMenuItem && !noPathsError()) {

			if (!pathAndFillManager.anySelected()) {
				SNT.error("No paths selected.");
				return;
			}

			final GenericDialog gd = new GenericDialog("Selected Paths to ROIs");

			final int[] PLANES_ID = { ThreePanes.XY_PLANE, ThreePanes.XZ_PLANE, ThreePanes.ZY_PLANE };
			final String[] PLANES_STRING = { "XY_View", "XZ_View", "ZY_View" };
			final InteractiveTracerCanvas[] canvases = { plugin.xy_tracer_canvas, plugin.xz_tracer_canvas,
					plugin.zy_tracer_canvas };
			final boolean[] destinationPlanes = new boolean[PLANES_ID.length];
			for (int i = 0; i < PLANES_ID.length; i++)
				destinationPlanes[i] = canvases[i] != null && getImagePlusFromPane(PLANES_ID[i]) != null;

			gd.setInsets(0, 10, 0);
			gd.addMessage("Create 2D Path-ROIs from:");
			gd.setInsets(0, 20, 0);
			for (int i = 0; i < PLANES_ID.length; i++)
				gd.addCheckbox(PLANES_STRING[i], destinationPlanes[i]);

			// 2D traces?
			final Vector<?> cbxs = gd.getCheckboxes();
			for (int i = 1; i < PLANES_ID.length; i++)
				((Checkbox) cbxs.get(i)).setEnabled(!plugin.singleSlice);

			final String[] scopes = { "ROI Manager", "Image overlay" };
			gd.addRadioButtonGroup("Store Path-ROIs in:", scopes, 2, 1, scopes[0]);

			gd.addMessage("");
			gd.addCheckbox("Color code ROIs by SWC type", false);
			gd.addCheckbox("Discard pre-existing ROIs in Overlay/Manager", true);

			gd.showDialog();
			if (gd.wasCanceled())
				return;

			for (int i = 0; i < PLANES_ID.length; i++)
				destinationPlanes[i] = gd.getNextBoolean();
			final String scope = gd.getNextRadioButton();
			final boolean swcColors = gd.getNextBoolean();
			final boolean reset = gd.getNextBoolean();

			if (scopes[0].equals(scope)) { // ROI Manager

				final Overlay overlay = new Overlay();
				for (int i = 0; i < destinationPlanes.length; i++) {
					if (destinationPlanes[i]) {
						final int lastPlaneIdx = overlay.size() - 1;
						plugin.addPathsToOverlay(overlay, PLANES_ID[i], swcColors);
						if (plugin.singleSlice)
							continue;
						for (int j = lastPlaneIdx + 1; j < overlay.size(); j++) {
							final Roi roi = overlay.get(j);
							roi.setName(roi.getName() + " [" + PLANES_STRING[i] + "]");
						}
					}
				}
				RoiManager rm = RoiManager.getInstance2();
				if (rm == null)
					rm = new RoiManager();
				else if (reset)
					rm.reset();
				Prefs.showAllSliceOnly = !plugin.singleSlice;
				rm.setEditMode(plugin.getImagePlus(), false);
				for (final Roi path : overlay.toArray())
					rm.addRoi(path);
				rm.runCommand("sort");
				rm.setEditMode(plugin.getImagePlus(), true);
				rm.runCommand("show all without labels");

			} else { // Overlay

				String error = "";
				for (int i = 0; i < destinationPlanes.length; i++) {
					if (destinationPlanes[i]) {
						final ImagePlus imp = getImagePlusFromPane(PLANES_ID[i]);
						if (imp == null) {
							error += PLANES_STRING[i] + ", ";
							continue;
						}
						Overlay overlay = imp.getOverlay();
						if (overlay == null) {
							overlay = new Overlay();
							imp.setOverlay(overlay);
						} else if (reset)
							overlay.clear();
						plugin.addPathsToOverlay(overlay, PLANES_ID[i], swcColors);
					}
				}
				if (!error.isEmpty()) {
					SNT.error("Some ROIs were skipped because some images (" + error.substring(0, error.length() - 2)
							+ ") are no longer available.\nPlease consider exporting to the ROI Manager instead.");
				}
			}

		} else if (source == cancelSearch) {

			if (currentState == SEARCHING) {
				updateStatusText("Cancelling path search...");
				plugin.cancelSearch(false);
			} else if (currentState == CALCULATING_GAUSSIAN) {
				updateStatusText("Cancelling Gaussian generation...");
				plugin.cancelGaussian();
			} else {
				SNT.error("BUG! (wrong state for cancelling...)");
			}

		} else if (source == keepSegment) {

			plugin.confirmTemporary();

		} else if (source == junkSegment) {

			plugin.cancelTemporary();

		} else if (source == completePath) {

			plugin.finishedPath();

		} else if (source == cancelPath) {

			plugin.cancelPath();

		} else if (source == quitMenuItem) {

			exitRequested();

		} else if (source == showOrHidePathList) {

			togglePathListVisibility();

		} else if (source == showOrHideFillList) {

			toggleFillListVisibility();

		} else if (source == editSigma) {

			double newSigma = -1;
			double newMultiplier = -1;
			while (newSigma <= 0) {
				final GenericDialog gd = new GenericDialog("Select Scale of Structures");
				gd.setInsets(10, 0, 0);
				gd.addMessage("Please enter the approximate radius of the structures you are looking for:");
				gd.addNumericField("Sigma: ", plugin.getMinimumSeparation(), 4, 6,
						"(The default is the minimum voxel separation)");
				gd.setInsets(10, 0, 0);
				gd.addMessage("Please enter the scaling factor to apply:");
				gd.addNumericField("    Multiplier: ", 4, 4, 6, "(If unsure, just leave this at 4)");
				gd.showDialog();
				if (gd.wasCanceled())
					return;

				newSigma = gd.getNextNumber();
				if (newSigma <= 0) {
					SNT.error("The value of sigma must be positive");
				}

				newMultiplier = gd.getNextNumber();
				if (newMultiplier <= 0) {
					SNT.error("The value of the multiplier must be positive");
				}
			}

			setSigma(newSigma, true);
			setMultiplier(newMultiplier);

		} else if (source == sigmaWizard) {

			preSigmaPaletteState = currentState;
			changeState(WAITING_FOR_SIGMA_POINT);

		} else if (source == colorImageChoice) {

			if (!ignoreColorImageChoiceEvents)
				checkForColorImageChange();

		} else if (source == arrangeWindowsMenuItem) {
			arrangeCanvases();
		}
	}

	private void arrangeDialogs() {
		final GraphicsDevice activeScreen = getGraphicsConfiguration().getDevice();
		final int screenWidth = activeScreen.getDisplayMode().getWidth();
		final int screenHeight = activeScreen.getDisplayMode().getHeight();
		final Rectangle bounds = activeScreen.getDefaultConfiguration().getBounds();

		setLocation(bounds.x, bounds.y);
		pw.setLocation(screenWidth - pw.getWidth(), bounds.y);
		fw.setLocation(bounds.x + getWidth(), screenHeight - fw.getHeight());
	}

	private void arrangeCanvases() {
		final StackWindow xy_window = plugin.getWindow(ThreePanes.XY_PLANE);
		if (xy_window == null)
			return;
		final GraphicsConfiguration xy_config = xy_window.getGraphicsConfiguration();
		final GraphicsDevice xy_screen = xy_config.getDevice();
		final int screenWidth = xy_screen.getDisplayMode().getWidth();
		final int screenHeight = xy_screen.getDisplayMode().getHeight();
		final Rectangle bounds = xy_screen.getDefaultConfiguration().getBounds();

		// Place 3D Viewer at lower right of the screen where image was found
		if (plugin.use3DViewer) {
			final ImageWindow3D uniWindow = plugin.get3DUniverse().getWindow();
			uniWindow.setLocation(bounds.x + screenWidth - uniWindow.getWidth(),
					bounds.y + screenHeight - uniWindow.getHeight());
		}

		// We'll avoid centering the image on the screen it was found to
		// maximize available space. We'll also avoid the upper left of
		// the screen in case dialog an path window are also on this screen.
		int x = bounds.x + this.getX() + this.getWidth();
		if (x > bounds.x + screenWidth / 2 - xy_window.getWidth() / 2)
			x = bounds.x + screenWidth / 2 - xy_window.getWidth() / 2;
		int y = bounds.y + pw.getHeight() / 2;
		if (y > bounds.y + screenHeight / 2 - xy_window.getHeight() / 2)
			y = bounds.y + screenHeight / 2 - xy_window.getHeight() / 2;
		xy_window.setLocation(x, y);

		final StackWindow zy_window = plugin.getWindow(ThreePanes.ZY_PLANE);
		if (zy_window != null) {
			zy_window.setLocation(x + xy_window.getWidth(), y);
			zy_window.toFront();
		}
		final StackWindow xz_window = plugin.getWindow(ThreePanes.XZ_PLANE);
		if (xz_window != null) {
			xz_window.setLocation(x, y + xy_window.getHeight());
			xz_window.toFront();
		}
		xy_window.toFront();
	}

	private void toggleWindowVisibility(final int pane, final JCheckBoxMenuItem menuItem, final boolean setVisible) {
		if (getImagePlusFromPane(pane) == null) {
			SNT.error("Image closed: Pane is no longer accessible.");
			menuItem.setEnabled(false);
			menuItem.setSelected(false);
		} else { // NB: WindowManager list won't be notified
			plugin.getWindow(pane).setVisible(setVisible);
		}
	}

	private ImagePlus getImagePlusFromPane(final int pane) {
		final StackWindow win = plugin.getWindow(pane);
		return (win == null) ? null : win.getImagePlus();
	}

	private boolean noPathsError() {
		final boolean noPaths = pathAndFillManager.size() == 0;
		if (noPaths) {
			SNT.error("There are no traced paths.");
		}
		return noPaths;
	}

	@Override
	public void sigmaPaletteClosing() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				changeState(preSigmaPaletteState);
				setSigma(currentSigma, true);
			}
		});
	}

	protected void setPathListVisible(final boolean makeVisible, final boolean toFront) {
		assert SwingUtilities.isEventDispatchThread();
		if (makeVisible) {
			showOrHidePathList.setText(" Hide Path List");
			pw.setVisible(true);
			if (toFront)
				pw.toFront();
		} else {
			showOrHidePathList.setText("Show Path List");
			pw.setVisible(false);
		}
	}

	protected void togglePathListVisibility() {
		assert SwingUtilities.isEventDispatchThread();
		synchronized (pw) {
			setPathListVisible(!pw.isVisible(), true);
		}
	}

	protected void setFillListVisible(final boolean makeVisible) {
		assert SwingUtilities.isEventDispatchThread();
		if (makeVisible) {
			showOrHideFillList.setText(" Hide Fill List");
			fw.setVisible(true);
			fw.toFront();
		} else {
			showOrHideFillList.setText("Show Fill List");
			fw.setVisible(false);
		}
	}

	protected void toggleFillListVisibility() {
		assert SwingUtilities.isEventDispatchThread();
		synchronized (fw) {
			setFillListVisible(!fw.isVisible());
		}
	}

	public void thresholdChanged(final double f) {
		fw.thresholdChanged(f);
	}

	public boolean nearbySlices() {
		assert SwingUtilities.isEventDispatchThread();
		return (viewPathChoice.getSelectedIndex() > 0);
	}

	@Override
	public void itemStateChanged(final ItemEvent e) {
		assert SwingUtilities.isEventDispatchThread();

		final Object source = e.getSource();

		if (source == enforceDefaultColors) {

			plugin.displayCustomPathColors = !enforceDefaultColors.isSelected();
			plugin.repaintAllPanes();
			plugin.update3DViewerContents();

		} else if (source == viewPathChoice) {

			plugin.justDisplayNearSlices(nearbySlices(), getEitherSide());
			nearbyField.setEnabled(nearbySlices());

		} else if (source == useTubularGeodesics) {

			plugin.enableTubularGeodesicsTracing(useTubularGeodesics.isSelected());

		} else if (source == useSnapWindow) {

			plugin.enableSnapCursor(useSnapWindow.isSelected());

		} else if (source == preprocess && !ignorePreprocessEvents) {

			if (preprocess.isSelected())
				turnOnHessian();
			else {
				plugin.enableHessian(false);
				// changeState(preGaussianState);
			}

		} else if (source == usePreprocessed) {

			if (usePreprocessed.isSelected()) {
				preprocess.setSelected(false);
			}

		} else if (source == justShowSelected) {

			plugin.setShowOnlySelectedPaths(justShowSelected.isSelected());

		} else if (source == paths3DChoice) {

			final int selectedIndex = paths3DChoice.getSelectedIndex();
			plugin.setPaths3DDisplay(selectedIndex + 1);

		} else if (source == mipOverlayMenuItem) {

			plugin.showMIPOverlays(e.getStateChange() == ItemEvent.SELECTED);

		} else if (source == drawDiametersXYMenuItem) {

			plugin.setDrawDiametersXY(e.getStateChange() == ItemEvent.SELECTED);

		} else if (source == autoActivationMenuItem) {

			plugin.enableAutoActivation(e.getStateChange() == ItemEvent.SELECTED);

		} else if (source == xyCanvasMenuItem && xyCanvasMenuItem.isEnabled()) {
			toggleWindowVisibility(ThreePanes.XY_PLANE, xyCanvasMenuItem, e.getStateChange() == ItemEvent.DESELECTED);
		} else if (source == zyCanvasMenuItem && zyCanvasMenuItem.isEnabled()) {
			toggleWindowVisibility(ThreePanes.ZY_PLANE, zyCanvasMenuItem, e.getStateChange() == ItemEvent.DESELECTED);
		} else if (source == xzCanvasMenuItem && xzCanvasMenuItem.isEnabled()) {
			toggleWindowVisibility(ThreePanes.XZ_PLANE, xzCanvasMenuItem, e.getStateChange() == ItemEvent.DESELECTED);
		} else if (plugin.use3DViewer && source == threeDViewerMenuItem && threeDViewerMenuItem.isEnabled()) {
			plugin.get3DUniverse().getWindow().setVisible(e.getStateChange() == ItemEvent.DESELECTED);
		}
	}

	@Override
	public void stateChanged(final ChangeEvent e) {
		final Object source = e.getSource();
		if (source == snapWindowXYsizeSpinner) {
			plugin.cursorSnapWindowXY = (int) snapWindowXYsizeSpinner.getValue() / 2;
		} else if (source == snapWindowZsizeSpinner) {
			plugin.cursorSnapWindowZ = (int) snapWindowZsizeSpinner.getValue() / 2;
		}
	}

	@Override
	public void paint(final Graphics g) {
		super.paint(g);
	}

	volatile boolean reportedInvalid;

	protected int getEitherSide() {
		assert SwingUtilities.isEventDispatchThread();

		final String s = nearbyField.getText();
		if (s.equals("")) {
			reportedInvalid = false;
			return 0;
		}

		try {
			final int e = Integer.parseInt(s);
			if (e < 0) {
				if (!reportedInvalid) {
					SNT.error("The number of slices either side cannot be negative.");
					reportedInvalid = true;
					return 0;
				}
			}
			if (e > plugin.depth) {
				SNT.error("The no. of slices either side should not be higher than image's depth.");
				return plugin.depth;
			}
			reportedInvalid = false;
			return e;

		} catch (final NumberFormatException nfe) {
			if (!reportedInvalid) {
				SNT.error("The number of slices either side must be a non-negative integer.");
				reportedInvalid = true;
				return 0;
			}
			return 0;
		}

	}

	@Override
	public void textValueChanged(final TextEvent e) {
		assert SwingUtilities.isEventDispatchThread();
		plugin.justDisplayNearSlices(nearbySlices(), getEitherSide());
	}

	private JMenu tracingMenu() {
		final JMenu tracingMenu = new JMenu("Tracing");
		autoActivationMenuItem = new JCheckBoxMenuItem("Activate Canvas on Mouse Hovering",
				plugin.autoCanvasActivation);
		autoActivationMenuItem.addItemListener(this);
		tracingMenu.add(autoActivationMenuItem);
		tracingMenu.addSeparator();
		drawDiametersXYMenuItem = new JCheckBoxMenuItem("Draw Diameters in XY View", plugin.getDrawDiametersXY());
		drawDiametersXYMenuItem.addItemListener(this);
		tracingMenu.add(drawDiametersXYMenuItem);
		final String opacityLabel = "Show MIP Overlay(s) at " + SimpleNeuriteTracer.OVERLAY_OPACITY_PERCENT
				+ "% Opacity";
		mipOverlayMenuItem = new JCheckBoxMenuItem(opacityLabel);
		mipOverlayMenuItem.setEnabled(!plugin.singleSlice);
		mipOverlayMenuItem.addItemListener(this);
		tracingMenu.add(mipOverlayMenuItem);
		tracingMenu.addSeparator();
		final JMenuItem optionsMenuItem = new JMenuItem("Options...");
		optionsMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				new SNTPrefs(plugin).promptForOptions();
			}
		});
		tracingMenu.add(optionsMenuItem);

		return tracingMenu;
	}

	private JMenu helpMenu() {
		final JMenu helpMenu = new JMenu("Help");
		final String URL = "http://imagej.net/Simple_Neurite_Tracer";
		JMenuItem mi = menuItemTrigerringURL("Main documentation page", URL);
		helpMenu.add(mi);
		helpMenu.addSeparator();
		mi = menuItemTrigerringURL("Tutorials", URL + "#Tutorials");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("Basic instructions", URL + ":_Basic_Instructions");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("Step-by-step instructions", URL + ":_Step-By-Step_Instructions");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("Filling out processes", URL + ":_Basic_Instructions#Filling_Out_Neurons");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("3D interaction", URL + ":_3D_Interaction");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("Tubular Geodesics", URL + ":_Tubular_Geodesics");
		helpMenu.add(mi);
		helpMenu.addSeparator();
		mi = menuItemTrigerringURL("List of shortcuts", URL + ":_Key_Shortcuts");
		helpMenu.add(mi);
		helpMenu.addSeparator();
		// mi = menuItemTrigerringURL("Sholl analysis walkthrough", URL +
		// ":_Sholl_analysis");
		// helpMenu.add(mi);
		// helpMenu.addSeparator();
		mi = menuItemTrigerringURL("Ask a question", "http://forum.imagej.net");
		helpMenu.add(mi);
		helpMenu.addSeparator();
		mi = menuItemTrigerringURL("Citing SNT...", URL + "#Citing_Simple_Neurite_Tracer");
		helpMenu.add(mi);
		return helpMenu;
	}

	private JMenuItem shollAnalysisHelpMenuItem() {
		JMenuItem mi;
		mi = new JMenuItem("Sholl Analysis...");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final Thread newThread = new Thread(new Runnable() {
					@Override
					public void run() {
						if (noPathsError())
							return;
						String modKey = IJ.isMacOSX() ? "Alt" : "Ctrl";
						modKey += "+Shift";
						final String url1 = Sholl_Analysis.URL + "#Analysis_of_Traced_Cells";
						final String url2 = "http://imagej.net/Simple_Neurite_Tracer:_Sholl_analysis";
						final StringBuilder sb = new StringBuilder();
						sb.append("<html>");
						sb.append("<div WIDTH=390>");
						sb.append("To initiate <a href='").append(Sholl_Analysis.URL).append("'>Sholl Analysis</a>, ");
						sb.append("you must first select a focal point:");
						sb.append("<ol>");
						sb.append("<li>Mouse over the path of interest. Press \"G\" to activate it</li>");
						sb.append("<li>Press \"").append(modKey).append("\" to select a point along the path</li>");
						sb.append("<li>Press \"").append(modKey).append("+A\" to start analysis</li>");
						sb.append("</ol>");
						sb.append("A detailed walkthrough of this procedure is <a href='").append(url2)
								.append("'>available online</a>. ");
						sb.append("For batch processing, run <a href='").append(url1)
								.append("'>Analyze>Sholl>Sholl Analysis (Tracings)...</a>. ");
						new HTMLDialog("Sholl Analysis How-to", sb.toString(), false);
					}
				});
				newThread.start();
			}
		});
		return mi;
	}

	private JMenuItem menuItemTrigerringURL(final String label, final String URL) {
		final JMenuItem mi = new JMenuItem(label);
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.runPlugIn("ij.plugin.BrowserLauncher", URL);
			}
		});
		return mi;
	}

}
