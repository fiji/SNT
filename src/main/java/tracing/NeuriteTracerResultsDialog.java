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
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;
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
import ij.io.FileInfo;
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
public class NeuriteTracerResultsDialog extends JDialog {

	public static final boolean verbose = SNT.isDebugMode();

	/* Deprecated stuff to be removed soon */
	@Deprecated
	private final String noColorImageString = "[None]";
	@Deprecated
	private ImagePlus currentColorImage;
	@Deprecated
	private JComboBox<String> colorImageChoice;

	/* UI */
	protected JComboBox<String> viewPathChoice;
	protected final String projectionChoice = "Projected through all slices";
	protected final String partsNearbyChoice = "Parts in nearby slices [5]";
	private JComboBox<String> filterChoice;
	private JComboBox<String> pathsColorChoice;
	private JCheckBox justShowSelected;
	protected JButton editSigma;
	protected JButton sigmaWizard;
	protected JCheckBox useSnapWindow;
	protected JSpinner snapWindowXYsizeSpinner;
	protected JSpinner snapWindowZsizeSpinner;
	protected JSpinner nearbyFieldSpinner;
	private JCheckBox preprocess;
	private JCheckBox displayFiltered;
	private JButton showOrHidePathList;
	private JButton showOrHideFillList;
	private JMenuItem loadMenuItem;
	private JMenuItem loadLabelsMenuItem;
	private JMenuItem saveMenuItem;
	private JMenuItem exportCSVMenuItem;
	private JMenuItem exportAllSWCMenuItem;
	private JMenuItem quitMenuItem;
	private JMenuItem makeLineStackMenuItem;
	private JMenuItem pathsToROIsMenuItem;
	private JMenuItem exportCSVMenuItemAgain;
	private JMenuItem sendToTrakEM2;
	private JLabel statusText;
	private JLabel statusBarText;
	private JButton keepSegment;
	private JButton junkSegment;
	protected JButton abortButton;
	private JButton completePath;

	private volatile int currentState;
	private volatile double currentSigma;
	private volatile double currentMultiplier;
	private volatile boolean ignoreColorImageChoiceEvents = false;
	private volatile boolean ignorePreprocessEvents = false;
	private volatile int preGaussianState;
	private volatile int preSigmaPaletteState;

	private final SimpleNeuriteTracer plugin;
	private final PathAndFillManager pathAndFillManager;
	private final GuiUtils guiUtils;
	private final PathWindow pw;
	private final FillWindow fw;
	private final SNTPrefs prefs;
	protected final GuiListener listener;

	/* These are the states that the UI can be in: */
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
	static final int IMAGE_CLOSED = -1;

	// TODO: Internal preferences: should be migrated to SNTPrefs
	protected boolean finishOnDoubleConfimation;
	protected boolean discardOnDoubleCancellation;

	public NeuriteTracerResultsDialog(final String title,
		final SimpleNeuriteTracer plugin)
	{

		super(plugin.legacyService.getIJ1Helper().getIJ(), title, false);
		guiUtils = new GuiUtils(this);
		this.plugin = plugin;
		new ClarifyingKeyListener(plugin).addKeyAndContainerListenerRecursively(this);
		listener = new GuiListener();

		assert SwingUtilities.isEventDispatchThread();

		prefs = plugin.prefs;
		pathAndFillManager = plugin.getPathAndFillManager();
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(final WindowEvent e) {
				exitRequested();
			}
		});

		setJMenuBar(createMenuBar());

		final JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setBackground(getContentPane().getBackground());
		tabbedPane.setBorder(new EmptyBorder(0, 0, 0, 0));

		final JPanel main = new JPanel();
		main.setBackground(getContentPane().getBackground());
		main.setLayout(new GridBagLayout());
		final GridBagConstraints c = GuiUtils.defaultGbc();

		final JPanel statusPanel = statusPanel();
		main.add(statusPanel, c);
		c.insets = new Insets(4, 8, 8, 8);
		++c.gridy;
		addSeparator(main, "", true, c); // empty separator
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

		++c.gridy;
		addSeparator(main, "", true, c); // empty separator

		c.fill = GridBagConstraints.HORIZONTAL;
		++c.gridy;
		main.add(bottomPanel(), c);
