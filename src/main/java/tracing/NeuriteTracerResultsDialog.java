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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
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
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import features.SigmaPalette;
import tracing.FillWindow;
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
import tracing.gui.ColorChangedListener;
import tracing.gui.ColorChooserButton;
import tracing.gui.GuiUtils;

@SuppressWarnings("serial")
public class NeuriteTracerResultsDialog extends JDialog implements ActionListener, WindowListener, ItemListener,
		TextListener, SigmaPalette.SigmaPaletteListener, ImageListener, ChangeListener {

	public static final boolean verbose = SNT.isDebugMode();

	private PathWindow pw;
	private FillWindow fw;
	private SNTPrefs prefs;

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

	protected JCheckBox enforceDefaultColors;
	protected JComboBox<String> colorImageChoice;
	protected JComboBox<String> filterChoice;

	protected String noColorImageString = "[None]";
	protected ImagePlus currentColorImage;

	protected JCheckBox justShowSelected;

	//protected JComboBox<String> paths3DChoice;
	protected String[] paths3DChoicesStrings = { "BUG", "As surface reconstructions", "As lines",
			"As lines and discs" };

	//protected JCheckBox useTubularGeodesics;

	protected JCheckBox displayFiltered = new JCheckBox();
	protected JCheckBox preprocess = new JCheckBox();
	protected JCheckBox usePreprocessed = new JCheckBox();;

	protected volatile double currentSigma;
	protected volatile double currentMultiplier;

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
				final Calibration colorImageCalibration = intendedColorImage
					.getCalibration();
				if (!SNT.similarCalibrations(calibration, colorImageCalibration)) {
					SNT.error("Warning: the calibration of '" + intendedColorImage
						.getTitle() + "' is different from the image you're tracing ('" +
						image.getTitle() + "')'\nThis may produce unexpected results.");
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

	private void updateLabel() {
		assert SwingUtilities.isEventDispatchThread();
		preprocess.setText("Hessian-based analysis (\u03C3 = " + SNT.formatDouble(
			currentSigma) + ", \u00D7 = " + SNT.formatDouble(currentMultiplier) +
			")");
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
		prefs.savePluginPrefs();
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

					cancelSearch.setEnabled(false);
					keepSegment.setEnabled(true);
					junkSegment.setEnabled(true);

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

					cancelSearch.setText("Cancel [ESC]");
					cancelSearch.setEnabled(true);
					keepSegment.setEnabled(true);
					junkSegment.setEnabled(true);

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


	//protected boolean launchedByArchive;

	public NeuriteTracerResultsDialog(final String title, final SimpleNeuriteTracer plugin) {

		super(plugin.legacyService.getIJ1Helper().getIJ(), title, false);
		assert SwingUtilities.isEventDispatchThread();

		new ClarifyingKeyListener().addKeyAndContainerListenerRecursively(this);

		this.plugin = plugin;
		prefs = plugin.prefs;
		pathAndFillManager = plugin.getPathAndFillManager();
		addWindowListener(this);

		setJMenuBar(createMenuBar());

		JTabbedPane tabbedPane=new JTabbedPane();
		tabbedPane.setBackground(getContentPane().getBackground());
		tabbedPane.setBorder(new EmptyBorder(0,0,0,0));

		JPanel main = new JPanel();
		main.setBackground(getContentPane().getBackground());
		main.setLayout(new GridBagLayout());
		final GridBagConstraints c = GuiUtils.singleColumnConstrains();

		//addSeparator(main, "Actions:", false, c);
		//++c.gridy;
		assembleStatusPanel();
		main.add(statusPanel, c);
		c.insets = new Insets(4, 8, 8, 8);
		++c.gridy;
		addSeparator(main, "Cursor snapping:", true, c);
		++c.gridy;
		main.add(snappingPanel(), c);
		++c.gridy;
		addSeparator(main, "Curvatures:", true, c);
		++c.gridy;
		main.add(hessianPanel(), c);
		++c.gridy;
		addSeparator(main, "Additional Segmentation Threads:", true, c);
		++c.gridy;
		main.add(filteringPanel(), c);
		++c.gridy;
		addSeparator(main, "Path Rendering:", true, c);
		++c.gridy;
		main.add(renderingPanel(), c);
		++c.gridy;
		addSeparator(main, "Path Labelling:", false, c);
		++c.gridy;
		main.add(colorOptionsPanel(), c);

		c.fill = GridBagConstraints.HORIZONTAL;
		++c.gridy;
		main.add(bottomPanel(), c);
    tabbedPane.addTab("Main",main);
    
    
    
	JPanel advanced = new JPanel();
	advanced.setLayout(new GridBagLayout());
	final GridBagConstraints c2 = GuiUtils.singleColumnConstrains();
	
	final JPanel channelOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

	channelOptionsPanel.add(leftAlignedLabel("Tracing channel:", true));
	final SpinnerModel nearbyModel = new SpinnerNumberModel(plugin.depth==1?1:2, 1, plugin.depth, 1);
	final JSpinner nearbySpinner = new JSpinner(nearbyModel);
	final JFormattedTextField textfield = ((DefaultEditor) nearbySpinner.getEditor()).getTextField();
	textfield.setEditable(true);
	nearbySpinner.setEnabled(isStackAvailable());
	nearbySpinner.addChangeListener(this);
	channelOptionsPanel.add(nearbySpinner);
	nearbyField = new TextField("2", 2);	
	
	
	
	JCheckBox channel = new JCheckBox("Tracing hannel", plugin.snapCursor);
	channel.setBorder(new EmptyBorder(0, 0, 0, 0));
	channel.addItemListener(this);
	channelOptionsPanel.add(channel);

	++c2.gridy;
	advanced.add(channelOptionsPanel, c2);
  tabbedPane.addTab("Advanced",advanced);
    
    
    
    
    
   // tabbedPane.addTab("Advanced", hideWindowsPanel);
    getContentPane().add(tabbedPane);

		pack();

		pw = new PathWindow(pathAndFillManager, plugin, getX() + getWidth(), getY());
		pathAndFillManager.addPathAndFillListener(pw);

		fw = new FillWindow(pathAndFillManager, plugin, getX() + getWidth(), getY() + pw.getHeight());
		pathAndFillManager.addPathAndFillListener(fw);

		changeState(WAITING_TO_START_PATH);
	}

	private JPanel statusButtonPanel() {
		final JPanel statusChoicesPanel = new JPanel();
		statusChoicesPanel.setLayout(new GridLayout(2,3,0,0));
		keepSegment = GuiUtils.smallButton("<html><b>Y</b>es");
		keepSegment.addActionListener(this);
		statusChoicesPanel.add(keepSegment);
		junkSegment = GuiUtils.smallButton("<html><b>N</b>o");
		junkSegment.addActionListener(this);
		statusChoicesPanel.add(junkSegment);
		cancelSearch = GuiUtils.smallButton("<html><b>Esc</b>. Search");
		cancelSearch.addActionListener(this);
		statusChoicesPanel.add(cancelSearch);
		completePath = GuiUtils.smallButton("<html><b>F</b>inish Path");
		completePath.addActionListener(this);
		statusChoicesPanel.add(completePath);
		cancelPath = GuiUtils.smallButton("<html><b>C</b>ancel Path");
		cancelPath.addActionListener(this);
		statusChoicesPanel.add(cancelPath);
		statusChoicesPanel.add(GuiUtils.smallButton("Skip..."));
		return statusChoicesPanel;
	}

	private void assembleStatusPanel() {
		statusPanel = new JPanel();
		statusPanel.setBorder(new EmptyBorder(0,0,0,0));
		statusPanel.setLayout(new BorderLayout());
		statusText = new JLabel("");
		statusText.setOpaque(true);
		statusText.setForeground(Color.BLACK);
		statusText.setBackground(Color.WHITE);
		updateStatusText("Initial status text");
		statusText.setBorder(new EmptyBorder(5, 5, 5, 5));
		statusPanel.add(statusText, BorderLayout.CENTER);
		statusPanel.add(statusButtonPanel(), BorderLayout.SOUTH);
	}

	private JPanel filteringPanel() {
		final JPanel filteringOptionsPanel = new JPanel();
		filteringOptionsPanel.setLayout(new GridBagLayout());
		final GridBagConstraints oop_f = GuiUtils.singleColumnConstrains();

		filterChoice = new JComboBox<>();
		filterChoice.addItem("None. Use existing image");
		filterChoice.addItem("Frangi Vesselness");
		filterChoice.addItem("Tubeness");
		filterChoice.addItem("Tubular Geodesics");
		filterChoice.addItem("Other...");
		++oop_f.gridy;
		filteringOptionsPanel.add(filterChoice, oop_f);

		displayFiltered = new JCheckBox("Display filter image");
		displayFiltered.addItemListener(this);
		++oop_f.gridy;
		filteringOptionsPanel.add(displayFiltered, oop_f);
		return filteringOptionsPanel;
	}


	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		menuBar.setBackground(getBackground());

		JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);

		menuBar.add(tracingMenu());

		JMenu analysisMenu = new JMenu("Analysis");
		menuBar.add(analysisMenu);

		JMenu viewMenu = new JMenu("View");
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
		return menuBar;
	}

	private JPanel renderingPanel() {
		final JPanel viewOptionsPanel = new JPanel();
		viewOptionsPanel.setLayout(new GridBagLayout());
		final GridBagConstraints vop_c = GuiUtils.singleColumnConstrains();

		final JComboBox<String> showSelectedChoice = new JComboBox<>();
		showSelectedChoice.addItem("Show only selected paths");
		showSelectedChoice.addItem("Show all paths");
		showSelectedChoice.addItemListener(this);

		justShowSelected = new JCheckBox("Show only selected paths", plugin.showOnlySelectedPaths);
		justShowSelected.addItemListener(this);

		++vop_c.gridy;
		viewOptionsPanel.add(justShowSelected, vop_c);
		viewPathChoice = new JComboBox<>();
		viewPathChoice.addItem(projectionChoice);
		viewPathChoice.addItem(partsNearbyChoice);
		viewPathChoice.addItemListener(this);
		viewPathChoice.setEnabled(isStackAvailable());

		final JPanel nearbyPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
		nearbyPanel.add(leftAlignedLabel("(up to ", isStackAvailable()));
		final SpinnerModel nearbyModel = new SpinnerNumberModel(plugin.depth==1?1:2, 1, plugin.depth, 1);
		final JSpinner nearbySpinner = new JSpinner(nearbyModel);
		final JFormattedTextField textfield = ((DefaultEditor) nearbySpinner.getEditor()).getTextField();
		textfield.setEditable(true);
		nearbySpinner.setEnabled(isStackAvailable());
		nearbySpinner.addChangeListener(this);
		nearbyPanel.add(nearbySpinner);

		nearbyField = new TextField("2", 2);
		nearbyPanel.add(leftAlignedLabel(" slices to each side) ", isStackAvailable()));
		++vop_c.gridy;
		vop_c.gridx = 0;
		viewOptionsPanel.add(viewPathChoice, vop_c);
		++vop_c.gridy;
		viewOptionsPanel.add(nearbyPanel, vop_c);
		return viewOptionsPanel;
	}

	private JPanel colorOptionsPanel() {
		final JPanel colorOptionsPanel = new JPanel();
		colorOptionsPanel.setLayout(new GridBagLayout());
		final GridBagConstraints cop_f = GuiUtils.singleColumnConstrains();

		final JComboBox<String> colorChoice = new JComboBox<>();
		colorChoice.addItemListener(this);
		colorChoice.addItem("Default colors");
		colorChoice.addItem("Path's own color");
		++cop_f.gridy;
		colorOptionsPanel.add(colorChoice, cop_f);

		final JPanel colorButtonPanel = new JPanel();
		final ColorChooserButton colorChooser1 = new ColorChooserButton(Color.WHITE,
				"Selected Paths");
		colorChooser1.setName("Color for Selected Paths");
		colorChooser1.addColorChangedListener(new ColorChangedListener() {

			@Override
			public void colorChanged(final Color newColor) {
				System.out.print(colorChooser1);
				System.out.print(newColor);
			}
		});
		final ColorChooserButton colorChooser2 = new ColorChooserButton(Color.RED,
			"  Deselected Paths  ");
		colorChooser2.setName("Color for Deselected Paths");
		//colorChooser2.setBorderPainted(false);
		colorChooser2.addColorChangedListener(new ColorChangedListener() {

			@Override
			public void colorChanged(final Color newColor) {
				System.out.print(colorChooser2);
				System.out.print(newColor);
			}
		});
		colorButtonPanel.add(colorChooser1);
		colorButtonPanel.add(colorChooser2);
		colorButtonPanel.setEnabled(false);

		++cop_f.gridy;
		colorOptionsPanel.add(colorButtonPanel, cop_f);
		return colorOptionsPanel;
	}

	private JPanel snappingPanel() {

		final JPanel tracingOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		useSnapWindow = new JCheckBox("<html><b>S</b>napping within: XY", plugin.snapCursor);
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

	private JPanel hessianPanel() {
		JPanel hessianOptionsPanel = new JPanel();
		hessianOptionsPanel.setLayout(new GridBagLayout());
		final GridBagConstraints oop_c = GuiUtils.singleColumnConstrains();
		preprocess = new JCheckBox();
		setSigma(plugin.getMinimumSeparation(), false);
		setMultiplier(4);
		updateLabel();
		preprocess.addItemListener(this);
		++oop_c.gridy;
		hessianOptionsPanel.add(preprocess, oop_c);

		final JPanel sigmaButtonPanel = new JPanel();
		editSigma = GuiUtils.smallButton("Pick Sigma Manually");
		editSigma.addActionListener(this);
		sigmaButtonPanel.add(editSigma);

		sigmaWizard = GuiUtils.smallButton("Pick Sigma Visually");
		sigmaWizard.addActionListener(this);
		sigmaButtonPanel.add(sigmaWizard);

		++oop_c.gridy;
		hessianOptionsPanel.add(sigmaButtonPanel, oop_c);
		return hessianOptionsPanel;
	}

	private JPanel bottomPanel() {
		final JPanel hideWindowsPanel = new JPanel();
		showOrHidePathList = new JButton("Show / Hide Path List");
		showOrHidePathList.addActionListener(this);
		showOrHideFillList = new JButton("Show / Hide Fill List");
		showOrHideFillList.addActionListener(this);
		hideWindowsPanel.add(showOrHidePathList);
		hideWindowsPanel.add(showOrHideFillList);
		return hideWindowsPanel;
	}

	private void addSeparator(final JComponent component, final String heading, final boolean vgap, final GridBagConstraints c) {
		final Insets previousInsets = c.insets;
		c.insets = new Insets(vgap?8:0, 4, 0, 0);
		final JLabel label = leftAlignedLabel(heading, true);
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));
		component.add(label, c);
		c.insets = previousInsets;
	}

	private JLabel leftAlignedLabel(final String text, final boolean enabled) {
		final JLabel label = new JLabel(text);
		if (!enabled)
			label.setForeground(GuiUtils.getDisabledComponentColor());
		return label;
	}

	protected void displayOnStarting() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (plugin.prefs.isSaveWinLocations())
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
		Point loc = prefs.getPathWindowLocation();
		if (loc != null)
			pw.setLocation(loc);
		loc = prefs.getFillWindowLocation();
		if (loc != null)
			fw.setLocation(loc);
//		final GraphicsDevice activeScreen = getGraphicsConfiguration().getDevice();
//		final int screenWidth = activeScreen.getDisplayMode().getWidth();
//		final int screenHeight = activeScreen.getDisplayMode().getHeight();
//		final Rectangle bounds = activeScreen.getDefaultConfiguration().getBounds();
//
//		setLocation(bounds.x, bounds.y);
//		pw.setLocation(screenWidth - pw.getWidth(), bounds.y);
//		fw.setLocation(bounds.x + getWidth(), screenHeight - fw.getHeight());
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
				prefs.promptForOptions();
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

	public PathWindow getPathWindow() {
		return pw;
	}

	public FillWindow getFillWindow() {
		return fw;
	}

}