//		++c.gridy;
//		addSeparator(main, "", true, c);

		tabbedPane.addTab("Main", main);

		final JPanel advanced = new JPanel();
		advanced.setLayout(new GridBagLayout());
		final GridBagConstraints c2 = GuiUtils.defaultGbc();
		c2.anchor = GridBagConstraints.NORTHEAST;
		c2.gridwidth = GridBagConstraints.REMAINDER;
		c2.insets = new Insets(4, 8, 8, 8);
		addSeparator(advanced, "Tracing:", true, c2);
		++c2.gridy;
		advanced.add(advancedTracingPanel(), c2);
		++c2.gridy;
		addSeparator(advanced, "UI Interaction:", true, c2);
		++c2.gridy;
		advanced.add(interactionPanel(), c2);
		++c2.gridy;
		addSeparator(advanced, "Misc:", true, c2);
		++c2.gridy;
		c2.weighty = 1;
		advanced.add(miscPanel(), c2);
		tabbedPane.addTab("Advanced", advanced);
		tabbedPane.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				if (tabbedPane.getSelectedIndex() == 1 && getCurrentState() > 0) {
					tabbedPane.setSelectedIndex(0);
					guiUtils.blinkingError(statusPanel,
						"Please complete current operation before selecting the \"Advanced\" tab.");;
				}
			}
		});

		setLayout(new BorderLayout());
		add(tabbedPane, BorderLayout.NORTH);
		add(statusBar(), BorderLayout.SOUTH);
		pack();

		pw = new PathWindow(pathAndFillManager, plugin, getX() + getWidth(),
			getY());
		pathAndFillManager.addPathAndFillListener(pw);

		fw = new FillWindow(pathAndFillManager, plugin, getX() + getWidth(),
			getY() + pw.getHeight());
		pathAndFillManager.addPathAndFillListener(fw);

		changeState(WAITING_TO_START_PATH);
	}

	public int getCurrentState() {
		return currentState;
	}

	private void updateStatusText(final String newStatus, boolean includeStatusBar) {
		updateStatusText(newStatus);
		showStatus(newStatus);
	}

	private void updateStatusText(final String newStatus) {
		statusText.setText("<html><strong>" + newStatus + "</strong></html>");
	}

	@Deprecated
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
				if (title == oldSelection) selectedIndex = j;
			}
		}

		colorImageChoice.setSelectedIndex(selectedIndex);

		ignoreColorImageChoiceEvents = false;

		// This doesn't trigger an item event
		checkForColorImageChange();
	}

	@Deprecated
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
				if (!(intendedColorImage.getWidth() == image.getWidth() &&
					intendedColorImage.getHeight() == image.getHeight() &&
					intendedColorImage.getStackSize() == image.getStackSize())) SNT.error(
						"Warning: the dimensions (in voxels) of '" + intendedColorImage
							.getTitle() + "' is different from the image you're tracing ('" +
							image.getTitle() + "')'\nThis may produce unexpected results.");
			}
			currentColorImage = intendedColorImage;
			plugin.setColorImage(currentColorImage);
		}
	}

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
						SNT.error(
							"[BUG] The preprocess checkbox should never be on when setSigma is called");
					}
					else {
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
		if (plugin.pathsUnsaved() && guiUtils.getConfirmation(
			"There are unsaved paths. Do you really want to quit?", "Really quit?"))
		{
			plugin.cancelSearch(true);
			plugin.notifyListeners(new SNTEvent(SNTEvent.QUIT));
			prefs.savePluginPrefs();
			pw.dispose();
			fw.dispose();
			dispose();
			plugin.closeAndReset();
		}
	}

	protected void disableImageDependentComponents() {
		assert SwingUtilities.isEventDispatchThread();
		loadMenuItem.setEnabled(false);
		loadLabelsMenuItem.setEnabled(false);
		keepSegment.setEnabled(false);
		junkSegment.setEnabled(false);
		abortButton.setEnabled(false);
		completePath.setEnabled(false);
		cancelPath.setEnabled(false);
		editSigma.setEnabled(false);
		sigmaWizard.setEnabled(false);
		preprocess.setEnabled(false);
		pathsColorChoice.setEnabled(false);
		justShowSelected.setEnabled(false);
		fw.setEnabledNone();
	}

	protected void disableEverything() {
		assert SwingUtilities.isEventDispatchThread();
		disableImageDependentComponents();
		statusText.setEnabled(false);
		viewPathChoice.setEnabled(false);
		exportCSVMenuItem.setEnabled(false);
		exportAllSWCMenuItem.setEnabled(false);
		exportCSVMenuItemAgain.setEnabled(false);
		sendToTrakEM2.setEnabled(false);
		pathsToROIsMenuItem.setEnabled(false);
		saveMenuItem.setEnabled(false);
		quitMenuItem.setEnabled(false);
	}

	public void changeState(final int newState) {

		SNT.debug("changing state to: " + getState(newState));
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				switch (newState) {

					case WAITING_TO_START_PATH:
						updateStatusText("Click somewhere to start a new path...");
						disableEverything();
						pw.valueChanged(null); // Fake a selection change in the path tree:
						viewPathChoice.setEnabled(isStackAvailable());
						nearbyFieldSpinner.setEnabled(nearbySlices());
						preprocess.setEnabled(true);
						editSigma.setEnabled(preprocess.isSelected());
						sigmaWizard.setEnabled(preprocess.isSelected());
						fw.setEnabledWhileNotFilling();
						loadLabelsMenuItem.setEnabled(true);
						saveMenuItem.setEnabled(true);
						loadMenuItem.setEnabled(true);
						exportCSVMenuItem.setEnabled(true);
						exportAllSWCMenuItem.setEnabled(true);
						exportCSVMenuItemAgain.setEnabled(true);
						sendToTrakEM2.setEnabled(plugin.anyListeners());
						pathsToROIsMenuItem.setEnabled(true);
						quitMenuItem.setEnabled(true);
						pathsColorChoice.setEnabled(true);
						justShowSelected.setEnabled(true);
						break;

					case PARTIAL_PATH:
						updateStatusText(
							"Now select a point further along that structure...");
						disableEverything();
						abortButton.setEnabled(false);
						keepSegment.setEnabled(false);
						junkSegment.setEnabled(false);
						if (plugin.justFirstPoint()) completePath.setEnabled(false);
						else completePath.setEnabled(true);
						cancelPath.setEnabled(true);
						viewPathChoice.setEnabled(isStackAvailable());
						preprocess.setEnabled(true);
						editSigma.setEnabled(preprocess.isSelected());
						sigmaWizard.setEnabled(preprocess.isSelected());
						quitMenuItem.setEnabled(false);
						break;

					case SEARCHING:
						updateStatusText("Searching for path between points...");
						disableEverything();
						abortButton.setEnabled(true);
						keepSegment.setEnabled(false);
						junkSegment.setEnabled(false);
						completePath.setEnabled(false);
						cancelPath.setEnabled(false);
						quitMenuItem.setEnabled(true);
						break;

					case QUERY_KEEP:
						if (!plugin.confirmSegments) { // TODO:
							plugin.confirmTemporary();
							break;
						}
						updateStatusText("Keep this new path segment?");
						disableEverything();
						keepSegment.setEnabled(true);
						junkSegment.setEnabled(true);
						abortButton.setEnabled(false);
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
						abortButton.setEnabled(true);
						keepSegment.setEnabled(false);
						junkSegment.setEnabled(false);
						break;

					case WAITING_FOR_SIGMA_POINT:
						updateStatusText("Click on a neuron in the image");
						disableEverything();
						abortButton.setEnabled(true);
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
						SNT.debug("BUG: switching to an unknown state");
						return;
				}

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

	private JPanel advancedTracingPanel() {
		final JPanel tracingPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		gdb.gridwidth = 1;
		final JCheckBox mipOverlayCheckBox = new JCheckBox(
			"Show MIP overlay(s) at " + SimpleNeuriteTracer.OVERLAY_OPACITY_PERCENT +
				"% opacity");
		mipOverlayCheckBox.setEnabled(!plugin.singleSlice);
		mipOverlayCheckBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				plugin.showMIPOverlays(e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		tracingPanel.add(mipOverlayCheckBox, gdb);
		++gdb.gridy;
		final JCheckBox diametersCheckBox = new JCheckBox(
			"Draw diameters in XY view", plugin.getDrawDiametersXY());
		diametersCheckBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				plugin.setDrawDiametersXY(e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		tracingPanel.add(diametersCheckBox, gdb);
		++gdb.gridy;

		// User inputs for multidimensional images
		final boolean hasChannels = plugin.getImagePlus().getNChannels() > 1;
		final boolean hasFrames = plugin.getImagePlus().getNFrames() > 1;

		final JPanel positionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		positionPanel.add(leftAlignedLabel("Channel", hasChannels));
		final JSpinner channelSpinner = GuiUtils.integerSpinner(plugin.channel, 1,
			plugin.getImagePlus().getNChannels() * 100, 1);
		positionPanel.add(channelSpinner);
		positionPanel.add(leftAlignedLabel(" Frame", hasFrames));
		final JSpinner frameSpinner = GuiUtils.integerSpinner(plugin.frame, 1,
			plugin.getImagePlus().getNFrames() * 100, 1);
		positionPanel.add(frameSpinner);
		final JButton applyPositionButton = GuiUtils.smallButton("Apply");
		applyPositionButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				final int newC = (int) channelSpinner.getValue();
				final int newT = (int) frameSpinner.getValue();
				if (newC == plugin.channel && newT == plugin.frame) {
					guiUtils.error("Position C=" + newC + ", T=" + newT +
						" is already being traced.");
					return;
				}
				if (guiUtils.getConfirmation("You are currently tracing position C=" +
					plugin.channel + ", T=" + plugin.frame + ". Start tracing C=" + newC +
					", T=" + newT + "?", "Change Hyperstack Position?")) {
					plugin.reloadImage(newC, newT);
					refreshStatus();
				}
			}
		});
		positionPanel.add(applyPositionButton);
		tracingPanel.add(positionPanel, gdb);
		channelSpinner.setEnabled(hasChannels);
		frameSpinner.setEnabled(hasFrames);
		applyPositionButton.setEnabled(hasChannels || hasFrames);
		return tracingPanel;
	}

	private JPanel interactionPanel() {
		final JPanel intPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		final JCheckBox confirmCheckbox = new JCheckBox("Pressing 'Y' twice finishes path",
			finishOnDoubleConfimation);
		confirmCheckbox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				finishOnDoubleConfimation = (e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		intPanel.add(confirmCheckbox, gdb);
		++gdb.gridy;
		final JCheckBox finishCheckbox = new JCheckBox(
			"Pressing 'N' twice cancels path", discardOnDoubleCancellation);
		confirmCheckbox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				discardOnDoubleCancellation = (e.getStateChange() == ItemEvent.SELECTED);// TODO
			}
		});
		intPanel.add(finishCheckbox, gdb);
		++gdb.gridy;
		final JCheckBox canvasCheckBox = new JCheckBox(
			"Activate canvas on mouse hovering", plugin.autoCanvasActivation);
		canvasCheckBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				plugin.enableAutoActivation(e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		intPanel.add(canvasCheckBox, gdb);
		return intPanel;
	}

	private JPanel miscPanel() {
		final JPanel miscPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		final JCheckBox winLocCheckBox = new JCheckBox(
			"Remember window locations across restarts", plugin.prefs
				.isSaveWinLocations());
		winLocCheckBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				plugin.prefs.setSaveWinLocations(e
					.getStateChange() == ItemEvent.SELECTED);
			}
		});
		miscPanel.add(winLocCheckBox, gdb);
		++gdb.gridy;
		final JCheckBox compressedXMLCheckBox = new JCheckBox(
			"Use compression when saving traces", plugin.useCompressedXML);
		compressedXMLCheckBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				plugin.useCompressedXML = (e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		miscPanel.add(compressedXMLCheckBox, gdb);
		++gdb.gridy;
		final JCheckBox debugCheckBox = new JCheckBox("Debug mode", SNT
			.isDebugMode());
		debugCheckBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				SNT.setDebugMode(e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		miscPanel.add(debugCheckBox, gdb);
		++gdb.gridy;
		final JButton resetbutton = GuiUtils.smallButton("Reset Preferences...");
		resetbutton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				if (guiUtils.getConfirmation(
					"Reset preferences to defaults? (Restart required)", "Reset?"))
				{
					plugin.prefs.resetOptions();
					guiUtils.msg("You should now restart SNT for changes to take effect",
						"Restart required");
				}
			}
		});
		gdb.fill = GridBagConstraints.NONE;
		miscPanel.add(resetbutton, gdb);
		return miscPanel;
	}

	private JPanel statusButtonPanel() {
		final JPanel statusChoicesPanel = new JPanel();
		statusChoicesPanel.setLayout(new GridBagLayout());
		statusChoicesPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.LINE_START;
		gbc.fill = GridBagConstraints.NONE;
		gbc.ipadx = 0;
		gbc.ipady = 0;
		gbc.insets = new Insets(0, 0, 0, 0);
		keepSegment = GuiUtils.smallButton("<html><b>Y</b>es");
		keepSegment.addActionListener(listener);
		keepSegment.setMargin(new Insets(0, 0, 0, 0));
		statusChoicesPanel.add(keepSegment, gbc);
		junkSegment = GuiUtils.smallButton("<html><b>N</b>o");
		junkSegment.setMargin(new Insets(0, 0, 0, 0));
		junkSegment.addActionListener(listener);
		gbc.gridx = 1;
		statusChoicesPanel.add(junkSegment, gbc);
		completePath = GuiUtils.smallButton("<html><b>F</b>inish");
		completePath.addActionListener(listener);
		completePath.setMargin(new Insets(0, 0, 0, 0));
		gbc.gridx = 2;
		statusChoicesPanel.add(completePath, gbc);
		gbc.gridx = 3;
		abortButton = GuiUtils.smallButton("<html><b>C</b>ancel");
		//abortButton.setToolTipText("<html>Shortcuts: <tt>ESC </tt> or <tt>C</tt>");
		abortButton.setMargin(new Insets(0, 0, 0, 0));
		abortButton.addActionListener(listener);
		gbc.gridx = 4;
		statusChoicesPanel.add(abortButton, gbc);
		return statusChoicesPanel;
	}

	private JPanel statusPanel() {
		final JPanel statusPanel = new JPanel();
		statusPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
		statusPanel.setLayout(new BorderLayout());
		statusText = new JLabel("");
		statusText.setOpaque(true);
		statusText.setBackground(Color.WHITE);
		//statusText.setText("Initial status text");
		statusText.setBorder(BorderFactory.createCompoundBorder(BorderFactory
			.createBevelBorder(BevelBorder.LOWERED), BorderFactory.createEmptyBorder(
				5, 5, 5, 5)));
		statusPanel.add(statusText, BorderLayout.CENTER);
		final JPanel buttonPanel = statusButtonPanel();
		statusPanel.add(buttonPanel, BorderLayout.SOUTH);
		return statusPanel;
	}

	private JPanel filteringPanel() {
		final JPanel filteringOptionsPanel = new JPanel();
		filteringOptionsPanel.setLayout(new GridBagLayout());
		final GridBagConstraints oop_f = GuiUtils.defaultGbc();

		filterChoice = new JComboBox<>();
		filterChoice.addItem("None. Use existing image");
		filterChoice.addItem("Frangi Vesselness");
		filterChoice.addItem("Tubeness");
		filterChoice.addItem("Tubular Geodesics");
		filterChoice.addItem("Other...");
		++oop_f.gridy;
		filteringOptionsPanel.add(filterChoice, oop_f);

		displayFiltered = new JCheckBox("Display filter image");
		displayFiltered.addItemListener(listener);
		++oop_f.gridy;
		filteringOptionsPanel.add(displayFiltered, oop_f);
		return filteringOptionsPanel;
	}

	private JMenuBar createMenuBar() {
		final JMenuBar menuBar = new JMenuBar();
		final JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);
		final JMenu analysisMenu = new JMenu("Analysis");
		menuBar.add(analysisMenu);
		final JMenu viewMenu = new JMenu("View");
		menuBar.add(viewMenu);
		menuBar.add(helpMenu());

		loadMenuItem = new JMenuItem("Load Traces / (e)SWC File...");
		loadMenuItem.addActionListener(listener);
		fileMenu.add(loadMenuItem);
		loadLabelsMenuItem = new JMenuItem("Load Labels (AmiraMesh) File...");
		loadLabelsMenuItem.addActionListener(listener);
		fileMenu.add(loadLabelsMenuItem);
		fileMenu.addSeparator();
		saveMenuItem = new JMenuItem("Save Traces File...");
		saveMenuItem.addActionListener(listener);
		fileMenu.add(saveMenuItem);
		exportAllSWCMenuItem = new JMenuItem("Save All Paths as SWC...");
		exportAllSWCMenuItem.addActionListener(listener);
		fileMenu.add(exportAllSWCMenuItem);
		fileMenu.addSeparator();
		exportCSVMenuItem = new JMenuItem("Export Path Properties...");
		exportCSVMenuItem.addActionListener(listener);
		fileMenu.add(exportCSVMenuItem);
		sendToTrakEM2 = new JMenuItem("Send to TrakEM2");
		sendToTrakEM2.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				plugin.notifyListeners(new SNTEvent(SNTEvent.SEND_TO_TRAKEM2));
			}
		});
		fileMenu.add(sendToTrakEM2);
		fileMenu.addSeparator();
		quitMenuItem = new JMenuItem("Quit");
		quitMenuItem.addActionListener(listener);
		fileMenu.add(quitMenuItem);

		pathsToROIsMenuItem = new JMenuItem("Convert Paths to ROIs...");
		pathsToROIsMenuItem.addActionListener(listener);
		analysisMenu.add(pathsToROIsMenuItem);
		analysisMenu.addSeparator();
		exportCSVMenuItemAgain = new JMenuItem("Measure Paths...");
		exportCSVMenuItemAgain.addActionListener(listener);
		analysisMenu.add(exportCSVMenuItemAgain);
		makeLineStackMenuItem = new JMenuItem(
			"Render/Analyze Skeletonized Paths...");
		makeLineStackMenuItem.addActionListener(listener);
		analysisMenu.add(makeLineStackMenuItem);
		final JMenuItem correspondencesMenuItem = new JMenuItem(
			"Show correspondences with file..");
		correspondencesMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				final File tracesFile = guiUtils.openFile("Select other traces file...",
					null);
				if (tracesFile == null) return;
				if (!tracesFile.exists()) {
					guiUtils.error(tracesFile.getAbsolutePath() + " is not available");
					return;
				}
				// FIXME: 3D VIEWER exclusive method
				plugin.showCorrespondencesTo(tracesFile, Color.YELLOW, 2.5);
			}
		});
		analysisMenu.add(correspondencesMenuItem);
		analysisMenu.addSeparator();
		analysisMenu.add(shollAnalysisHelpMenuItem());

		final JCheckBoxMenuItem xyCanvasMenuItem = new JCheckBoxMenuItem(
			"Hide XY View");
		xyCanvasMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleWindowVisibility(MultiDThreePanes.XY_PLANE, xyCanvasMenuItem);
			}
		});
		viewMenu.add(xyCanvasMenuItem);
		final JCheckBoxMenuItem zyCanvasMenuItem = new JCheckBoxMenuItem(
			"Hide ZY View");
		zyCanvasMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleWindowVisibility(MultiDThreePanes.ZY_PLANE, zyCanvasMenuItem);
			}
		});
		viewMenu.add(zyCanvasMenuItem);
		final JCheckBoxMenuItem xzCanvasMenuItem = new JCheckBoxMenuItem(
			"Hide XZ View");
		xzCanvasMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleWindowVisibility(MultiDThreePanes.XZ_PLANE, xzCanvasMenuItem);
			}
		});
		viewMenu.add(xzCanvasMenuItem);
		final JCheckBoxMenuItem threeDViewerMenuItem = new JCheckBoxMenuItem(
			"Hide 3D View");
		threeDViewerMenuItem.setEnabled(plugin.use3DViewer);
		threeDViewerMenuItem.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				if (plugin.get3DUniverse() != null) plugin.get3DUniverse().getWindow()
					.setVisible(e.getStateChange() == ItemEvent.DESELECTED);
			}
		});
		viewMenu.add(threeDViewerMenuItem);
		viewMenu.addSeparator();
		final JMenuItem arrangeWindowsMenuItem = new JMenuItem("Arrange Views");
		arrangeWindowsMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				arrangeCanvases();
			}
		});
		viewMenu.add(arrangeWindowsMenuItem);
		return menuBar;
	}

	private JPanel renderingPanel() {
		final JPanel viewOptionsPanel = new JPanel();
		viewOptionsPanel.setLayout(new GridBagLayout());
		final GridBagConstraints vop_c = GuiUtils.defaultGbc();

		justShowSelected = new JCheckBox("Show only selected paths",
			plugin.showOnlySelectedPaths);
		justShowSelected.addItemListener(listener);

		++vop_c.gridy;
		viewOptionsPanel.add(justShowSelected, vop_c);
		viewPathChoice = new JComboBox<>();
		viewPathChoice.addItem(projectionChoice);
		viewPathChoice.addItem(partsNearbyChoice);
		viewPathChoice.addItemListener(listener);
		viewPathChoice.setEnabled(isStackAvailable());

		final JPanel nearbyPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
		nearbyPanel.add(leftAlignedLabel("(up to ", isStackAvailable()));
		nearbyFieldSpinner = GuiUtils.integerSpinner(plugin.depth == 1 ? 1 : 2, 1,
			plugin.depth, 1);
		nearbyFieldSpinner.setEnabled(isStackAvailable());
		nearbyFieldSpinner.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				plugin.justDisplayNearSlices(nearbySlices(), (int) nearbyFieldSpinner
					.getValue());
			}
		});
		nearbyPanel.add(nearbyFieldSpinner);
		nearbyPanel.add(leftAlignedLabel(" slices to each side) ",
			isStackAvailable()));
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
		final GridBagConstraints cop_f = GuiUtils.defaultGbc();

		final JPanel colorButtonPanel = new JPanel();
		final ColorChooserButton colorChooser1 = new ColorChooserButton(
			plugin.selectedColor, "Selected Paths");
		colorChooser1.setName("Color for Selected Paths");
		colorChooser1.addColorChangedListener(new ColorChangedListener() {

			@Override
			public void colorChanged(final Color newColor) {
				plugin.setSelectedColor(newColor);
			}
		});
		final ColorChooserButton colorChooser2 = new ColorChooserButton(
			plugin.deselectedColor, "  Deselected Paths  ");
		colorChooser2.setName("Color for Deselected Paths");
		colorChooser2.addColorChangedListener(new ColorChangedListener() {

			@Override
			public void colorChanged(final Color newColor) {
				plugin.setDeselectedColor(newColor);
			}
		});
		colorButtonPanel.add(colorChooser1);
		colorButtonPanel.add(colorChooser2);

		pathsColorChoice = new JComboBox<>();
		pathsColorChoice.addItemListener(listener);
		pathsColorChoice.addItem("Default colors");
		pathsColorChoice.addItem("Path Manager colors");
		pathsColorChoice.setSelectedIndex(plugin.displayCustomPathColors ? 1 : 0);
		pathsColorChoice.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				plugin.displayCustomPathColors = !"Default colors".equals(
					pathsColorChoice.getSelectedItem());
				colorChooser1.setEnabled(!plugin.displayCustomPathColors);
				colorChooser2.setEnabled(!plugin.displayCustomPathColors);
				plugin.repaintAllPanes();
				plugin.update3DViewerContents();
			}
		});
		++cop_f.gridy;
		colorOptionsPanel.add(pathsColorChoice, cop_f);

		++cop_f.gridy;
		colorOptionsPanel.add(colorButtonPanel, cop_f);
		return colorOptionsPanel;
	}

	private JPanel snappingPanel() {

		final JPanel tracingOptionsPanel = new JPanel(new FlowLayout(
			FlowLayout.LEFT));
		useSnapWindow = new JCheckBox("<html>Enable <b>S</b>napping within: XY",
			plugin.snapCursor);
		useSnapWindow.setBorder(new EmptyBorder(0, 0, 0, 0));
		useSnapWindow.addItemListener(listener);
		tracingOptionsPanel.add(useSnapWindow);

		snapWindowXYsizeSpinner = GuiUtils.integerSpinner(
			plugin.cursorSnapWindowXY * 2,
			SimpleNeuriteTracer.MIN_SNAP_CURSOR_WINDOW_XY,
			SimpleNeuriteTracer.MAX_SNAP_CURSOR_WINDOW_XY * 2, 2);
		snapWindowXYsizeSpinner.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				plugin.cursorSnapWindowXY = (int) snapWindowXYsizeSpinner.getValue() /
					2;
			}
		});
		tracingOptionsPanel.add(snapWindowXYsizeSpinner);

		final JLabel z_spinner_label = leftAlignedLabel("Z", isStackAvailable());
		z_spinner_label.setBorder(new EmptyBorder(0, 2, 0, 0));
		tracingOptionsPanel.add(z_spinner_label);
		snapWindowZsizeSpinner = GuiUtils.integerSpinner(plugin.cursorSnapWindowZ *
			2, SimpleNeuriteTracer.MIN_SNAP_CURSOR_WINDOW_Z,
			SimpleNeuriteTracer.MAX_SNAP_CURSOR_WINDOW_Z * 2, 2);
		snapWindowZsizeSpinner.setEnabled(isStackAvailable());
		snapWindowZsizeSpinner.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				plugin.cursorSnapWindowZ = (int) snapWindowZsizeSpinner.getValue() / 2;
			}
		});
		tracingOptionsPanel.add(snapWindowZsizeSpinner);
		return tracingOptionsPanel;
	}

	private JPanel hessianPanel() {
		final JPanel hessianOptionsPanel = new JPanel();
		hessianOptionsPanel.setLayout(new GridBagLayout());
		final GridBagConstraints oop_c = GuiUtils.defaultGbc();
		preprocess = new JCheckBox();
		setSigma(plugin.getMinimumSeparation(), false);
		setMultiplier(4);
		updateLabel();
		preprocess.addItemListener(listener);
		++oop_c.gridy;
		hessianOptionsPanel.add(preprocess, oop_c);

		final JPanel sigmaButtonPanel = new JPanel();
		editSigma = GuiUtils.smallButton("Pick Sigma Manually");
		editSigma.addActionListener(listener);
		sigmaButtonPanel.add(editSigma);

		sigmaWizard = GuiUtils.smallButton("Pick Sigma Visually");
		sigmaWizard.addActionListener(listener);
		sigmaButtonPanel.add(sigmaWizard);

		++oop_c.gridy;
		hessianOptionsPanel.add(sigmaButtonPanel, oop_c);
		return hessianOptionsPanel;
	}

	private JPanel bottomPanel() {
		final JPanel hideWindowsPanel = new JPanel();
		showOrHidePathList = new JButton("Show / Hide Path List");
		showOrHidePathList.addActionListener(listener);
		showOrHideFillList = new JButton("Show / Hide Fill List");
		showOrHideFillList.addActionListener(listener);
		hideWindowsPanel.add(showOrHidePathList);
		hideWindowsPanel.add(showOrHideFillList);
		return hideWindowsPanel;
	}

	private JPanel statusBar() {
		final JPanel statusBar = new JPanel();
		statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
		statusBar.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 0));
		statusBarText = leftAlignedLabel("", false);
		statusBar.add(statusBarText);
		refreshStatus();
		return statusBar;
	}

	private void refreshStatus() {
		showStatus(null);
	}

	private void showStatus(String msg) {
		final String defaultText = "Tracing " + plugin.getImagePlus()
			.getShortTitle() + ", C=" + plugin.channel + ", T=" + plugin.frame;
		if (msg == null || msg.isEmpty()) {
			statusBarText.setText(defaultText);
			return;
		}
		final Timer timer = new Timer(3000, new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				statusBarText.setText(defaultText);
			}
		});
		timer.setRepeats(false);
		timer.start();
		statusBarText.setText(msg);
	}

	private void addSeparator(final JComponent component, final String heading,
		final boolean vgap, final GridBagConstraints c)
	{
		final Insets previousInsets = c.insets;
		c.insets = new Insets(vgap ? 8 : 0, 4, 0, 0);
		final JLabel label = leftAlignedLabel(heading, true);
		label.setFont(new Font("SansSerif", Font.PLAIN, 11));
		component.add(label, c);
		c.insets = previousInsets;
	}

	private JLabel leftAlignedLabel(final String text, final boolean enabled) {
		final JLabel label = new JLabel(text);
		label.setHorizontalAlignment(SwingConstants.LEFT);
		if (!enabled) label.setForeground(GuiUtils.getDisabledComponentColor());
		return label;
	}

	protected void displayOnStarting() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				if (plugin.prefs.isSaveWinLocations()) arrangeDialogs();
				arrangeCanvases();
				setVisible(true);
				setPathListVisible(true, false);
				setFillListVisible(false);
				plugin.getWindow(MultiDThreePanes.XY_PLANE).toFront();
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
				}
				else {
					newStatus = "Cursor position: Distance from path is " + fw.df4.format(
						t);
				}
				fw.fillStatus.setText(newStatus);
			}
		});
	}

	private void setSigmaFromUser() {
		final JTextField sigmaField = new JTextField(String.valueOf(plugin
			.getMinimumSeparation()), 5);
		final JTextField multiplierField = new JTextField("4", 5);
		final Object[] contents = {
			"<html><b>Sigma</b><br>Enter the approximate radius of the structures you are<br>" +
				"tracing (the default is the minimum voxel separation):", sigmaField,
			"<html><br><b>Multiplier</b><br>Enter the scaling factor to apply " +
				"(the default is 4):", multiplierField, };
		final int result = JOptionPane.showConfirmDialog(this, contents,
			"Select Scale of Structures", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION) {
			final double sigma = GuiUtils.extractDouble(sigmaField);
			final double multiplier = GuiUtils.extractDouble(multiplierField);
			if (Double.isNaN(sigma) || sigma <= 0 || Double.isNaN(multiplier) ||
				multiplier <= 0)
			{
				guiUtils.error(
					"The value of sigma and multiplier must be a valid positive number.",
					"Invalid Input");
				return;
			}
			setSigma(sigma, true);
			setMultiplier(multiplier);
		}
	}

	private void arrangeDialogs() {
		Point loc = prefs.getPathWindowLocation();
		if (loc != null) pw.setLocation(loc);
		loc = prefs.getFillWindowLocation();
		if (loc != null) fw.setLocation(loc);
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
		final StackWindow xy_window = plugin.getWindow(MultiDThreePanes.XY_PLANE);
		if (xy_window == null) return;
		final GraphicsConfiguration xy_config = xy_window
			.getGraphicsConfiguration();
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
		if (x > bounds.x + screenWidth / 2 - xy_window.getWidth() / 2) x =
			bounds.x + screenWidth / 2 - xy_window.getWidth() / 2;
		int y = bounds.y + pw.getHeight() / 2;
		if (y > bounds.y + screenHeight / 2 - xy_window.getHeight() / 2) y =
			bounds.y + screenHeight / 2 - xy_window.getHeight() / 2;
		xy_window.setLocation(x, y);

		final StackWindow zy_window = plugin.getWindow(MultiDThreePanes.ZY_PLANE);
		if (zy_window != null) {
			zy_window.setLocation(x + xy_window.getWidth(), y);
			zy_window.toFront();
		}
		final StackWindow xz_window = plugin.getWindow(MultiDThreePanes.XZ_PLANE);
		if (xz_window != null) {
			xz_window.setLocation(x, y + xy_window.getHeight());
			xz_window.toFront();
		}
		xy_window.toFront();
	}

	private void toggleWindowVisibility(final int pane, final JCheckBoxMenuItem mItem)
	{
		if (getImagePlusFromPane(pane) == null) {
			String msg;
			if (pane == MultiDThreePanes.XY_PLANE) msg =
				"Tracing image is no longer available.";
			else if (plugin.getSinglePane()) msg =
				"You are tracing in single-pane mode. To generate ZY/XZ " +
					"panes run \"Display ZY/XZ views\".";
			else msg = "Pane was closed and is no longer accessible. " +
				"You can (re)build it using \"Rebuild ZY/XZ views\".";
			guiUtils.error(msg);
			mItem.setSelected(false);
			return;
		}
		// NB: WindowManager list won't be notified
		plugin.getWindow(pane).setVisible(!mItem.isSelected());
	}

	private ImagePlus getImagePlusFromPane(final int pane) {
		final StackWindow win = plugin.getWindow(pane);
		return (win == null) ? null : win.getImagePlus();
	}

	private boolean noPathsError() {
		final boolean noPaths = pathAndFillManager.size() == 0;
		if (noPaths) guiUtils.error("There are no traced paths.");
		return noPaths;
	}

	protected void setPathListVisible(final boolean makeVisible,
		final boolean toFront)
	{
		assert SwingUtilities.isEventDispatchThread();
		if (makeVisible) {
			showOrHidePathList.setText(" Hide Path List");
			pw.setVisible(true);
			if (toFront) pw.toFront();
		}
		else {
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
		}
		else {
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

	private JMenu helpMenu() {
		final JMenu helpMenu = new JMenu("Help");
		final JMenuItem aboutMenuItem = new JMenuItem("About...");
		aboutMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				guiUtils.msg("You are running Simple Neurite Tracer version " +
					SNT.VERSION, "SNT v" + SNT.VERSION);
			}
		});
		helpMenu.add(aboutMenuItem);
		helpMenu.addSeparator();
		final String URL = "http://imagej.net/Simple_Neurite_Tracer";
		JMenuItem mi = menuItemTrigerringURL("Main documentation page", URL);
		helpMenu.add(mi);
		helpMenu.addSeparator();
		mi = menuItemTrigerringURL("Tutorials", URL + "#Tutorials");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("Basic instructions", URL +
			":_Basic_Instructions");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("Step-by-step instructions", URL +
			":_Step-By-Step_Instructions");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("Filling out processes", URL +
			":_Basic_Instructions#Filling_Out_Neurons");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("3D interaction", URL + ":_3D_Interaction");
		helpMenu.add(mi);
		mi = menuItemTrigerringURL("Tubular Geodesics", URL +
			":_Tubular_Geodesics");
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
		mi = menuItemTrigerringURL("Citing SNT...", URL +
			"#Citing_Simple_Neurite_Tracer");
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
						if (noPathsError()) return;
						String modKey = IJ.isMacOSX() ? "Alt" : "Ctrl";
						modKey += "+Shift";
						final String url1 = Sholl_Analysis.URL +
							"#Analysis_of_Traced_Cells";
						final String url2 =
							"http://imagej.net/Simple_Neurite_Tracer:_Sholl_analysis";
						final StringBuilder sb = new StringBuilder();
						sb.append("<html>");
						sb.append("<div WIDTH=390>");
						sb.append("To initiate <a href='").append(Sholl_Analysis.URL)
							.append("'>Sholl Analysis</a>, ");
						sb.append("you must first select a focal point:");
						sb.append("<ol>");
						sb.append(
							"<li>Mouse over the path of interest. Press \"G\" to activate it</li>");
						sb.append("<li>Press \"").append(modKey).append(
							"\" to select a point along the path</li>");
						sb.append("<li>Press \"").append(modKey).append(
							"+A\" to start analysis</li>");
						sb.append("</ol>");
						sb.append("A detailed walkthrough of this procedure is <a href='")
							.append(url2).append("'>available online</a>. ");
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

	private JMenuItem menuItemTrigerringURL(final String label,
		final String URL)
	{
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

	protected void reset() {
		showStatus("Resetting");
		changeState(WAITING_TO_START_PATH);
	}

	protected void abortCurrentOperation() {//FIXME: MOVE TO Simple NeuriteTracer
		switch (currentState) {
			case (SEARCHING):
				updateStatusText("Cancelling path search...", true);
				plugin.cancelSearch(false);
				break;
			case (CALCULATING_GAUSSIAN):
				updateStatusText("Cancelling Gaussian generation...", true);
				plugin.cancelGaussian();
				break;
			case (WAITING_FOR_SIGMA_POINT):
				showStatus("Sigma adjustment cancelled...");
				changeState(preSigmaPaletteState);
				break;
			case (PARTIAL_PATH):
				showStatus("Last temporary path cancelled...");
				plugin.cancelPath();
				break;
			case (QUERY_KEEP):
				showStatus("Last segment cancelled...");
				plugin.cancelTemporary();
				break;
			case (FILLING_PATHS):
				showStatus("Filling out cancelled...");
				plugin.discardFill();
				break;
			case (WAITING_TO_START_PATH):
				showStatus("Instruction ignored: Nothing to abort");
				break; // nothing to abort;
			default:
				SNT.error("BUG: Wrong state for aborting operation...");
				break;
		}
	}

	private String getState(int state) {
		switch (state) {
			case WAITING_TO_START_PATH:
				return "WAITING_TO_START_PATH";
			case PARTIAL_PATH:
				return "PARTIAL_PATH";
			case SEARCHING:
				return "SEARCHING";
			case QUERY_KEEP:
				return "QUERY_KEEP";
			case LOGGING_POINTS:
				return "LOGGING_POINTS";
			case DISPLAY_EVS:
				return "DISPLAY_EVS";
			case FILLING_PATHS:
				return "FILLING_PATHS";
			case CALCULATING_GAUSSIAN:
				return "CALCULATING_GAUSSIAN";
			case WAITING_FOR_SIGMA_POINT:
				return "WAITING_FOR_SIGMA_POINT";
			case WAITING_FOR_SIGMA_CHOICE:
				return "WAITING_FOR_SIGMA_CHOICE";
			case SAVING:
				return "SAVING";
			case LOADING:
				return "LOADING";
			case FITTING_PATHS:
				return "FITTING_PATHS";
			case IMAGE_CLOSED:
				return "IMAGE_CLOSED";
			default:
				return "UNKNOWN";
		}
	}

	private class GuiListener implements ActionListener, ItemListener,
		SigmaPalette.SigmaPaletteListener, ImageListener
	{

		/* ImageListener */
		@Override
		public void imageClosed(final ImagePlus imp) {
			// updateColorImageChoice(); FIXME
			if (plugin.getImagePlus() == imp) changeState(
				NeuriteTracerResultsDialog.IMAGE_CLOSED);
		}

		@Override
		public void imageOpened(final ImagePlus imp) {}

		@Override
		public void imageUpdated(final ImagePlus imp) {}

		/* SigmaPaletteListener */
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

		@Override
		public void itemStateChanged(final ItemEvent e) {
			assert SwingUtilities.isEventDispatchThread();

			final Object source = e.getSource();

			if (source == viewPathChoice) {

				plugin.justDisplayNearSlices(nearbySlices(), (int) nearbyFieldSpinner
					.getValue());
				nearbyFieldSpinner.setEnabled(nearbySlices());

			}
			else if (source == useSnapWindow) {

				plugin.enableSnapCursor(useSnapWindow.isSelected());

			}
			else if (source == preprocess && !ignorePreprocessEvents) {

				if (preprocess.isSelected()) turnOnHessian();
				else {
					plugin.enableHessian(false);
					changeState(preGaussianState);
				}

			}
			else if (source == justShowSelected) {

				plugin.setShowOnlySelectedPaths(justShowSelected.isSelected());

			}
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			assert SwingUtilities.isEventDispatchThread();

			final Object source = e.getSource();

			if (source == saveMenuItem && !noPathsError()) {

				final FileInfo info = plugin.file_info;
				SaveDialog sd;

				if (info == null) {

					sd = new SaveDialog("Save traces as...", "image", ".traces");

				}
				else {

					final String fileName = info.fileName;
					final String directory = info.directory;

					String suggestedSaveFilename;

					suggestedSaveFilename = fileName;

					sd = new SaveDialog("Save traces as...", directory,
						suggestedSaveFilename, ".traces");
				}

				String savePath;
				if (sd.getFileName() == null) {
					return;
				}
				savePath = sd.getDirectory() + sd.getFileName();

				final File file = new File(savePath);
				if (file.exists()) {
					if (!IJ.showMessageWithCancel("Save traces file...", "The file " +
						savePath + " already exists.\n" + "Do you want to replace it?"))
						return;
				}

				IJ.showStatus("Saving traces to " + savePath);

				final int preSavingState = currentState;
				changeState(SAVING);
				try {
					pathAndFillManager.writeXML(savePath, plugin.useCompressedXML);
				}
				catch (final IOException ioe) {
					IJ.showStatus("Saving failed.");
					SNT.error("Writing traces to '" + savePath + "' failed: " + ioe);
					changeState(preSavingState);
					return;
				}
				changeState(preSavingState);
				IJ.showStatus("Saving completed.");

				plugin.unsavedPaths = false;

			}
			else if (source == loadMenuItem) {

				if (plugin.pathsUnsaved()) {
					final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(),
						"Warning",
						"There are unsaved paths. Do you really want to load new traces?");

					if (!d.yesPressed()) return;
				}

				final int preLoadingState = currentState;
				changeState(LOADING);
				plugin.loadTracings();
				changeState(preLoadingState);

			}
			else if (source == exportAllSWCMenuItem && !noPathsError()) {

				if (pathAndFillManager.usingNonPhysicalUnits() && !guiUtils
					.getConfirmation(
						"These tracings were obtained from a spatially uncalibrated " +
							"image but the SWC specification assumes all coordinates to be " +
							"in " + GuiUtils.micrometre() +
							". Do you really want to proceed " + "with the SWC export?",
						"Warning")) return;

				final FileInfo info = plugin.file_info;
				SaveDialog sd;

				if (info == null) {

					sd = new SaveDialog("Export all as SWC...", "exported", "");

				}
				else {

					String suggestedFilename;
					final int extensionIndex = info.fileName.lastIndexOf(".");
					if (extensionIndex == -1) suggestedFilename = info.fileName;
					else suggestedFilename = info.fileName.substring(0, extensionIndex);

					sd = new SaveDialog("Export all as SWC...", info.directory,
						suggestedFilename + "-exported", "");
				}

				String savePath;
				if (sd.getFileName() == null) {
					return;
				}
				savePath = sd.getDirectory() + sd.getFileName();
				if (verbose) SNT.log("Got savePath: " + savePath);
				if (!pathAndFillManager.checkOKToWriteAllAsSWC(savePath)) return;
				pathAndFillManager.exportAllAsSWC(savePath);

			}
			else if ((source == exportCSVMenuItem ||
				source == exportCSVMenuItemAgain) && !noPathsError())
			{

				final FileInfo info = plugin.file_info;
				SaveDialog sd;

				if (info == null) {

					sd = new SaveDialog("Export Paths as CSV...", "traces", ".csv");

				}
				else {

					sd = new SaveDialog("Export Paths as CSV...", info.directory,
						info.fileName, ".csv");
				}

				String savePath;
				if (sd.getFileName() == null) {
					return;
				}
				savePath = sd.getDirectory() + sd.getFileName();

				final File file = new File(savePath);
				if (file.exists()) {
					if (!IJ.showMessageWithCancel("Export as CSV...", "The file " +
						savePath + " already exists.\n" + "Do you want to replace it?"))
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
				}
				catch (final IOException ioe) {
					IJ.showStatus("Exporting failed.");
					SNT.error("Writing traces to '" + savePath + "' failed:\n" + ioe);
					changeState(preExportingState);
					return;
				}
				IJ.showStatus("Export complete.");
				changeState(preExportingState);

			}
			else if (source == loadLabelsMenuItem) {

				plugin.loadLabels();

			}
			else if (source == makeLineStackMenuItem && !noPathsError()) {

				final SkeletonPlugin skelPlugin = new SkeletonPlugin(plugin);
				skelPlugin.run();

			}
			else if (source == pathsToROIsMenuItem && !noPathsError()) {

				if (!pathAndFillManager.anySelected()) {
					SNT.error("No paths selected.");
					return;
				}

				final GenericDialog gd = new GenericDialog("Selected Paths to ROIs");

				final int[] PLANES_ID = { MultiDThreePanes.XY_PLANE, MultiDThreePanes.XZ_PLANE,
					MultiDThreePanes.ZY_PLANE };
				final String[] PLANES_STRING = { "XY_View", "XZ_View", "ZY_View" };
				final InteractiveTracerCanvas[] canvases = { plugin.xy_tracer_canvas,
					plugin.xz_tracer_canvas, plugin.zy_tracer_canvas };
				final boolean[] destinationPlanes = new boolean[PLANES_ID.length];
				for (int i = 0; i < PLANES_ID.length; i++)
					destinationPlanes[i] = canvases[i] != null && getImagePlusFromPane(
						PLANES_ID[i]) != null;

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
				if (gd.wasCanceled()) return;

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
							if (plugin.singleSlice) continue;
							for (int j = lastPlaneIdx + 1; j < overlay.size(); j++) {
								final Roi roi = overlay.get(j);
								roi.setName(roi.getName() + " [" + PLANES_STRING[i] + "]");
							}
						}
					}
					RoiManager rm = RoiManager.getInstance2();
					if (rm == null) rm = new RoiManager();
					else if (reset) rm.reset();
					Prefs.showAllSliceOnly = !plugin.singleSlice;
					rm.setEditMode(plugin.getImagePlus(), false);
					for (final Roi path : overlay.toArray())
						rm.addRoi(path);
					rm.runCommand("sort");
					rm.setEditMode(plugin.getImagePlus(), true);
					rm.runCommand("show all without labels");

				}
				else { // Overlay

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
							}
							else if (reset) overlay.clear();
							plugin.addPathsToOverlay(overlay, PLANES_ID[i], swcColors);
						}
					}
					if (!error.isEmpty()) {
						SNT.error("Some ROIs were skipped because some images (" + error
							.substring(0, error.length() - 2) +
							") are no longer available.\nPlease consider exporting to the ROI Manager instead.");
					}
				}

			}
			else if (source == abortButton) {

				if (currentState == SEARCHING) {
					updateStatusText("Cancelling path search...");
					plugin.cancelSearch(false);
				}
				else if (currentState == CALCULATING_GAUSSIAN) {
					updateStatusText("Cancelling Gaussian generation...");
					plugin.cancelGaussian();
				}
				else if (currentState == WAITING_FOR_SIGMA_POINT) {
					updateStatusText("Cancelling sigma adjustment...");
					changeState(preSigmaPaletteState);
				}
				else if (currentState == PARTIAL_PATH) {
					plugin.cancelPath();
				} 
				else if (currentState == QUERY_KEEP) {
					plugin.cancelTemporary();
				}
				else if (currentState != WAITING_TO_START_PATH){
					SNT.error("BUG: Wrong state for aborting operation...");
				}
			}
			else if (source == keepSegment) {

				plugin.confirmTemporary();

			}
			else if (source == junkSegment) {

				plugin.cancelTemporary();

			}
			else if (source == completePath) {

				plugin.finishedPath();

			}
			else if (source == cancelPath) {

				plugin.cancelPath();

			}
			else if (source == quitMenuItem) {

				exitRequested();

			}
			else if (source == showOrHidePathList) {

				togglePathListVisibility();

			}
			else if (source == showOrHideFillList) {

				toggleFillListVisibility();

			}
			else if (source == editSigma) {

				setSigmaFromUser();

			}
			else if (source == sigmaWizard) {

				preSigmaPaletteState = currentState;
				changeState(WAITING_FOR_SIGMA_POINT);

			}
			else if (source == colorImageChoice) {

				if (!ignoreColorImageChoiceEvents) checkForColorImageChange();

			}
		}

	}

}
