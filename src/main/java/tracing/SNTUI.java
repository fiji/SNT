/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import javax.swing.AbstractAction;
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
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.table.DefaultGenericTable;
import org.scijava.util.ColorRGB;
import org.scijava.util.Types;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.HTMLDialog;
import ij.gui.StackWindow;
import ij.measure.Calibration;
import ij3d.Content;
import ij3d.ContentConstants;
import ij3d.Image3DUniverse;
import ij3d.ImageWindow3D;
import net.imagej.Dataset;
import sholl.ShollUtils;
import tracing.analysis.TreeAnalyzer;
import tracing.event.SNTEvent;
import tracing.gui.ColorChooserButton;
import tracing.gui.GuiUtils;
import tracing.gui.IconFactory;
import tracing.gui.IconFactory.GLYPH;
import tracing.gui.SigmaPalette;
import tracing.gui.cmds.ChooseDatasetCmd;
import tracing.gui.cmds.CompareFilesCmd;
import tracing.gui.cmds.ComputeTubenessImg;
import tracing.gui.cmds.JSONImporterCmd;
import tracing.gui.cmds.MLImporterCmd;
import tracing.gui.cmds.MultiSWCImporterCmd;
import tracing.gui.cmds.OpenDatasetCmd;
import tracing.gui.cmds.RemoteSWCImporterCmd;
import tracing.gui.cmds.ResetPrefsCmd;
import tracing.gui.cmds.ShowCorrespondencesCmd;
import tracing.hyperpanes.MultiDThreePanes;
import tracing.io.FlyCircuitLoader;
import tracing.io.NeuroMorphoLoader;
import tracing.plugin.PlotterCmd;
import tracing.plugin.StrahlerCmd;
import tracing.viewer.Viewer3D;

@SuppressWarnings("serial")
public class SNTUI extends JDialog {

	/* Deprecated stuff to be removed soon */
	@Deprecated
	private final String noColorImageString = "[None]";
	@Deprecated
	private ImagePlus currentColorImage;
	@Deprecated
	private JComboBox<String> colorImageChoice;

	/* UI */
	private JCheckBox showPathsSelected;
	protected JCheckBox showPartsNearby;
	protected JCheckBox useSnapWindow;
	private JCheckBox onlyActiveCTposition;
	protected JSpinner snapWindowXYsizeSpinner;
	protected JSpinner snapWindowZsizeSpinner;
	protected JSpinner nearbyFieldSpinner;
	private JButton showOrHidePathList;
	private JButton showOrHideFillList = new JButton(); // must be initialized
	private JMenuItem loadTracesMenuItem;
	private JMenuItem loadSWCMenuItem;
	private JMenuItem loadLabelsMenuItem;
	private JMenuItem saveMenuItem;
	private JMenuItem exportCSVMenuItem;
	private JMenuItem exportAllSWCMenuItem;
	private JMenuItem quitMenuItem;
	private JMenuItem measureMenuItem;
	private JMenuItem strahlerMenuItem;
	private JMenuItem plotMenuItem;
	private JMenuItem sendToTrakEM2;
	private JLabel statusText;
	private JLabel statusBarText;
	private JButton keepSegment;
	private JButton junkSegment;
	private JButton completePath;
	private JButton rebuildCanvasButton;
	private JCheckBox debugCheckBox;

	// UI controls for auto-tracing
	private JComboBox<String> searchAlgoChoice;
	private JPanel hessianPanel;
	private JMenuItem computeTubenessJMI;
	private JMenuItem loadTubenessJMI;
	private JMenuItem flushTubenessJMI;
	private JCheckBox preprocess;
	private JCheckBox aStarCheckBox;

	// UI controls for CT data source
	private JPanel sourcePanel;

	// UI controls for loading 'filtered image'
	private JPanel filteredImgPanel;
	private JTextField filteredImgPathField;
	private JButton filteredImgOptionsButton;
	private JButton filteredImgBrowseButton;
	private JCheckBox filteredImgOverlayCheckbox;
	private JCheckBox filteredImgActivateCheckbox;
	private ActiveWorker activeWorker;

	private static final int MARGIN = 4;
	private volatile int currentState;
	private volatile boolean ignoreColorImageChoiceEvents = false;
	private volatile boolean ignorePreprocessEvents = false;
	private volatile int preGaussianState;

	private final SimpleNeuriteTracer plugin;
	private final PathAndFillManager pathAndFillManager;
	protected final GuiUtils guiUtils;
	private final PathManagerUI pmUI;
	private final FillManagerUI fmUI;

	/* Reconstruction Viewer */
	protected Viewer3D recViewer;
	protected Frame recViewerFrame;
	private JButton openRecViewer;

	protected final GuiListener listener;

	/* These are the states that the UI can be in: */
	/**
	 * Flag specifying that image data is available and the UI is not waiting on any
	 * pending operations, thus 'ready to trace'
	 */
	public static final int WAITING_TO_START_PATH = 0;
	static final int PARTIAL_PATH = 1;
	static final int SEARCHING = 2;
	static final int QUERY_KEEP = 3;
	// static final int LOGGING_POINTS = 4;
	// static final int DISPLAY_EVS = 5;
	static final int CRUNCHING_DATA = 5;
	static final int FILLING_PATHS = 6;
	static final int CALCULATING_GAUSSIAN = 7;
	static final int WAITING_FOR_SIGMA_POINT = 8;
	static final int WAITING_FOR_SIGMA_CHOICE = 9;
	static final int SAVING = 10;
	static final int LOADING = 11;
	/** Flag specifying UI is currently waiting for fitting operations to conclued */
	public static final int FITTING_PATHS = 12;
	static final int LOADING_FILTERED_IMAGE = 13;
	/** Flag specifying UI is currently waiting for user to edit a selected Path */
	public static final int EDITING = 14;
	/**
	 * Flag specifying all SNT are temporarily disabled (all user interactions are
	 * waived back to ImageJ)
	 */
	public static final int SNT_PAUSED = 15;
	/**
	 * Flag specifying tracing functions are (currently) disabled. Tracing is
	 * disabled when the user chooses so or when no valid image data is available
	 * (e.g., when no image has been loaded and a placeholder display canvas is
	 * being used)
	 */
	public static final int TRACING_PAUSED = 16;


	// TODO: Internal preferences: should be migrated to SNTPrefs
	protected boolean confirmTemporarySegments = true;
	protected boolean finishOnDoubleConfimation = true;
	protected boolean discardOnDoubleCancellation = true;
	protected boolean askUserConfirmation = true;

	/**
	 * Instantiates SNT's main UI and associated {@link PathManagerUI} and
	 * {@link FillManagerUI} instances.
	 *
	 * @param plugin the {@link SimpleNeuriteTracer} instance associated with this
	 *               UI
	 */
	public SNTUI(final SimpleNeuriteTracer plugin) {
		this(plugin, null, null);
	}

	private SNTUI(final SimpleNeuriteTracer plugin, final PathManagerUI pmUI, final FillManagerUI fmUI) {

		super(plugin.legacyService.getIJ1Helper().getIJ(), "SNT v" + SNT.VERSION, false);
		guiUtils = new GuiUtils(this);
		this.plugin = plugin;
		new ClarifyingKeyListener(plugin).addKeyAndContainerListenerRecursively(this);
		listener = new GuiListener();
		pathAndFillManager = plugin.getPathAndFillManager();

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(final WindowEvent e) {
				exitRequested();
			}
		});

		assert SwingUtilities.isEventDispatchThread();
		final JTabbedPane tabbedPane = new JTabbedPane();

		{ // Main tab
			final GridBagConstraints c1 = GuiUtils.defaultGbc();
			{
				final JPanel tab1 = getTab();
				// c.insets.left = MARGIN * 2;
				c1.anchor = GridBagConstraints.NORTHEAST;
				addSeparatorWithURL(tab1, "Cursor Auto-snapping:", false, c1);
				++c1.gridy;
				tab1.add(snappingPanel(), c1);
				++c1.gridy;
				tab1.add(aStarPanel(), c1);
				++c1.gridy;
				tab1.add(hessianPanel(), c1);
				++c1.gridy;
				tab1.add(filteredImagePanel(), c1);
				++c1.gridy;
				addSeparatorWithURL(tab1, "Filters for Visibility of Paths:", true, c1);
				++c1.gridy;
				tab1.add(renderingPanel(), c1);
				++c1.gridy;
				addSeparatorWithURL(tab1, "Default Path Colors:", true, c1);
				++c1.gridy;
				tab1.add(colorOptionsPanel(), c1);
				++c1.gridy;
				GuiUtils.addSeparator(tab1, "", true, c1); // empty separator
				++c1.gridy;
				c1.fill = GridBagConstraints.HORIZONTAL;
				c1.insets = new Insets(0, 0, 0, 0);
				tab1.add(hideWindowsPanel(), c1);
				tabbedPane.addTab(" Main ", tab1);
			}
		}

		{ // Options Tab
			final JPanel tab2 = getTab();
			tab2.setLayout(new GridBagLayout());
			final GridBagConstraints c2 = GuiUtils.defaultGbc();
			// c2.insets.left = MARGIN * 2;
			c2.anchor = GridBagConstraints.NORTHEAST;
			c2.gridwidth = GridBagConstraints.REMAINDER;
			{
				addSeparatorWithURL(tab2, "Data Source:", false, c2);
				++c2.gridy;
				tab2.add(sourcePanel = sourcePanel(plugin.getImagePlus()), c2);
				++c2.gridy;
			}

			addSeparatorWithURL(tab2, "Views:", true, c2);
			++c2.gridy;
			tab2.add(viewsPanel(), c2);
			++c2.gridy;
			{
				addSeparatorWithURL(tab2, "Temporary Paths:", true, c2);
				++c2.gridy;
				tab2.add(tracingPanel(), c2);
				++c2.gridy;
			}
			addSeparatorWithURL(tab2, "UI Interaction:", true, c2);
			++c2.gridy;
			tab2.add(interactionPanel(), c2);
			++c2.gridy;
			addSeparatorWithURL(tab2, "Misc:", true, c2);
			++c2.gridy;
			c2.weighty = 1;
			tab2.add(miscPanel(), c2);
			tabbedPane.addTab(" Options ", tab2);
		}

		{ // 3D tab
			final JPanel tab3 = getTab();
			tab3.setLayout(new GridBagLayout());
			final GridBagConstraints c3 = GuiUtils.defaultGbc();
			// c3.insets.left = MARGIN * 2;
			c3.anchor = GridBagConstraints.NORTHEAST;
			c3.gridwidth = GridBagConstraints.REMAINDER;

			tabbedPane.addTab(" 3D ", tab3);
			GuiUtils.addSeparator(tab3, "Reconstruction Viewer:", true, c3);
			c3.gridy++;
			final String msg = "The Reconstruction Viewer is an advanced OpenGL " +
				"visualization tool. For performance reasons, some Path " +
				"Manager changes may need to be synchronized manually from " +
				"RV Controls";
			tab3.add(largeMsg(msg), c3);
			c3.gridy++;
			tab3.add(reconstructionViewerPanel(), c3);
			c3.gridy++;
			addSpacer(tab3, c3);
			GuiUtils.addSeparator(tab3, "Legacy 3D Viewer:", true, c3);
			++c3.gridy;
			final String msg2 =
				"The Legacy 3D Viewer is a functional tracing canvas " +
					"but it depends on outdated services that are now deprecated. " +
					"It may not function reliably on recent operating systems.";
			tab3.add(largeMsg(msg2), c3);
			c3.gridy++;
			tab3.add(legacy3DViewerPanel(), c3);
			addSpacer(tab3, c3);
			GuiUtils.addSeparator(tab3, "SciView", true, c3);
			++c3.gridy;
			final String msg3 =
				"SciView is IJ2's modern replacement for the Legacy 3D " +
					"Viewer providing 3D visualization and virtual reality capabilities " +
					"for both images and meshes. It is not yet available in SNT.";
			tab3.add(largeMsg(msg3), c3);

			{
				tabbedPane.setIconAt(0, IconFactory.getTabbedPaneIcon(GLYPH.HOME));
				tabbedPane.setIconAt(1, IconFactory.getTabbedPaneIcon(GLYPH.TOOL));
				tabbedPane.setIconAt(2, IconFactory.getTabbedPaneIcon(GLYPH.CUBE));
			}
		}

		tabbedPane.addChangeListener(e -> {
			if (tabbedPane.getSelectedIndex() == 1 && getCurrentState() > WAITING_TO_START_PATH
					&& getCurrentState() < EDITING) {
				tabbedPane.setSelectedIndex(0);
				guiUtils.blinkingError(statusText,
						"Please complete current task before selecting the \"Options\" tab.");
			}
		});
		setJMenuBar(createMenuBar());
		setLayout(new GridBagLayout());
		final GridBagConstraints dialogGbc = GuiUtils.defaultGbc();
		add(statusPanel(), dialogGbc);
		dialogGbc.gridy++;
		add(tabbedPane, dialogGbc);
		dialogGbc.gridy++;
		add(statusBar(), dialogGbc);
		pack();
		toFront();

		if (pmUI == null) {
			this.pmUI = new PathManagerUI(plugin);
			this.pmUI.setLocation(getX() + getWidth(), getY());
			if (showOrHidePathList != null) {
				this.pmUI.addWindowStateListener(evt -> {
					if ((evt.getNewState() & Frame.ICONIFIED) == Frame.ICONIFIED) {
						showOrHidePathList.setText("Show Path Manager");
					}
				});
				this.pmUI.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosing(final WindowEvent e) {
						showOrHidePathList.setText("Show Path Manager");
					}
				});
			}
		} else {
			this.pmUI = pmUI;
		}
		if (fmUI == null) {
			this.fmUI = new FillManagerUI(plugin);
			this.fmUI.setLocation(getX() + getWidth(), getY() + this.pmUI.getHeight());
			if (showOrHidePathList != null) {
				this.fmUI.addWindowStateListener(evt -> {
					if (showOrHideFillList != null && (evt.getNewState() & Frame.ICONIFIED) == Frame.ICONIFIED) {
						showOrHideFillList.setText("Show Fill Manager");
					}
				});
				this.fmUI.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosing(final WindowEvent e) {
						showOrHideFillList.setText("Show Fill Manager");
					}
				});
			}
		} else {
			this.fmUI = fmUI;
		}
	}

	/**
	 * Gets the current UI state.
	 *
	 * @return the current UI state, e.g., {@link SNTUI#WAITING_FOR_SIGMA_POINT},
	 *         {@link SNTUI#WAITING_FOR_SIGMA_POINT}, etc.
	 */
	public int getCurrentState() {
		if (plugin.tracingHalted && currentState == WAITING_TO_START_PATH)
			currentState = TRACING_PAUSED;
		return currentState;
	}

	/**
	 * Enables/disables debug mode
	 *
	 * @param enable true to enable debug mode, otherwise false
	 */
	public void setEnableDebugMode(final boolean enable) {
		debugCheckBox.setSelected(enable);
		if (getReconstructionViewer(false) == null) {
			SNT.setDebugMode(enable);
		} else {
			// will call SNT.setDebugMode(enable);
			getReconstructionViewer(false).setEnableDebugMode(enable);
		}
	}

	private void addSeparatorWithURL(final JComponent component, final String label, final boolean vgap,
			final GridBagConstraints c) {
		final String anchor = label.replace(" ", "_").replace(":", "");
		final String uri = "https://imagej.net/SNT:_Overview#" + anchor;
		JLabel jLabel = GuiUtils.leftAlignedLabel(label, uri, true);
		GuiUtils.addSeparator(component, jLabel, vgap, c);
	}

	private void updateStatusText(final String newStatus, final boolean includeStatusBar) {
		updateStatusText(newStatus);
		if (includeStatusBar)
			showStatus(newStatus, true);
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
			for (int i1 : wList) {
				final ImagePlus imp = WindowManager.getImage(i1);
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
				final Calibration colorImageCalibration = intendedColorImage.getCalibration();
				if (!SNT.similarCalibrations(calibration, colorImageCalibration)) {
					guiUtils.centeredMsg("The calibration of '" + intendedColorImage.getTitle()
							+ "' is different from the image you're tracing ('" + image.getTitle()
							+ "')'\nThis may produce unexpected results.", "Warning");
				}
				if (!(intendedColorImage.getWidth() == image.getWidth()
						&& intendedColorImage.getHeight() == image.getHeight()
						&& intendedColorImage.getStackSize() == image.getStackSize()))
					guiUtils.centeredMsg("the dimensions (in voxels) of '" + intendedColorImage.getTitle()
							+ "' is different from the image you're tracing ('" + image.getTitle()
							+ "')'\nThis may produce unexpected results.", "Warning");

			}
			currentColorImage = intendedColorImage;
			plugin.setColorImage(currentColorImage);
		}
	}

	protected void gaussianCalculated(final boolean succeeded) {
		SwingUtilities.invokeLater(() -> {
			if (!succeeded) {
				ignorePreprocessEvents = true;
				preprocess.setSelected(false);
				ignorePreprocessEvents = false;
			}
			changeState(preGaussianState);
		});
	}

	/**
	 * Sets the multiplier value for Hessian-based analysis. In the UI the
	 * multiplier is reported as 1/256 * max of tubeness image.
	 *
	 * @param multiplier the new multiplier value
	 */
	public void setMultiplier(final double multiplier) {
		plugin.hessianMultiplier = multiplier;
		SwingUtilities.invokeLater(() -> {
			updateHessianPanel();
		});
	}

	/**
	 * Sets the sigma value for computation of curvatures, updating the Hessian
	 * panel accordingly
	 *
	 * @param sigma            the new sigma value
	 * @param mayStartGaussian if true and the current UI state allows it, the
	 *                         Gaussian computation will be performed using the the
	 *                         new parameter
	 */
	public void setSigma(final double sigma, final boolean mayStartGaussian) {
		plugin.hessianSigma = sigma; // ensure it is applied immediately
		SwingUtilities.invokeLater(() -> {
			updateHessianPanel();
			if (!mayStartGaussian)
				return;
			preprocess.setSelected(false);

			// Turn on the checkbox: according to the documentation this doesn't
			// generate an event, so we manually turn on the Gaussian calculation
			ignorePreprocessEvents = true;
			preprocess.setSelected(true);
			ignorePreprocessEvents = false;
			enableHessian(true);
		});
	}

	protected void updateHessianPanel() {
		final StringBuilder sb = new StringBuilder("Hessian-based analysis ");
		if (!plugin.isTubenessImageCached())
			sb.append("(\u03C3=").append(SNT.formatDouble(getSigma(), 2));
		else if (plugin.hessianSigma == -1)
			sb.append("(\u03C3=?.??");
		else
			sb.append("(\u03C3=").append(SNT.formatDouble(plugin.hessianSigma, 2));
		sb.append("; max=").append(SNT.formatDouble(256 / plugin.hessianMultiplier, 1));
		sb.append(") ");
		preprocess.setToolTipText((plugin.isTubenessImageCached()) ? "Computations currently cached in memory"
				: "Computations are not cached. Will be performed as needed");
		assert SwingUtilities.isEventDispatchThread();
		preprocess.setText(hotKeyLabel(sb.toString(), "H"));
		final boolean dataCached = plugin.isTubenessImageCached();
		computeTubenessJMI.setEnabled(!dataCached);
		loadTubenessJMI.setEnabled(!dataCached);
		flushTubenessJMI.setEnabled(dataCached);
	}

	/**
	 * Gets the current Sigma value as reported in the "Auto-tracing" widget.
	 *
	 * @return the sigma value currently in use
	 */
	public double getSigma() {
		if (plugin.hessianSigma == -1)
			plugin.hessianSigma = plugin.getDefaultHessianSigma();
		return plugin.hessianSigma;
	}

	protected void exitRequested() {
		assert SwingUtilities.isEventDispatchThread();
		String msg = "Exit SNT?";
		if (plugin.pathsUnsaved())
			msg = "There are unsaved paths. Do you really want to quit?";
		if (pmUI.measurementsUnsaved())
			msg = "There are unsaved measurements. Do you really want to quit?";
		if (!guiUtils.getConfirmation(msg, "Really Quit?"))
			return;
		abortCurrentOperation();
		plugin.cancelSearch(true);
		plugin.notifyListeners(new SNTEvent(SNTEvent.QUIT));
		plugin.prefs.savePluginPrefs(true);
		pmUI.dispose();
		pmUI.closeTable();
		fmUI.dispose();
		if (recViewer != null)
			recViewer.dispose();
		dispose();
		// NB: If visible Reconstruction Plotter will remain open
		plugin.closeAndResetAllPanes();
		SNT.setPlugin(null);
	}

	private void setEnableAutoTracingComponents(final boolean enable, final boolean enableAstar) {
		if (hessianPanel != null) {
			GuiUtils.enableComponents(hessianPanel, enable);
			GuiUtils.enableComponents(preprocess.getParent(), enable);
			GuiUtils.enableComponents(aStarCheckBox.getParent(), enableAstar);
		}
		if (filteredImgPanel != null)
			GuiUtils.enableComponents(filteredImgPanel, enable);
		if (enable)
			updateFilteredFileField();
	}

	protected void disableImageDependentComponents() {
		assert SwingUtilities.isEventDispatchThread();
		loadLabelsMenuItem.setEnabled(false);
		fmUI.setEnabledNone();
		setEnableAutoTracingComponents(false, false);
	}

	private void disableEverything() {
		assert SwingUtilities.isEventDispatchThread();
		disableImageDependentComponents();
		loadTracesMenuItem.setEnabled(false);
		loadSWCMenuItem.setEnabled(false);
		exportCSVMenuItem.setEnabled(false);
		exportAllSWCMenuItem.setEnabled(false);
		measureMenuItem.setEnabled(false);
		sendToTrakEM2.setEnabled(false);
		saveMenuItem.setEnabled(false);
		quitMenuItem.setEnabled(false);
	}

	private void updateRebuildCanvasButton() {
		final ImagePlus imp = plugin.getImagePlus();
		final String label = (imp == null || !imp.isVisible() || imp.getProcessor() == null) ? "Create Canvas"
				: "Resize Canvas";
		rebuildCanvasButton.setText(label);
	}

	/**
	 * Changes this UI to a new state.
	 *
	 * @param newState the new state, e.g., {@link SNTUI#WAITING_TO_START_PATH},
	 *                 {@link SNTUI#TRACING_PAUSED}, etc.
	 */
	public void changeState(final int newState) {

		SwingUtilities.invokeLater(() -> {
			switch (newState) {

			case WAITING_TO_START_PATH:
//					if (plugin.analysisMode || !plugin.accessToValidImageData()) {
//						changeState(ANALYSIS_MODE);
//						return;
//					}
				keepSegment.setEnabled(false);
				junkSegment.setEnabled(false);
				completePath.setEnabled(false);

				pmUI.valueChanged(null); // Fake a selection change in the path tree:
				showPartsNearby.setEnabled(isStackAvailable());
				setEnableAutoTracingComponents(plugin.isAstarEnabled(), true);
				fmUI.setEnabledWhileNotFilling();
				loadLabelsMenuItem.setEnabled(true);
				saveMenuItem.setEnabled(true);
				loadTracesMenuItem.setEnabled(true);
				loadSWCMenuItem.setEnabled(true);

				exportCSVMenuItem.setEnabled(true);
				exportAllSWCMenuItem.setEnabled(true);
				measureMenuItem.setEnabled(true);
				sendToTrakEM2.setEnabled(plugin.anyListeners());
				quitMenuItem.setEnabled(true);
				showPathsSelected.setEnabled(true);
				updateStatusText("Click somewhere to start a new path...");
				showOrHideFillList.setEnabled(true);
				updateRebuildCanvasButton();
				break;

			case TRACING_PAUSED:

				keepSegment.setEnabled(false);
				junkSegment.setEnabled(false);
				completePath.setEnabled(false);
				pmUI.valueChanged(null); // Fake a selection change in the path tree:
				showPartsNearby.setEnabled(isStackAvailable());
				setEnableAutoTracingComponents(false, false);
				plugin.discardFill(false);
				fmUI.setEnabledWhileNotFilling();
				// setFillListVisible(false);
				loadLabelsMenuItem.setEnabled(true);
				saveMenuItem.setEnabled(true);
				loadTracesMenuItem.setEnabled(true);
				loadSWCMenuItem.setEnabled(true);

				exportCSVMenuItem.setEnabled(true);
				exportAllSWCMenuItem.setEnabled(true);
				measureMenuItem.setEnabled(true);
				sendToTrakEM2.setEnabled(plugin.anyListeners());
				quitMenuItem.setEnabled(true);
				showPathsSelected.setEnabled(true);
				updateRebuildCanvasButton();
				updateStatusText("Tracing functions disabled...");
				break;

			case PARTIAL_PATH:
				updateStatusText("Select a point further along the structure...");
				disableEverything();
				keepSegment.setEnabled(false);
				junkSegment.setEnabled(false);
				completePath.setEnabled(true);
				showPartsNearby.setEnabled(isStackAvailable());
				setEnableAutoTracingComponents(plugin.isAstarEnabled(), true);
				quitMenuItem.setEnabled(false);
				break;

			case SEARCHING:
				updateStatusText("Searching for path between points...");
				disableEverything();
				break;

			case QUERY_KEEP:
				updateStatusText("Keep this new path segment?");
				disableEverything();
				keepSegment.setEnabled(true);
				junkSegment.setEnabled(true);
				break;

			case FILLING_PATHS:
				updateStatusText("Filling out selected paths...");
				disableEverything();
				fmUI.setEnabledWhileFilling();
				break;

			case FITTING_PATHS:
				updateStatusText("Fitting volumes around selected paths...");
				break;

			case CRUNCHING_DATA:
				updateStatusText("Crunching data. This could take a while...");
				disableEverything();
				break;

			case CALCULATING_GAUSSIAN:
				updateStatusText("Calculating Gaussian...");
				disableEverything();
				break;

			case LOADING_FILTERED_IMAGE:
				updateStatusText("Loading Filtered Image...");
				disableEverything();
				break;

			case WAITING_FOR_SIGMA_POINT:
				updateStatusText("Click on a representative structure...");
				disableEverything();
				break;

			case WAITING_FOR_SIGMA_CHOICE:
				updateStatusText("Close the sigma palette window to continue...");
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

			case EDITING:
				if (noPathsError())
					return;
				plugin.setCanvasLabelAllPanes(InteractiveTracerCanvas.EDIT_MODE_LABEL);
				updateStatusText("Editing Mode. Tracing functions disabled...");
				disableEverything();
				keepSegment.setEnabled(false);
				junkSegment.setEnabled(false);
				completePath.setEnabled(false);
				showPartsNearby.setEnabled(isStackAvailable());
				setEnableAutoTracingComponents(false, false);
				getFillManager().setVisible(false);
				showOrHideFillList.setEnabled(false);
				break;

			case SNT_PAUSED:
				updateStatusText("SNT is paused. Core functions disabled...");
				disableEverything();
				keepSegment.setEnabled(false);
				junkSegment.setEnabled(false);
				completePath.setEnabled(false);
				showPartsNearby.setEnabled(isStackAvailable());
				setEnableAutoTracingComponents(false, false);
				getFillManager().setVisible(false);
				showOrHideFillList.setEnabled(false);
				break;

			default:
				SNT.error("BUG: switching to an unknown state");
				return;
			}
			currentState = newState;
			SNT.log("UI state: " + getState(currentState));
			plugin.updateAllViewers();
		});

	}

	protected void resetState() {
		plugin.pauseTracing(!plugin.accessToValidImageData() || plugin.tracingHalted, false); // will set UI state
	}

	/**
	 * Gets the current UI state.
	 *
	 * @return the current state, e.g., {@link SNTUI#WAITING_TO_START_PATH},
	 *         {@link SNTUI#TRACING_PAUSED}, etc.
	 */
	public int getState() {
		return currentState;
	}

	private boolean isStackAvailable() {
		return plugin != null && !plugin.is2D();
	}

	/* User inputs for multidimensional images */
	private JPanel sourcePanel(final ImagePlus imp) {
		final JPanel sourcePanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		gdb.gridwidth = 1;
		final boolean hasChannels = imp != null && imp.getNChannels() > 1;
		final boolean hasFrames = imp != null && imp.getNFrames() > 1;
		final JPanel positionPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
		positionPanel.add(GuiUtils.leftAlignedLabel("Channel", true));
		final JSpinner channelSpinner = GuiUtils.integerSpinner(plugin.channel, 1,
				(hasChannels) ? imp.getNChannels() : 1, 1);
		positionPanel.add(channelSpinner);
		positionPanel.add(GuiUtils.leftAlignedLabel(" Frame", true));
		final JSpinner frameSpinner = GuiUtils.integerSpinner(plugin.frame, 1, (hasFrames) ? imp.getNFrames() : 1, 1);
		positionPanel.add(frameSpinner);
		final JButton applyPositionButton = new JButton("Reload");
		final ChangeListener spinnerListener = e -> applyPositionButton.setText(
				((int) channelSpinner.getValue() == plugin.channel && (int) frameSpinner.getValue() == plugin.frame)
						? "Reload"
						: "Apply");
		channelSpinner.addChangeListener(spinnerListener);
		frameSpinner.addChangeListener(spinnerListener);
		channelSpinner.setEnabled(hasChannels);
		frameSpinner.setEnabled(hasFrames);
		applyPositionButton.addActionListener(e -> {
			if (!plugin.accessToValidImageData()) {
				guiUtils.error("There is no valid image data to be loaded.");
				return;
			}
			final int newC = (int) channelSpinner.getValue();
			final int newT = (int) frameSpinner.getValue();
			loadImagefromGUI(newC, newT);
		});
		positionPanel.add(applyPositionButton);
		sourcePanel.add(positionPanel, gdb);
		return sourcePanel;
	}

	private void loadImagefromGUI(final int newC, final int newT) {
		final boolean reload = newC == plugin.channel && newT == plugin.frame;
		if (!reload && askUserConfirmation
				&& !guiUtils
						.getConfirmation(
								"You are currently tracing position C=" + plugin.channel + ", T=" + plugin.frame
										+ ". Start tracing C=" + newC + ", T=" + newT + "?",
								"Change Hyperstack Position?")) {
			return;
		}
		abortCurrentOperation();
		changeState(LOADING);
		final boolean hessianDataExists = plugin.isHessianEnabled();
		plugin.reloadImage(newC, newT); // nullifies hessianData
		if (!reload)
			plugin.getImagePlus().setPosition(newC, plugin.getImagePlus().getZ(), newT);
		plugin.showMIPOverlays(0);
		if (plugin.filteredImageLoaded()) {
			final String[] choices = new String[] { "Unload. I'll load new data manually", "Reload",
					"Do nothing. Leave as is" };
			final String choice = guiUtils.getChoice("What should be done with cached filtered image?",
					"Reload Filtered Data?", choices, (reload) ? choices[1] : choices[0]);
			if (choice != null && choice.startsWith("Unload")) {
				flushFilteredData();
			} else if (choice != null && choice.startsWith("Reload")) {
				loadCachedDataImage(false, false, plugin.filteredFileImage);
			}
		}
		if (hessianDataExists)
			enableHessian(true);
		resetState();
		showStatus(reload ? "Image reloaded into memory..." : null, true);
	}

	private JPanel viewsPanel() {
		final JPanel viewsPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		gdb.gridwidth = 1;

		final JPanel mipPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		final JCheckBox mipOverlayCheckBox = new JCheckBox("Overlay MIP(s) at");
		mipPanel.add(mipOverlayCheckBox);
		final JSpinner mipSpinner = GuiUtils.integerSpinner(20, 10, 80, 1);
		mipSpinner.addChangeListener(e -> mipOverlayCheckBox.setSelected(false));
		mipPanel.add(mipSpinner);
		mipPanel.add(GuiUtils.leftAlignedLabel(" % opacity", true));
		mipOverlayCheckBox.addActionListener(e -> {
			if (!plugin.accessToValidImageData()) {
				noValidImageDataError();
				mipOverlayCheckBox.setSelected(false);
			} else if (plugin.is2D()) {
				guiUtils.error(plugin.getImagePlus().getTitle() + " has no depth. Cannot generate projection.");
				mipOverlayCheckBox.setSelected(false);
			} else {
				plugin.showMIPOverlays(false,
						(mipOverlayCheckBox.isSelected()) ? (int) mipSpinner.getValue() * 0.01 : 0);
			}
		});
		viewsPanel.add(mipPanel, gdb);
		++gdb.gridy;

		final JCheckBox diametersCheckBox = new JCheckBox("Draw diameters in XY view", plugin.getDrawDiametersXY());
		diametersCheckBox.addItemListener(e -> plugin.setDrawDiametersXY(e.getStateChange() == ItemEvent.SELECTED));
		viewsPanel.add(diametersCheckBox, gdb);
		++gdb.gridy;

		final JCheckBox zoomAllPanesCheckBox = new JCheckBox("Apply zoom changes to all views",
				!plugin.isZoomAllPanesDisabled());
		zoomAllPanesCheckBox
				.addItemListener(e -> plugin.disableZoomAllPanes(e.getStateChange() == ItemEvent.DESELECTED));
		viewsPanel.add(zoomAllPanesCheckBox, gdb);
		++gdb.gridy;

		final String bLabel = (plugin.getSinglePane()) ? "Display" : "Rebuild";
		final JButton refreshPanesButton = new JButton(bLabel + " ZY/XZ Views");
		refreshPanesButton.addActionListener(e -> {
			final boolean noImageData = !plugin.accessToValidImageData();
			if (noImageData && pathAndFillManager.size() == 0) {
				uncomputableCanvasError();
				return;
			}
			if (plugin.getImagePlus() == null) {
				guiUtils.error("There is no loaded image. Please load one or create a display canvas.",
						"No Canvas Exist");
				return;
			}
			if (plugin.is2D()) {
				guiUtils.error(plugin.getImagePlus().getTitle() + " has no depth. Cannot generate side views!");
				return;
			}
			showStatus("Rebuilding ZY/XZ views...", false);
			changeState(LOADING);
			try {
				plugin.setSinglePane(false);
				plugin.rebuildZYXZpanes();
				arrangeCanvases(false);
				showStatus("ZY/XZ views reloaded...", true);
				refreshPanesButton.setText("Rebuild ZY/XZ views");
			} catch (final Throwable t) {
				if (t instanceof OutOfMemoryError) {
					guiUtils.error("Out of Memory: There is not enough RAM to load side views!");
				} else {
					guiUtils.error("An error occured. See Console for details.");
					t.printStackTrace();
				}
				plugin.setSinglePane(true);
				if (noImageData)
					plugin.rebuildDisplayCanvases();
				showStatus("Out of memory error...", true);
			} finally {
				resetState();
			}
		});

		rebuildCanvasButton = new JButton();
		updateRebuildCanvasButton();
		rebuildCanvasButton.addActionListener(e -> {
			final boolean noImageData = !plugin.accessToValidImageData();
			if (noImageData && pathAndFillManager.size() == 0) {
				uncomputableCanvasError();
				return;
			}
			if (noImageData) {
				changeState(LOADING);
				showStatus("Resizing Canvas...", false);
				if (plugin.getImagePlus(MultiDThreePanes.XZ_PLANE) == null
						&& plugin.getImagePlus(MultiDThreePanes.ZY_PLANE) == null) {
					plugin.setSinglePane(true);
				}
				plugin.rebuildDisplayCanvases(); // will change UI state
				showStatus("Canvas rebuilt...", true);
			} else {
				guiUtils.error("Currently, this command is only available for display canvases. To resize "
						+ "current image use IJ's command <i>Image> Adjust> Canvas Size...</i>");
				return;
			}
		});

		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 0));
		buttonPanel.add(rebuildCanvasButton);
		buttonPanel.add(refreshPanesButton);
		gdb.fill = GridBagConstraints.NONE;
		viewsPanel.add(buttonPanel, gdb);
		return viewsPanel;
	}

	private void uncomputableCanvasError() {
		guiUtils.error("Image data is not available and no paths exist to compute a display canvas.");
	}

	private JPanel tracingPanel() {
		final JPanel tPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		final JCheckBox confirmTemporarySegmentsCheckbox = new JCheckBox("Confirm temporary segments",
				confirmTemporarySegments);
		final JCheckBox confirmCheckbox = new JCheckBox("Pressing 'Y' twice finishes path", finishOnDoubleConfimation);
		final JCheckBox finishCheckbox = new JCheckBox("Pressing 'N' twice cancels path", discardOnDoubleCancellation);
		confirmTemporarySegmentsCheckbox.addItemListener(e -> {
			confirmTemporarySegments = (e.getStateChange() == ItemEvent.SELECTED);
			confirmCheckbox.setEnabled(confirmTemporarySegments);
			finishCheckbox.setEnabled(confirmTemporarySegments);
		});

		confirmCheckbox.addItemListener(e -> finishOnDoubleConfimation = (e.getStateChange() == ItemEvent.SELECTED));
		confirmCheckbox.addItemListener(e -> discardOnDoubleCancellation = (e.getStateChange() == ItemEvent.SELECTED));
		tPanel.add(confirmTemporarySegmentsCheckbox, gdb);
		++gdb.gridy;
		gdb.insets = new Insets(0, MARGIN * 3, 0, 0);
		tPanel.add(confirmCheckbox, gdb);
		++gdb.gridy;
		tPanel.add(finishCheckbox, gdb);
		++gdb.gridy;
		gdb.insets = new Insets(0, 0, 0, 0);
		return tPanel;

	}

	private JPanel interactionPanel() {
		final JPanel intPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		intPanel.add(extraColorsPanel(), gdb);
		++gdb.gridy;
		intPanel.add(nodePanel(), gdb);
		++gdb.gridy;

		final JCheckBox canvasCheckBox = new JCheckBox("Activate canvas on mouse hovering",
				plugin.autoCanvasActivation);
		guiUtils.addTooltip(canvasCheckBox, "Whether the image window should be brought to front as soon as the mouse "
				+ "pointer enters it. This ensures shortcuts work as expected.");
		canvasCheckBox.addItemListener(e -> plugin.enableAutoActivation(e.getStateChange() == ItemEvent.SELECTED));
		intPanel.add(canvasCheckBox, gdb);
		++gdb.gridy;
		return intPanel;
	}

	private JPanel nodePanel() {
		final InteractiveTracerCanvas canvas = plugin.getXYCanvas();
		final JSpinner nodeSpinner = GuiUtils.doubleSpinner((canvas == null) ? 1 : canvas.nodeDiameter(), 0, 100, 1, 0);
		nodeSpinner.addChangeListener(e -> {
			final double value = (double) (nodeSpinner.getValue());
			canvas.setNodeDiameter(value);
			if (!plugin.getSinglePane()) {
				plugin.getXZCanvas().setNodeDiameter(value);
				plugin.getZYCanvas().setNodeDiameter(value);
			}
			plugin.updateAllViewers();
		});
		final JButton defaultsButton = new JButton("Default");
		defaultsButton.addActionListener(e -> {
			plugin.getXYCanvas().setNodeDiameter(-1);
			if (!plugin.getSinglePane()) {
				plugin.getXZCanvas().setNodeDiameter(-1);
				plugin.getZYCanvas().setNodeDiameter(-1);
			}
			nodeSpinner.setValue(plugin.getXYCanvas().nodeDiameter());
			showStatus("Node scale reset", true);
		});

		final JPanel p = new JPanel();
		p.setLayout(new GridBagLayout());
		final GridBagConstraints c = GuiUtils.defaultGbc();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 3;
		c.ipadx = 0;
		p.add(GuiUtils.leftAlignedLabel("Path nodes rendering scale: ", true));
		c.gridx = 1;
		p.add(nodeSpinner, c);
		c.fill = GridBagConstraints.NONE;
		c.gridx = 2;
		p.add(defaultsButton);
		return p;
	}

	private JPanel extraColorsPanel() {

		final LinkedHashMap<String, Color> hm = new LinkedHashMap<>();
		final InteractiveTracerCanvas canvas = plugin.getXYCanvas();
		hm.put("Canvas annotations", (canvas == null) ? null : canvas.getAnnotationsColor());
		hm.put("Fills", (canvas == null) ? null : canvas.getFillColor());
		hm.put("Unconfirmed paths", (canvas == null) ? null : canvas.getUnconfirmedPathColor());
		hm.put("Temporary paths", (canvas == null) ? null : canvas.getTemporaryPathColor());

		final JComboBox<String> colorChoice = new JComboBox<>();
		for (final Entry<String, Color> entry : hm.entrySet())
			colorChoice.addItem(entry.getKey());

		final String selectedKey = String.valueOf(colorChoice.getSelectedItem());
		final ColorChooserButton cChooser = new ColorChooserButton(hm.get(selectedKey), "Change", 1,
				SwingConstants.RIGHT);

		colorChoice.addActionListener(
				e -> cChooser.setSelectedColor(hm.get(String.valueOf(colorChoice.getSelectedItem())), false));

		cChooser.addColorChangedListener(newColor -> {
			final String selectedKey1 = String.valueOf(colorChoice.getSelectedItem());
			switch (selectedKey1) {
			case "Canvas annotations":
				plugin.setAnnotationsColorAllPanes(newColor);
				break;
			case "Fills":
				plugin.getXYCanvas().setFillColor(newColor);
				if (!plugin.getSinglePane()) {
					plugin.getZYCanvas().setFillColor(newColor);
					plugin.getXZCanvas().setFillColor(newColor);
				}
				break;
			case "Unconfirmed paths":
				plugin.getXYCanvas().setUnconfirmedPathColor(newColor);
				if (!plugin.getSinglePane()) {
					plugin.getZYCanvas().setUnconfirmedPathColor(newColor);
					plugin.getXZCanvas().setUnconfirmedPathColor(newColor);
				}
				break;
			case "Temporary paths":
				plugin.getXYCanvas().setTemporaryPathColor(newColor);
				if (!plugin.getSinglePane()) {
					plugin.getZYCanvas().setTemporaryPathColor(newColor);
					plugin.getXZCanvas().setTemporaryPathColor(newColor);
				}
				break;
			default:
				throw new IllegalArgumentException("Unrecognized option");
			}
			plugin.updateAllViewers();
		});

		final JPanel p = new JPanel();
		p.setLayout(new GridBagLayout());
		final GridBagConstraints c = GuiUtils.defaultGbc();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 3;
		c.ipadx = 0;
		p.add(GuiUtils.leftAlignedLabel("Colors: ", true));
		c.gridx = 1;
		p.add(colorChoice, c);
		c.fill = GridBagConstraints.NONE;
		c.gridx = 2;
		p.add(cChooser);
		return p;
	}

	private JPanel miscPanel() {
		final JPanel miscPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		final JCheckBox winLocCheckBox = new JCheckBox("Remember window locations", plugin.prefs.isSaveWinLocations());
		guiUtils.addTooltip(winLocCheckBox, "Whether position of dialogs should be preserved across restarts");
		winLocCheckBox.addItemListener(e -> plugin.prefs.setSaveWinLocations(e.getStateChange() == ItemEvent.SELECTED));
		miscPanel.add(winLocCheckBox, gdb);
		++gdb.gridy;
		final JCheckBox compressedXMLCheckBox = new JCheckBox("Use compression when saving traces",
				plugin.useCompressedXML);
		compressedXMLCheckBox
				.addItemListener(e -> plugin.useCompressedXML = (e.getStateChange() == ItemEvent.SELECTED));
		miscPanel.add(compressedXMLCheckBox, gdb);
		++gdb.gridy;
		final JCheckBox askUserConfirmationCheckBox = new JCheckBox("Skip confirmation dialogs", !askUserConfirmation);
		guiUtils.addTooltip(askUserConfirmationCheckBox,
				"Whether \"Are you sure?\" prompts should precede major operations");
		askUserConfirmationCheckBox
				.addItemListener(e -> askUserConfirmation = e.getStateChange() == ItemEvent.DESELECTED);
		miscPanel.add(askUserConfirmationCheckBox, gdb);
		++gdb.gridy;
		debugCheckBox = new JCheckBox("Debug mode", SNT.isDebugMode());
		debugCheckBox.addItemListener(e -> SNT.setDebugMode(e.getStateChange() == ItemEvent.SELECTED));
		miscPanel.add(debugCheckBox, gdb);
		++gdb.gridy;
		final JButton resetbutton = GuiUtils.smallButton("Reset Preferences...");
		resetbutton.addActionListener(e -> {
			(new CmdRunner(ResetPrefsCmd.class)).execute();
		});
		gdb.fill = GridBagConstraints.NONE;
		miscPanel.add(resetbutton, gdb);
		return miscPanel;
	}

	@SuppressWarnings("deprecation")
	private JPanel legacy3DViewerPanel() {

		final String VIEWER_NONE = "None";
		final String VIEWER_WITH_IMAGE = "New with image...";
		final String VIEWER_EMPTY = "New without image";

		// Define UI components
		final JComboBox<String> univChoice = new JComboBox<>();
		final JButton applyUnivChoice = new JButton("Apply");
		final JComboBox<String> displayChoice = new JComboBox<>();
		final JButton applyDisplayChoice = new JButton("Apply");
		final JButton refreshList = GuiUtils.smallButton("Refresh List");
		final JComboBox<String> actionChoice = new JComboBox<>();
		final JButton applyActionChoice = new JButton("Apply");

		final LinkedHashMap<String, Image3DUniverse> hm = new LinkedHashMap<>();
		hm.put(VIEWER_NONE, null);
		if (!plugin.tracingHalted && !plugin.is2D()) {
			hm.put(VIEWER_WITH_IMAGE, null);
		}
		hm.put(VIEWER_EMPTY, null);
		for (final Image3DUniverse univ : Image3DUniverse.universes) {
			hm.put(univ.allContentsString(), univ);
		}

		// Build choices widget for viewers
		univChoice.setPrototypeDisplayValue(VIEWER_WITH_IMAGE);
		for (final Entry<String, Image3DUniverse> entry : hm.entrySet()) {
			univChoice.addItem(entry.getKey());
		}
		univChoice.addActionListener(e -> {

			final boolean none = VIEWER_NONE.equals(String.valueOf(univChoice.getSelectedItem()));
			applyUnivChoice.setEnabled(!none);
		});
		applyUnivChoice.addActionListener(new ActionListener() {

			private void resetChoice() {
				univChoice.setSelectedItem(VIEWER_NONE);
				applyUnivChoice.setEnabled(false);
				final boolean validViewer = plugin.use3DViewer && plugin.get3DUniverse() != null;
				displayChoice.setEnabled(validViewer);
				applyDisplayChoice.setEnabled(validViewer);
				actionChoice.setEnabled(validViewer);
				applyActionChoice.setEnabled(validViewer);
			}

			@Override
			public void actionPerformed(final ActionEvent e) {

				applyUnivChoice.setEnabled(false);

				final String selectedKey = String.valueOf(univChoice.getSelectedItem());
				if (VIEWER_NONE.equals(selectedKey)) {
					plugin.set3DUniverse(null);
					resetChoice();
					return;
				}

				Image3DUniverse univ;
				univ = hm.get(selectedKey);
				if (univ == null) {

					// Presumably a new viewer was chosen. Let's double-check
					final boolean newViewer = selectedKey.equals(VIEWER_WITH_IMAGE) || selectedKey.equals(VIEWER_EMPTY);
					if (!newViewer && !guiUtils.getConfirmation(
							"The chosen viewer does not seem to be available. Create a new one?",
							"Viewer Unavailable")) {
						resetChoice();
						return;
					}
					univ = new Image3DUniverse(512, 512);
				}

				plugin.set3DUniverse(univ);

				if (VIEWER_WITH_IMAGE.equals(selectedKey)) {

					final int defResFactor = Content.getDefaultResamplingFactor(plugin.getImagePlus(),
							ContentConstants.VOLUME);
					final Double userResFactor = guiUtils.getDouble("<HTML><body><div style='width:"
							+ Math.min(getWidth(), 500) + ";'>"
							+ "Please specify the image resampling factor. The default factor for current image is "
							+ defResFactor + ".", "Image Resampling Factor", defResFactor);

					if (userResFactor == null) { // user pressed cancel
						plugin.set3DUniverse(null);
						resetChoice();
						return;
					}

					final int resFactor = (Double.isNaN(userResFactor) || userResFactor < 1) ? defResFactor
							: userResFactor.intValue();
					plugin.prefs.set3DViewerResamplingFactor(resFactor);
					plugin.updateImageContent(resFactor);
				}

				// Add PointListener/Keylistener
				new QueueJumpingKeyListener(plugin, univ);
				ImageWindow3D window = univ.getWindow();
				if (univ.getWindow() == null) {
					window = new ImageWindow3D("SNT Legacy 3D Viewer", univ);
					window.setSize(512, 512);
					univ.init(window);
				} else {
					univ.resetView();
				}
				window.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosed(final WindowEvent e) {
						resetChoice();
					}
				});
				window.setVisible(true);
				resetChoice();
				showStatus("3D Viewer enabled: " + selectedKey, true);
			}
		});

		// Build widget for rendering choices
		displayChoice.addItem("Lines and discs");
		displayChoice.addItem("Lines");
		displayChoice.addItem("Surface reconstructions");
		applyDisplayChoice.addActionListener(e -> {

			switch (String.valueOf(displayChoice.getSelectedItem())) {
			case "Lines":
				plugin.setPaths3DDisplay(SimpleNeuriteTracer.DISPLAY_PATHS_LINES);
				break;
			case "Lines and discs":
				plugin.setPaths3DDisplay(SimpleNeuriteTracer.DISPLAY_PATHS_LINES_AND_DISCS);
				break;
			default:
				plugin.setPaths3DDisplay(SimpleNeuriteTracer.DISPLAY_PATHS_SURFACE);
				break;
			}
		});

		// Build refresh button
		refreshList.addActionListener(e -> {
			for (final Image3DUniverse univ : Image3DUniverse.universes) {
				if (hm.containsKey(univ.allContentsString()))
					continue;
				hm.put(univ.allContentsString(), univ);
				univChoice.addItem(univ.allContentsString());
			}
			showStatus("Viewers list updated...", true);
		});

		// Build actions
		class ApplyLabelsAction extends AbstractAction {

			final static String LABEL = "Apply Color Labels...";

			@Override
			public void actionPerformed(final ActionEvent e) {
				final File imageFile = openFile("Choose Labels Image...", (File) null);
				if (imageFile == null)
					return; // user pressed cancel
				try {
					plugin.statusService.showStatus(("Loading " + imageFile.getName()));
					final Dataset ds = plugin.datasetIOService.open(imageFile.getAbsolutePath());
					final ImagePlus colorImp = plugin.convertService.convert(ds, ImagePlus.class);
					showStatus("Applying color labels...", false);
					plugin.setColorImage(colorImp);
					showStatus("Labels image loaded...", true);

				} catch (final IOException exc) {
					guiUtils.error("Could not open " + imageFile.getAbsolutePath() + ". Maybe it is not a valid image?",
							"IO Error");
					exc.printStackTrace();
					return;
				}
			}
		}

		// Assemble widget for actions
		final String COMPARE_AGAINST = "Compare Reconstruction Against...";
		actionChoice.addItem(ApplyLabelsAction.LABEL);
		actionChoice.addItem(COMPARE_AGAINST);
		applyActionChoice.addActionListener(new ActionListener() {

			final ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);

			@Override
			public void actionPerformed(final ActionEvent e) {

				switch (String.valueOf(actionChoice.getSelectedItem())) {
				case ApplyLabelsAction.LABEL:
					new ApplyLabelsAction().actionPerformed(ev);
					break;
				case COMPARE_AGAINST:
					(new CmdRunner(ShowCorrespondencesCmd.class)).execute();
					break;
				default:
					break;
				}
			}
		});

		// Set defaults
		univChoice.setSelectedItem(VIEWER_NONE);
		applyUnivChoice.setEnabled(false);
		displayChoice.setEnabled(false);
		applyDisplayChoice.setEnabled(false);
		actionChoice.setEnabled(false);
		applyActionChoice.setEnabled(false);

		// Build panel
		final JPanel p = new JPanel();
		p.setLayout(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();
		c.ipadx = 0;
		c.insets = new Insets(0, 0, 0, 0);
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		// row 1
		c.gridy = 0;
		c.gridx = 0;
		p.add(GuiUtils.leftAlignedLabel("Viewer: ", true), c);
		c.gridx++;
		c.weightx = 1;
		p.add(univChoice, c);
		c.gridx++;
		c.weightx = 0;
		p.add(applyUnivChoice, c);
		c.gridx++;

		// row 2
		c.gridy++;
		c.gridx = 1;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.NONE;
		p.add(refreshList, c);

		// row 3
		c.gridy++;
		c.gridx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		p.add(GuiUtils.leftAlignedLabel("Mode: ", true), c);
		c.gridx++;
		c.fill = GridBagConstraints.HORIZONTAL;
		p.add(displayChoice, c);
		c.gridx++;
		c.fill = GridBagConstraints.NONE;
		p.add(applyDisplayChoice, c);
		c.gridx++;

		// row 4
		c.gridy++;
		c.gridx = 0;
		c.fill = GridBagConstraints.NONE;
		p.add(GuiUtils.leftAlignedLabel("Actions: ", true), c);
		c.gridx++;
		c.fill = GridBagConstraints.HORIZONTAL;
		p.add(actionChoice, c);
		c.gridx++;
		c.fill = GridBagConstraints.NONE;
		p.add(applyActionChoice, c);
		return p;
	}

	private void addSpacer(final JPanel panel, final GridBagConstraints c) {
		// extremely lazy implementation of a vertical spacer
		IntStream.rangeClosed(1, 4).forEach(i -> {
			panel.add(new JPanel(), c);
			c.gridy++;
		});
	}

	private JPanel largeMsg(final String msg) {
		final JTextArea ta = new JTextArea();
		final Font defFont = new JLabel().getFont();
		final Font font = defFont.deriveFont(defFont.getSize() * .8f);
		ta.setBackground(getBackground());
		ta.setEditable(false);
		ta.setMargin(null);
		ta.setColumns(20);
		ta.setBorder(null);
		ta.setAutoscrolls(true);
		ta.setLineWrap(true);
		ta.setWrapStyleWord(true);
		ta.setFocusable(false);
		ta.setText(msg);
		ta.setEnabled(false);
		ta.setFont(font);
		final JPanel p = new JPanel(new BorderLayout());
		p.setBackground(getBackground());
		p.add(ta, BorderLayout.NORTH);
		return p;
	}

	private JPanel reconstructionViewerPanel() {
		openRecViewer = new JButton("Open Reconstruction Viewer");
		openRecViewer.addActionListener(e -> {
			// if (noPathsError()) return;
			if (recViewer == null) {
				getReconstructionViewer(true);
				recViewer.setDefaultColor(new ColorRGB(plugin.deselectedColor.getRed(),
						plugin.deselectedColor.getGreen(), plugin.deselectedColor.getBlue()));
				if (pathAndFillManager.size() > 0)
					recViewer.syncPathManagerList();
				recViewerFrame = recViewer.show();
				recViewerFrame.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosing(final WindowEvent e) {
						openRecViewer.setEnabled(true);
						recViewer = null;
						recViewerFrame = null;
					}
				});
			}
		});

		// Build panel
		final JPanel panel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = new GridBagConstraints();
		gdb.fill = GridBagConstraints.HORIZONTAL;
		gdb.weightx = 0.5;
		panel.add(openRecViewer, gdb);
		return panel;
	}

	private JPanel statusButtonPanel() {
		final JPanel statusChoicesPanel = new JPanel();
		statusChoicesPanel.setLayout(new GridBagLayout());
		statusChoicesPanel.setBorder(new EmptyBorder(0, 0, MARGIN * 2, 0));
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		gbc.ipadx = 0;
		gbc.ipady = 0;

		gbc.insets = new Insets(0, 0, 0, 0);
		keepSegment = GuiUtils.smallButton(hotKeyLabel("Yes", "Y"));
		junkSegment = GuiUtils.smallButton(hotKeyLabel("No", "N"));
		equalizeButtons(keepSegment, junkSegment);
		keepSegment.addActionListener(listener);
		gbc.weightx = 0.25;
		statusChoicesPanel.add(keepSegment, gbc);
		gbc.ipadx = 2;
		junkSegment.addActionListener(listener);
		gbc.gridx = 1;
		statusChoicesPanel.add(junkSegment, gbc);
		completePath = GuiUtils.smallButton(hotKeyLabel("Finish", "F"));
		completePath.addActionListener(listener);
		gbc.gridx = 2;
		statusChoicesPanel.add(completePath, gbc);
		gbc.gridx = 3;
		final JButton abortButton = GuiUtils.smallButton(hotKeyLabel(hotKeyLabel("Cancel/Esc", "C"), "Esc"));
		abortButton.addActionListener(e -> abortCurrentOperation());
		gbc.gridx = 4;
		gbc.ipadx = 0;
		statusChoicesPanel.add(abortButton, gbc);
		return statusChoicesPanel;
	}

	private JPanel statusPanel() {
		final JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BorderLayout());
		statusText = new JLabel("Loading SNT...");
		statusText.setOpaque(true);
		//statusText.setBackground(Color.WHITE);
		statusText.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED),
				BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN)));
		statusPanel.add(statusText, BorderLayout.CENTER);
		final JPanel buttonPanel = statusButtonPanel();
		statusPanel.add(buttonPanel, BorderLayout.SOUTH);
		statusPanel.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN * 2, MARGIN));
		return statusPanel;
	}

	private JPanel filteredImagePanel() {
		filteredImgPathField = new JTextField();
		final JPopupMenu optionsMenu = new JPopupMenu();
		filteredImgBrowseButton = new JButton("Browse");
		filteredImgOptionsButton = optionsButton(optionsMenu, filteredImgBrowseButton);

		filteredImgActivateCheckbox = new JCheckBox(hotKeyLabel("Trace on filtered Image", "I"));
		guiUtils.addTooltip(filteredImgActivateCheckbox,
				"Whether auto-tracing should be computed on the filtered image");
		filteredImgActivateCheckbox
				.addActionListener(e -> enableFilteredImgTracing(filteredImgActivateCheckbox.isSelected()));

		filteredImgPathField.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void changedUpdate(final DocumentEvent e) {
				updateFilteredFileField();
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				updateFilteredFileField();
			}

			@Override
			public void insertUpdate(final DocumentEvent e) {
				updateFilteredFileField();
			}

		});

		filteredImgBrowseButton.addActionListener(e -> {
			final File file = openFile("Choose Filtered Image", new File(filteredImgPathField.getText()));
			if (file == null)
				return;
			filteredImgPathField.setText(file.getAbsolutePath());
		});

		final JMenuItem loadMenuItem = new JMenuItem("Load Image...");
		optionsMenu.add(loadMenuItem);
		loadMenuItem.addActionListener(e -> {
			if (plugin.isTracingOnFilteredImageAvailable()) {
				// Toggle: set action to disable filtered tracing
				if (!guiUtils.getConfirmation("Disable access to filtered image?", "Unload Image?"))
					return;
				// reset cached filtered image
				flushFilteredData();
			} else { // toggle: set action to enable 'filtered tracing'
				loadCachedDataImage(true, false, plugin.filteredFileImage);
				setFastMarchSearchEnabled(plugin.tubularGeodesicsTracingEnabled);
			}
			if (plugin.isTracingOnFilteredImageAvailable())
				loadMenuItem.setText("Flush Cached Image...");
			else
				loadMenuItem.setText("Load Image...");
		});
		final JMenuItem revealMenuItem = new JMenuItem("Show File in File Explorer");
		optionsMenu.add(revealMenuItem);
		revealMenuItem.addActionListener(e -> {
			try {
				File file = new File(filteredImgPathField.getText());
				if (SNT.fileAvailable(file)) {
					Desktop.getDesktop().open(file.getParentFile());
					// TODO: Move to java9
					// Desktop.getDesktop().browseFileDirectory(file);
				} else {
					guiUtils.error("Current image path is not valid.");
				}
			} catch (NullPointerException | IllegalArgumentException | IOException iae) {
				guiUtils.error("An error occured: image directory not available?");
			}
		});
		optionsMenu.addSeparator();
		final JMenuItem makeImgMenuItem = new JMenuItem("Generate Filtered Image...");
		optionsMenu.add(makeImgMenuItem);
		makeImgMenuItem.addActionListener(e -> {
			guiUtils.error("Not yet implemented");
		});

		filteredImgPanel = new JPanel();
		filteredImgPanel.setLayout(new GridBagLayout());
		GridBagConstraints c = GuiUtils.defaultGbc();
		c.ipadx = 0;

		// header row
		addSeparatorWithURL(filteredImgPanel, "Tracing on Filtered Image:", true, c);
		c.gridy++;

		// row 1
		c.gridwidth = 3;
		filteredImgPanel.add(GuiUtils.leftAlignedLabel("File: ", true));
		c.gridx = 1;
		filteredImgPanel.add(filteredImgPathField, c);
		c.fill = GridBagConstraints.NONE;
		c.gridx = 2;
		filteredImgPanel.add(filteredImgBrowseButton, c);
		c.gridy++;

		// row 2
		filteredImgOverlayCheckbox = new JCheckBox("Render in overlay at ");
		final JSpinner mipSpinner = GuiUtils.integerSpinner(20, 10, 80, 1);
		mipSpinner.addChangeListener(e -> filteredImgOverlayCheckbox.setSelected(false));
		filteredImgOverlayCheckbox.addActionListener(e -> {
			if (!plugin.filteredImageLoaded()) {
				noFilteredImgAvailableError();
				return;
			}
			plugin.showMIPOverlays(true,
					(filteredImgOverlayCheckbox.isSelected()) ? (int) mipSpinner.getValue() * 0.01 : 0);
		});
		final JPanel overlayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		overlayPanel.add(filteredImgOverlayCheckbox);
		overlayPanel.add(mipSpinner);
		overlayPanel.add(GuiUtils.leftAlignedLabel(" % opacity          ", true));

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridwidth = 2;
		filteredImgPanel.add(overlayPanel, c);
		c.gridwidth = 3;
		c.fill = GridBagConstraints.NONE;

		c.gridx = 2;
		equalizeButtons(filteredImgOptionsButton, filteredImgBrowseButton);
		filteredImgPanel.add(filteredImgOptionsButton, c);
		c.gridy++;

		// row 3
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		filteredImgPanel.add(filteredImgActivateCheckbox, c);
		c.gridy++;

		return filteredImgPanel;
	}

	private JButton optionsButton(final JPopupMenu optionsMenu, JButton templateButton) {
		JButton optionsButton = new JButton();
		if (templateButton == null) templateButton = new JButton("Browse");
		this.equalizeButtons(optionsButton, templateButton);
		IconFactory.applyIcon(optionsButton, templateButton.getFont().getSize2D(), GLYPH.OPTIONS);
		optionsButton.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(final MouseEvent e) {
				if (optionsButton.isEnabled()) 
					optionsMenu.show(optionsButton, optionsButton.getWidth() / 2,
						optionsButton.getHeight() / 2);
			}
		});
		return optionsButton;
	}
	private void equalizeButtons(final JButton b1, final JButton b2) {
		if (b1.getText().length() >= b2.getText().length()) {
			b2.setSize(b1.getSize());
			b2.setMinimumSize(b1.getMinimumSize());
			b2.setPreferredSize(b1.getPreferredSize());
			b2.setMaximumSize(b1.getMaximumSize());
		} else {
			b1.setSize(b2.getSize());
			b1.setMinimumSize(b2.getMinimumSize());
			b1.setPreferredSize(b2.getPreferredSize());
			b1.setMaximumSize(b2.getMaximumSize());
		}
	}

	private void flushFilteredData() {
		plugin.filteredData = null;
		plugin.doSearchOnFilteredData = false;
		if (filteredImgOverlayCheckbox.isSelected()) {
			filteredImgOverlayCheckbox.setSelected(false);
			plugin.showMIPOverlays(true, 0);
		}
		if (plugin.tubularGeodesicsTracingEnabled) {
			setFastMarchSearchEnabled(false);
		}
		updateFilteredImgFields(true);
	}

	private void flushCachedTubeness() {
		plugin.cachedTubeness = null;
		updateHessianPanel();
	}

	protected File openFile(final String promptMsg, final String extension) {
		final File suggestedFile = SNT.findClosestPair(plugin.prefs.getRecentFile(), extension);
		return openFile(promptMsg, suggestedFile);
	}

	private File openFile(final String promptMsg, final File suggestedFile) {
		final File openedFile = plugin.legacyService.getIJ1Helper().openDialog(promptMsg, suggestedFile);
		if (openedFile != null)
			plugin.prefs.setRecentFile(openedFile);
		return openedFile;
	}

	protected File saveFile(final String promptMsg, final File suggestedFile, final String fallbackExtension) {
		final File fFile = (suggestedFile == null)
				? SNT.findClosestPair(plugin.prefs.getRecentFile(), fallbackExtension)
				: suggestedFile;
		final File savedFile = plugin.legacyService.getIJ1Helper().saveDialog(promptMsg, fFile, fallbackExtension);
		if (savedFile != null)
			plugin.prefs.setRecentFile(savedFile);
		return savedFile;
	}

	private void loadCachedDataImage(final boolean warnUserOnMemory, final boolean isTubeness, final File file) {
		if (warnUserOnMemory) {
			final int byteDepth = 32 / 8;
			final ImagePlus tracingImp = plugin.getImagePlus();
			final long megaBytesExtra = (((long) tracingImp.getWidth()) * tracingImp.getHeight()
					* tracingImp.getNSlices() * byteDepth * 2) / (1024 * 1024);
			final long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
			if (megaBytesExtra > 0.8 * maxMemory && !guiUtils.getConfirmation( //
					"Loading an extra image will likely require " + megaBytesExtra + "MiB of " //
							+ "RAM. Currently only " + maxMemory + " MiB are available. " //
							+ "Proceed nevertheless?",
					"Confirm Loading?")) {
				return;
			}
		}
		if (isTubeness && file == null)
			computeHessian();
		else
			loadImageData(isTubeness, file);
	}

	private void loadImageData(final boolean isTubeness, final File file) {

		showStatus("Loading image. Please wait...", false);
		changeState(CRUNCHING_DATA);
		activeWorker = new ActiveWorker() {

			@Override
			protected String doInBackground() throws Exception {

				try {
					if (isTubeness) {
						plugin.loadTubenessImage(file);
					} else {
						plugin.loadFilteredImage(file);
					}
				} catch (final IllegalArgumentException e1) {
					return ("Could not load " + file.getAbsolutePath() + ":<br>"
							+ e1.getMessage());
				} catch (final IOException e2) {
					e2.printStackTrace();
					return ("Loading of image failed. See Console for details.");
				} catch (final OutOfMemoryError e3) {
					e3.printStackTrace();
					return ("It seems there is not enough memory to proceed. See Console for details.");
				}
				return null;
			}

			private void flushData() {
				if (isTubeness) {
					flushCachedTubeness();
				} else {
					flushFilteredData();
				}
			}

			@Override
			public boolean kill() {
				flushData();
				return cancel(true);
			}

			@Override
			protected void done() {
				try {
					final String errorMsg = (String) get();
					if (errorMsg != null) {
						guiUtils.error(errorMsg);
						flushData();
					}
				} catch (InterruptedException | ExecutionException e) {
					SNT.error("ActiveWorker failure", e);
				}
				if (isTubeness) {
					updateHessianPanel();
				} else {
					updateFilteredImgFields(plugin.filteredImageLoaded());
				}
				resetState();
				showStatus(null, false);
			}
		};
		activeWorker.run();
	}

	private void computeHessian() {
		assert SwingUtilities.isEventDispatchThread();

		showStatus("Generating and parsing Tubeness image...", false);
		changeState(CRUNCHING_DATA);

		activeWorker = new ActiveWorker() {
			@Override
			protected Object doInBackground() {

				final ComputeTubenessImg cacti = new ComputeTubenessImg();
				cacti.setContext(plugin.getContext());
				// execute the command in the same thread, blocking until complete
				cacti.run();
				plugin.loadTubenessImage(cacti.getTubenessImp(), false);
				return null;
			}

			@Override
			protected void done() {
				updateHessianPanel();
				resetState();
				showStatus("Computation completed.", false);
			}

			@Override
			public boolean kill() {
				flushCachedTubeness();
				return super.cancel(true);
			}

		};
		activeWorker.execute();
	}

	private void updateFilteredImgFields(final boolean resetHessian) {
		SwingUtilities.invokeLater(() -> {
			if (resetHessian)
				enableHessian(false);
			final boolean successfullyLoaded = plugin.isTracingOnFilteredImageAvailable();
			filteredImgPathField.setEnabled(!successfullyLoaded);
			filteredImgBrowseButton.setEnabled(!successfullyLoaded);
			GuiUtils.enableComponents(filteredImgOverlayCheckbox.getParent(), successfullyLoaded);
			filteredImgActivateCheckbox.setEnabled(successfullyLoaded);
			if (!successfullyLoaded)
				filteredImgActivateCheckbox.setSelected(false);
		});
	}

	private void updateFilteredFileField() {
		if (filteredImgPathField == null)
			return;
		final String path = filteredImgPathField.getText();
		final boolean validFile = path != null && SNT.fileAvailable(new File(path));
		filteredImgPathField.setForeground((validFile) ? new JTextField().getForeground() : Color.RED);
		filteredImgOptionsButton.setEnabled(validFile);
		filteredImgActivateCheckbox.setEnabled(validFile);
		GuiUtils.enableComponents(filteredImgOverlayCheckbox.getParent(), validFile);
		final String tooltext = "<HTML>Path to a matched image (32-bit preferred). Current file:<br>" + path + " ("
				+ ((validFile) ? "valid" : "invalid") + " path)";
		filteredImgPathField.setToolTipText(tooltext);
	}

	@SuppressWarnings("deprecation")
	private JMenuBar createMenuBar() {
		final JMenuBar menuBar = new JMenuBar();
		final JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);
		final JMenu importSubmenu = new JMenu("Import");
		importSubmenu.setIcon(IconFactory.getMenuIcon(GLYPH.IMPORT));
		final JMenu exportSubmenu = new JMenu("Export (All Paths)");
		exportSubmenu.setIcon(IconFactory.getMenuIcon(GLYPH.EXPORT));
		final JMenu analysisMenu = new JMenu("Utilities");
		menuBar.add(analysisMenu);
		final ScriptInstaller installer = new ScriptInstaller(plugin.getContext(), this);
		menuBar.add(installer.getScriptsMenu());
		final JMenu viewMenu = new JMenu("View");
		menuBar.add(viewMenu);
		menuBar.add(helpMenu());

		loadTracesMenuItem = new JMenuItem("Load Traces...");
		loadTracesMenuItem.setIcon(IconFactory.getMenuIcon(GLYPH.IMPORT));
		loadTracesMenuItem.addActionListener(listener);
		fileMenu.add(loadTracesMenuItem);

		saveMenuItem = new JMenuItem("Save Traces...");
		saveMenuItem.setIcon(IconFactory.getMenuIcon(GLYPH.SAVE));
		saveMenuItem.addActionListener(listener);
		fileMenu.add(saveMenuItem);
		final JMenuItem saveTable = new JMenuItem("Save Measurements...", IconFactory.getMenuIcon(GLYPH.TABLE));
		saveTable.addActionListener(e -> {
			pmUI.saveTable();
			return;
		});
		fileMenu.add(saveTable);

		// Options to replace image data
		final JMenu changeImpMenu = new JMenu("Choose Tracing Image");
		changeImpMenu.setIcon(IconFactory.getMenuIcon(GLYPH.FILE_IMAGE));
		final JMenuItem fromList = new JMenuItem("From Open Image...");
		fromList.addActionListener(e -> {
			(new CmdRunner(ChooseDatasetCmd.class, null, LOADING)).execute();
		});
		changeImpMenu.add(fromList);
		final JMenuItem fromFile = new JMenuItem("From File...");
		fromFile.addActionListener(e -> {
			(new CmdRunner(OpenDatasetCmd.class, null, LOADING)).execute();
		});
		changeImpMenu.add(fromFile);
		fileMenu.addSeparator();
		fileMenu.add(changeImpMenu);

		sendToTrakEM2 = new JMenuItem("Send to TrakEM2");
		sendToTrakEM2.addActionListener(e -> plugin.notifyListeners(new SNTEvent(SNTEvent.SEND_TO_TRAKEM2)));
		fileMenu.addSeparator();
		fileMenu.add(sendToTrakEM2);
		fileMenu.addSeparator();
		fileMenu.add(importSubmenu);

		loadSWCMenuItem = new JMenuItem("(e)SWC...");
		loadSWCMenuItem.addActionListener(listener);
		importSubmenu.add(loadSWCMenuItem);
		final JMenuItem importJSON = new JMenuItem("JSON...");
		importSubmenu.add(importJSON);
		importJSON.addActionListener(e -> {
			final HashMap<String, Object> inputs = new HashMap<>();
			inputs.put("rebuildCanvas", true);
			(new CmdRunner(JSONImporterCmd.class, inputs, LOADING)).execute();
		});
		final JMenuItem importDirectory = new JMenuItem("Directory of SWCs...");
		importDirectory.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.FOLDER));
		importSubmenu.add(importDirectory);
		importDirectory.addActionListener(e -> {
			final HashMap<String, Object> inputs = new HashMap<>();
			inputs.put("rebuildCanvas", true);
			(new CmdRunner(MultiSWCImporterCmd.class, inputs, LOADING)).execute();
		});
		importSubmenu.addSeparator();
		loadLabelsMenuItem = new JMenuItem("Labels (AmiraMesh)...");
		loadLabelsMenuItem.setIcon(IconFactory.getMenuIcon(GLYPH.TAG));
		loadLabelsMenuItem.addActionListener(listener);
		importSubmenu.add(loadLabelsMenuItem);
		importSubmenu.addSeparator();
		final JMenu remoteSubmenu = new JMenu("Remote Databases");
		remoteSubmenu.setIcon(IconFactory.getMenuIcon(GLYPH.DATABASE));
		final JMenuItem importFlyCircuit = new JMenuItem("FlyCircuit...");
		remoteSubmenu.add(importFlyCircuit);
		importFlyCircuit.addActionListener(e -> {
			final HashMap<String, Object> inputs = new HashMap<>();
			inputs.put("loader", new FlyCircuitLoader());
			inputs.put("rebuildCanvas", true);
			(new CmdRunner(RemoteSWCImporterCmd.class, inputs, LOADING)).execute();
		});
		final JMenuItem importMouselight = new JMenuItem("MouseLight...");
		remoteSubmenu.add(importMouselight);
		importMouselight.addActionListener(e -> {
			final HashMap<String, Object> inputs = new HashMap<>();
			inputs.put("rebuildCanvas", true);
			(new CmdRunner(MLImporterCmd.class, inputs, LOADING)).execute();
		});
		final JMenuItem importNeuroMorpho = new JMenuItem("NeuroMorpho...");
		remoteSubmenu.add(importNeuroMorpho);
		importNeuroMorpho.addActionListener(e -> {
			final HashMap<String, Object> inputs = new HashMap<>();
			inputs.put("loader", new NeuroMorphoLoader());
			inputs.put("rebuildCanvas", true);
			(new CmdRunner(RemoteSWCImporterCmd.class, inputs, LOADING)).execute();
		});
		importSubmenu.add(remoteSubmenu);

		exportAllSWCMenuItem = new JMenuItem("SWC...");
		exportAllSWCMenuItem.addActionListener(listener);
		exportSubmenu.add(exportAllSWCMenuItem);
		exportCSVMenuItem = new JMenuItem("CSV Properties...");
		exportCSVMenuItem.addActionListener(listener);
		exportSubmenu.add(exportCSVMenuItem);
		fileMenu.add(exportSubmenu);

		fileMenu.addSeparator();
		quitMenuItem = new JMenuItem("Quit");
		quitMenuItem.addActionListener(listener);
		fileMenu.add(quitMenuItem);

		measureMenuItem = new JMenuItem("Quick Measurements", IconFactory.getMenuIcon(GLYPH.ROCKET));
		measureMenuItem.addActionListener(listener);
		strahlerMenuItem = new JMenuItem("Strahler Analysis", IconFactory.getMenuIcon(GLYPH.BRANCH_CODE));
		strahlerMenuItem.addActionListener(listener);
		plotMenuItem = new JMenuItem("Reconstruction Plotter...", IconFactory.getMenuIcon(GLYPH.DRAFT));
		plotMenuItem.addActionListener(listener);

		analysisMenu.add(measureMenuItem);
		analysisMenu.add(shollAnalysisHelpMenuItem());
		analysisMenu.add(strahlerMenuItem);

		analysisMenu.addSeparator();
		analysisMenu.add(plotMenuItem);
		final JMenuItem compareFiles = new JMenuItem("Compare Reconstructions...");
		compareFiles.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.BINOCULARS));
		analysisMenu.add(compareFiles);
		compareFiles.addActionListener(e -> {
			(new CmdRunner(CompareFilesCmd.class)).execute();
		});
		analysisMenu.addSeparator();

		final JMenu scriptUtilsMenu = installer.getUtilScriptsMenu();
		scriptUtilsMenu.setText("Utility Scripts");
		scriptUtilsMenu.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.PLUS));
		analysisMenu.add(scriptUtilsMenu);

		// View menu
		final JMenuItem arrangeWindowsMenuItem = new JMenuItem("Arrange Views");
		arrangeWindowsMenuItem.setIcon(IconFactory.getMenuIcon(GLYPH.WINDOWS));
		arrangeWindowsMenuItem.addActionListener(e -> arrangeCanvases(true));
		viewMenu.add(arrangeWindowsMenuItem);
		final JMenu hideViewsMenu = new JMenu("Hide Tracing Canvas");
		hideViewsMenu.setIcon(IconFactory.getMenuIcon(GLYPH.EYE_SLASH));
		final JCheckBoxMenuItem xyCanvasMenuItem = new JCheckBoxMenuItem("Hide XY View");
		xyCanvasMenuItem.addActionListener(e -> toggleWindowVisibility(MultiDThreePanes.XY_PLANE, xyCanvasMenuItem));
		hideViewsMenu.add(xyCanvasMenuItem);
		final JCheckBoxMenuItem zyCanvasMenuItem = new JCheckBoxMenuItem("Hide ZY View");
		zyCanvasMenuItem.addActionListener(e -> toggleWindowVisibility(MultiDThreePanes.ZY_PLANE, zyCanvasMenuItem));
		hideViewsMenu.add(zyCanvasMenuItem);
		final JCheckBoxMenuItem xzCanvasMenuItem = new JCheckBoxMenuItem("Hide XZ View");
		xzCanvasMenuItem.addActionListener(e -> toggleWindowVisibility(MultiDThreePanes.XZ_PLANE, xzCanvasMenuItem));
		hideViewsMenu.add(xzCanvasMenuItem);
		final JCheckBoxMenuItem threeDViewerMenuItem = new JCheckBoxMenuItem("Hide Legacy 3D View");
		threeDViewerMenuItem.addItemListener(e -> {
			if (plugin.get3DUniverse() == null || !plugin.use3DViewer) {
				guiUtils.error("Legacy 3D Viewer is not active.");
				return;
			}
			plugin.get3DUniverse().getWindow().setVisible(e.getStateChange() == ItemEvent.DESELECTED);
		});
		hideViewsMenu.add(threeDViewerMenuItem);
		viewMenu.add(hideViewsMenu);
		viewMenu.addSeparator();

		final JMenuItem filteredImpMenu = new JMenuItem("<HTML>Show Loaded <i>Filtered Image</i>");
		filteredImpMenu.addActionListener(e -> {
			final ImagePlus imp = plugin.getFilteredDataAsImp();
			if (imp == null) {
				guiUtils.error("No \"Filtered Image\" has been loaded.");
			} else {
				imp.resetDisplayRange();
				imp.show();
			}
		});
		viewMenu.add(filteredImpMenu);
		final JMenuItem tubenessImpMenu = new JMenuItem("<HTML>Show Cached/Loaded <i>Hessian (Tubeness) Image</i>");
		tubenessImpMenu.addActionListener(e -> {
			final ImagePlus imp = plugin.getCachedTubenessDataAsImp();
			if (imp == null) {
				guiUtils.error("No \"Tubeness\" image has been loaded/computed.");
			} else {
				final double max = 256 / plugin.hessianMultiplier;
				imp.setDisplayRange(0, max);
				imp.show();
			}
		});
		viewMenu.add(tubenessImpMenu);
		return menuBar;
	}

	private JPanel renderingPanel() {

		showPathsSelected = new JCheckBox(hotKeyLabel("1. Only selected paths (hide deselected)", "1"),
				plugin.showOnlySelectedPaths);
		showPathsSelected.addItemListener(listener);

		final JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row1.add(showPathsSelected);

		showPartsNearby = new JCheckBox(hotKeyLabel("2. Only nodes within ", "2"));
		showPartsNearby.setEnabled(isStackAvailable());
		showPartsNearby.addItemListener(listener);
		final JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row2.add(showPartsNearby);
		nearbyFieldSpinner = GuiUtils.integerSpinner(plugin.depth == 1 ? 1 : 2, 1, plugin.depth, 1);
		nearbyFieldSpinner.setEnabled(isStackAvailable());
		nearbyFieldSpinner.addChangeListener(e -> {
			showPartsNearby.setSelected(true);
			plugin.justDisplayNearSlices(true, (int) nearbyFieldSpinner.getValue());
		});

		row2.add(nearbyFieldSpinner);
		row2.add(GuiUtils.leftAlignedLabel(" nearby Z-slices", isStackAvailable()));

		final JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		onlyActiveCTposition = new JCheckBox(hotKeyLabel("3. Only paths from active channel/frame", "3"));
		row3.add(onlyActiveCTposition);
		onlyActiveCTposition.addItemListener(listener);

		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(row1);
		panel.add(row2);
		panel.add(row3);
		return panel;
	}

	private JPanel colorOptionsPanel() {
		final JPanel colorOptionsPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints cop_f = GuiUtils.defaultGbc();
		final JPanel colorButtonPanel = new JPanel(new GridLayout(1, 2));
		final ColorChooserButton colorChooser1 = new ColorChooserButton(plugin.selectedColor, "Selected");
		colorChooser1.setName("Color for Selected Paths");
		colorChooser1.addColorChangedListener(newColor -> plugin.setSelectedColor(newColor));
		final ColorChooserButton colorChooser2 = new ColorChooserButton(plugin.deselectedColor, "Deselected");
		colorChooser2.setName("Color for Deselected Paths");
		colorChooser2.addColorChangedListener(newColor -> plugin.setDeselectedColor(newColor));
		colorButtonPanel.add(colorChooser1);
		colorButtonPanel.add(colorChooser2);
		++cop_f.gridy;
		colorOptionsPanel.add(colorButtonPanel, cop_f);
		++cop_f.gridy;
		final JCheckBox jcheckbox = new JCheckBox("Enforce default colors (ignore color tags)");
		guiUtils.addTooltip(jcheckbox,
				"Whether default colors above should be used even when color tags have been applied in the Path Manager");
		jcheckbox.addActionListener(e -> {
			plugin.displayCustomPathColors = !jcheckbox.isSelected();
			// colorChooser1.setEnabled(!plugin.displayCustomPathColors);
			// colorChooser2.setEnabled(!plugin.displayCustomPathColors);
			plugin.updateAllViewers();
		});
		colorOptionsPanel.add(jcheckbox, cop_f);

		return colorOptionsPanel;
	}

	private JPanel snappingPanel() {

		final JPanel tracingOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		useSnapWindow = new JCheckBox(hotKeyLabel("Enable Snapping within: XY", "S"), plugin.snapCursor);
		guiUtils.addTooltip(useSnapWindow, "Whether the mouse pointer should snap to the brightest voxel "
				+ "searched within the specified neighborhood (in pixels). If Z=0 snapping occurs in 2D.");
		useSnapWindow.addItemListener(listener);
		tracingOptionsPanel.add(useSnapWindow);

		snapWindowXYsizeSpinner = GuiUtils.integerSpinner(plugin.cursorSnapWindowXY * 2,
				SimpleNeuriteTracer.MIN_SNAP_CURSOR_WINDOW_XY, SimpleNeuriteTracer.MAX_SNAP_CURSOR_WINDOW_XY * 2, 2);
		snapWindowXYsizeSpinner
				.addChangeListener(e -> plugin.cursorSnapWindowXY = (int) snapWindowXYsizeSpinner.getValue() / 2);
		tracingOptionsPanel.add(snapWindowXYsizeSpinner);

		final JLabel z_spinner_label = GuiUtils.leftAlignedLabel("  Z ", true);
		z_spinner_label.setBorder(new EmptyBorder(0, 2, 0, 0));
		tracingOptionsPanel.add(z_spinner_label);
		snapWindowZsizeSpinner = GuiUtils.integerSpinner(plugin.cursorSnapWindowZ * 2,
				SimpleNeuriteTracer.MIN_SNAP_CURSOR_WINDOW_Z, SimpleNeuriteTracer.MAX_SNAP_CURSOR_WINDOW_Z * 2, 2);
		snapWindowZsizeSpinner.setEnabled(isStackAvailable());
		snapWindowZsizeSpinner
				.addChangeListener(e -> plugin.cursorSnapWindowZ = (int) snapWindowZsizeSpinner.getValue() / 2);
		tracingOptionsPanel.add(snapWindowZsizeSpinner);
		// ensure same alignment of all other panels using defaultGbc
		final JPanel container = new JPanel(new GridBagLayout());
		container.add(tracingOptionsPanel, GuiUtils.defaultGbc());
		return container;
	}

	private JPanel aStarPanel() {
		aStarCheckBox = new JCheckBox("Enable ", plugin.isAstarEnabled());
		aStarCheckBox.addActionListener(e -> {
			boolean enable = aStarCheckBox.isSelected();
			if (!enable && askUserConfirmation
					&& !guiUtils.getConfirmation(
							"Disable computation of paths? All segmentation tasks will be disabled.",
							"Enable Manual Tracing?")) {
				aStarCheckBox.setSelected(true);
				return;
			}
			if (enable && !plugin.accessToValidImageData()) {
				aStarCheckBox.setSelected(false);
				noValidImageDataError();
				enable = false;
			} else if (enable && !plugin.inputImageLoaded()) {
				loadImagefromGUI(plugin.channel, plugin.frame);
			}
			plugin.enableAstar(enable);
		});

		searchAlgoChoice = new JComboBox<String>();
		searchAlgoChoice.addItem("A* search");
		searchAlgoChoice.addItem("Fast marching");
		searchAlgoChoice.addActionListener(event -> {
			// if user did not trigger the event ignore it
			if (!searchAlgoChoice.hasFocus())
				return;
			@SuppressWarnings("unchecked")
			final int idx = (int) ((JComboBox<String>) event.getSource()).getSelectedIndex();
			setFastMarchSearchEnabled(idx == 1);
		});

		final JPanel aStarPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gc = GuiUtils.defaultGbc();
		addSeparatorWithURL(aStarPanel, "Auto-tracing:", true, gc);
		++gc.gridy;

		final JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		checkboxPanel.add(aStarCheckBox);
		checkboxPanel.add(searchAlgoChoice);
		checkboxPanel.add(GuiUtils.leftAlignedLabel(" algorithm", true));

		aStarPanel.add(checkboxPanel, gc);
		return aStarPanel;
	}

	private JPanel hessianPanel() {
		hessianPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		preprocess = new JCheckBox("Cache All Hessian Computations");
		computeTubenessJMI = new JMenuItem("Cache All Hessian Computations");
		loadTubenessJMI = new JMenuItem("Load Precomputed \"Tubeness\" Image...");
		flushTubenessJMI = new JMenuItem("Flush Existing Cached Data");

		setSigma(plugin.getDefaultHessianSigma(), false);
		updateHessianPanel();
		preprocess.addActionListener(listener);
		final JPopupMenu optionsMenu = new JPopupMenu();
		final JButton optionsButton = optionsButton(optionsMenu, filteredImgBrowseButton);
		hessianPanel.add(optionsButton);
		final JMenuItem jmiVisual = new JMenuItem(GuiListener.EDIT_SIGMA_VISUALLY);
		jmiVisual.addActionListener(listener);
		optionsMenu.add(jmiVisual);
		JMenuItem jmiManual = new JMenuItem(GuiListener.EDIT_SIGMA_MANUALLY);
		jmiManual.addActionListener(listener);
		optionsMenu.add(jmiManual);
		optionsMenu.addSeparator();
		computeTubenessJMI.addActionListener(e -> {
			loadCachedDataImage(true, true, null);
		});
		optionsMenu.add(computeTubenessJMI);
		loadTubenessJMI.addActionListener(e -> {
			loadCachedDataImage(true, true, openFile("Choose \"Tubeness\" Image...", ".tubes.tif"));
		});
		optionsMenu.add(loadTubenessJMI);
		flushTubenessJMI.addActionListener(e -> {
			flushCachedTubeness();
		});
		optionsMenu.add(flushTubenessJMI);

		hessianPanel = new JPanel();
		hessianPanel.setLayout(new GridBagLayout());
		final GridBagConstraints c = GuiUtils.defaultGbc();

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridwidth = 2;
		hessianPanel.add(preprocess, c);
		c.gridwidth = 3;
		c.fill = GridBagConstraints.NONE;

		c.gridx = 2;
		hessianPanel.add(optionsButton, c);
		c.gridy++;
		return hessianPanel;
	}

	private JPanel hideWindowsPanel() {
		showOrHidePathList = new JButton("Show Path Manager");
		showOrHidePathList.addActionListener(listener);
		showOrHideFillList = new JButton("Show Fill Manager");
		showOrHideFillList.addActionListener(listener);
		final JPanel hideWindowsPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = new GridBagConstraints();
		gdb.fill = GridBagConstraints.HORIZONTAL;
		gdb.weightx = 0.5;
		hideWindowsPanel.add(showOrHidePathList, gdb);
		gdb.gridx = 1;
		hideWindowsPanel.add(showOrHideFillList, gdb);
		return hideWindowsPanel;
	}

	private JPanel statusBar() {
		final JPanel statusBar = new JPanel();
		statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
		statusBarText = GuiUtils.leftAlignedLabel("Ready to trace...", true);
		statusBarText.setBorder(BorderFactory.createEmptyBorder(0, MARGIN, MARGIN / 2, 0));
		statusBar.add(statusBarText);
		refreshStatus();
		return statusBar;
	}

	private void setFastMarchSearchEnabled(final boolean enable) {
		final boolean enbl = enable && isFastMarchSearchAvailable();
		plugin.tubularGeodesicsTracingEnabled = enbl;
		if (!enbl) {
			searchAlgoChoice.setSelectedIndex(0);
			if (plugin.tubularGeodesicsThread != null) {
				plugin.tubularGeodesicsThread.requestStop();
				plugin.tubularGeodesicsThread = null;
			}
		}
		refreshHessianPanelState();
	}

	private boolean isFastMarchSearchAvailable() {
		final boolean tgInstalled = Types.load("FijiITKInterface.TubularGeodesics") != null;
		final boolean tgAvailable = plugin.tubularGeodesicsTracingEnabled;
		if (!tgInstalled || !tgInstalled) {
			final StringBuilder msg = new StringBuilder(
					"Fast marching requires the <i>TubularGeodesics</i> plugin to be installed ")
							.append("and an <i>oof.tif</i> filtered image to be loaded. Currently, ");
			if (!tgInstalled && !tgAvailable) {
				msg.append("neither conditions are fullfilled.");
			} else if (!tgInstalled) {
				msg.append("the plugin is not installed.");
			} else {
				msg.append("the filtered image does not seem to be valid.");
			}
			guiUtils.error(msg.toString(), "Error", "https://imagej.net/SNT:_Tubular_Geodesics");
		}
		return tgInstalled && tgAvailable;
	}

	private void refreshStatus() {
		showStatus(null, false);
	}

	/**
	 * Updates the status bar.
	 *
	 * @param msg       the text to displayed. Set it to null (or empty String) to
	 *                  reset the status bar.
	 * @param temporary if true and {@code msg} is valid, text is displayed
	 *                  transiently for a couple of seconds
	 */
	public void showStatus(final String msg, final boolean temporary) {
		SwingUtilities.invokeLater(() -> {
			final boolean validMsg = !(msg == null || msg.isEmpty());
			if (validMsg && !temporary) {
				statusBarText.setText(msg);
				return;
			}

			final String defaultText;
			if (!plugin.accessToValidImageData()) {
				defaultText = "Image data unavailable...";
			} else {
				defaultText = "Tracing " + plugin.getImagePlus().getShortTitle() + ", C=" + plugin.channel + ", T="
						+ plugin.frame;
			}

			if (!validMsg) {
				statusBarText.setText(defaultText);
				return;
			}

			final Timer timer = new Timer(3000, e -> statusBarText.setText(defaultText));
			timer.setRepeats(false);
			timer.start();
			statusBarText.setText(msg);
		});
	}

	private JPanel getTab() {
		final JPanel tab = new JPanel();
		tab.setBorder(BorderFactory.createEmptyBorder(MARGIN * 2, MARGIN / 2, MARGIN / 2, MARGIN));
		tab.setLayout(new GridBagLayout());
		return tab;
	}

	protected void displayOnStarting() {
		SwingUtilities.invokeLater(() -> {
			if (plugin.prefs.isSaveWinLocations())
				arrangeDialogs();
			arrangeCanvases(false);
			resetState();
			pack();
			setVisible(true);
			{
				// Adjust fields that resize the dialog unless it is visible
				final String path = (plugin.filteredFileImage == null) ? ""
						: plugin.filteredFileImage.getAbsolutePath();
				filteredImgPathField.setText(path);
			}
			pathAndFillManager.resetListeners(null, true); // update Path lists
			setPathListVisible(true, false);
			setFillListVisible(false);
			final StackWindow impWindow = plugin.getWindow(MultiDThreePanes.XY_PLANE);
			if (impWindow != null)
				impWindow.toFront();
		});
	}

	private void setMultiplierForCachedTubenessFromUser() {
		final double defaultValue = 256 / SNTPrefs.DEFAULT_MULTIPLIER;
		String promptMsg = "<HTML><body><div style='width:500;'>" //
				+ "Enter the maximum pixel intensity on the cached "//
				+ "<i>Tubeness</i> image beyond which the cost function for A* search "//
				+ "is minimized. SNT's default is "//
				+ SNT.formatDouble(256 / SNTPrefs.DEFAULT_MULTIPLIER, 1) + ".";
		if (plugin.filteredData == null) {
			// min/max belong to plugin.cachedTubeness
			promptMsg += " The image min-max range is "//
					+ SNT.formatDouble(plugin.stackMinFiltered, 1) + "-"//
					+ SNT.formatDouble(plugin.stackMaxFiltered, 1) + ".";
		}
		final Double max = guiUtils.getDouble(promptMsg, "Hessian Settings (Cached Image)", defaultValue);
		if (max == null) {
			return; // user pressed cancel
		}
		if (Double.isNaN(max) || max < 0) {
			guiUtils.error("Maximum must be a positive number.", "Invalid Input");
			return;
		}
		setMultiplier(256 / max);
	}

	private boolean okToFlushCachedTubeness() {
		if (plugin.cachedTubeness == null)
			return true;
		final boolean ok = plugin.cachedTubeness != null && guiUtils.getConfirmation(
				"Hessian computations for the entire image currently exist. Discard such data?",
				"Discard Existing Computations?", "Yes. Discard Computations", "Cancel");
		if (ok)
			flushCachedTubeness();
		return ok;
	}

	private void setSigmaFromUser() {
		final JTextField sigmaField = new JTextField(SNT.formatDouble(getSigma(), 5), 5);
		final JTextField maxField = new JTextField(SNT.formatDouble(256 / plugin.hessianMultiplier, 1), 5);
		final Object[] contents = { "<html><b>Sigma</b><br>Enter the approximate radius of the structures you are<br>" //
				+ "tracing. The default is the average of voxel dimensions<br>" //
				+ "(anisotropic images) or twice the voxel size (isotropic),<br>" //
				+ "i.e., " + SNT.formatDouble(plugin.getDefaultHessianSigma(), 3) //
				+ plugin.spacing_units + " for active image:", sigmaField, //
				"<html><br><b>Maximum</b><br>Enter the maximum pixel intensity on the <i>Tubeness</i><br>"
						+ "image beyond which the cost function for A* search<br>" + "is minimized (the default is 256/"
						+ SNTPrefs.DEFAULT_MULTIPLIER + "=" + SNT.formatDouble(256 / SNTPrefs.DEFAULT_MULTIPLIER, 1)
						+ "):",
				maxField, };
		final int result = JOptionPane.showConfirmDialog(this, contents, "Hessian (\"Tubeness\") Settings",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION) {
			final double sigma = GuiUtils.extractDouble(sigmaField);
			final double max = GuiUtils.extractDouble(maxField);
			if (Double.isNaN(sigma) || sigma <= 0 || Double.isNaN(max) || max <= 0) {
				guiUtils.error("Sigma and max must be positive numbers.", "Invalid Input");
				return;
			}
			preprocess.setSelected(false); // should never be on when setSigma is
			// called
			setSigma(sigma, true);
			plugin.hessianSigma = sigma;
			setMultiplier(256 / max);

		}
	}

	private void arrangeDialogs() {
		Point loc = plugin.prefs.getPathWindowLocation();
		if (loc != null)
			pmUI.setLocation(loc);
		loc = plugin.prefs.getFillWindowLocation();
		if (loc != null)
			fmUI.setLocation(loc);
		// final GraphicsDevice activeScreen =
		// getGraphicsConfiguration().getDevice();
		// final int screenWidth = activeScreen.getDisplayMode().getWidth();
		// final int screenHeight = activeScreen.getDisplayMode().getHeight();
		// final Rectangle bounds =
		// activeScreen.getDefaultConfiguration().getBounds();
		//
		// setLocation(bounds.x, bounds.y);
		// pw.setLocation(screenWidth - pw.getWidth(), bounds.y);
		// fw.setLocation(bounds.x + getWidth(), screenHeight - fw.getHeight());
	}

	private void arrangeCanvases(final boolean displayErrorOnFailure) {

		final StackWindow xy_window = plugin.getWindow(MultiDThreePanes.XY_PLANE);
		if (xy_window == null) {
			if (displayErrorOnFailure)
				guiUtils.error("XY view is not available");
			return;
		}
		final GraphicsConfiguration xy_config = xy_window.getGraphicsConfiguration();
		final GraphicsDevice xy_screen = xy_config.getDevice();
		final Rectangle xy_screen_bounds = xy_screen.getDefaultConfiguration().getBounds();

		// Center the main tracing canvas on the screen it was found
		final int x = (xy_screen_bounds.width / 2) - (xy_window.getWidth() / 2) + xy_screen_bounds.x;
		final int y = (xy_screen_bounds.height / 2) - (xy_window.getHeight() / 2) + xy_screen_bounds.y;
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

	private void toggleWindowVisibility(final int pane, final JCheckBoxMenuItem mItem) {
		if (plugin.getImagePlus(pane) == null) {
			String msg;
			if (pane == MultiDThreePanes.XY_PLANE) {
				msg = "XY view is not available.";
			} else if (plugin.getSinglePane()) {
				msg = "View does not exist. To generate ZY/XZ " + "views run \"Display ZY/XZ views\".";
			} else {
				msg = "View is no longer accessible. " + "You can (re)build it using \"Rebuild ZY/XZ views\".";
			}
			guiUtils.error(msg);
			mItem.setSelected(false);
			return;
		}
		// NB: WindowManager list won't be notified
		plugin.getWindow(pane).setVisible(!mItem.isSelected());
	}

	private boolean noPathsError() {
		final boolean noPaths = pathAndFillManager.size() == 0;
		if (noPaths)
			guiUtils.error("There are no traced paths.");
		return noPaths;
	}

	private void setPathListVisible(final boolean makeVisible, final boolean toFront) {
		assert SwingUtilities.isEventDispatchThread();
		if (makeVisible) {
			pmUI.setVisible(true);
			if (toFront)
				pmUI.toFront();
			if (showOrHidePathList != null)
				showOrHidePathList.setText("  Hide Path Manager");
		} else {
			if (showOrHidePathList != null)
				showOrHidePathList.setText("Show Path Manager");
			pmUI.setVisible(false);
		}
	}

	private void togglePathListVisibility() {
		assert SwingUtilities.isEventDispatchThread();
		synchronized (pmUI) {
			setPathListVisible(!pmUI.isVisible(), true);
		}
	}

	protected void setFillListVisible(final boolean makeVisible) {
		assert SwingUtilities.isEventDispatchThread();
		if (makeVisible) {
			fmUI.setVisible(true);
			if (showOrHideFillList != null)
				showOrHideFillList.setText("  Hide Fill Manager");
			fmUI.toFront();
		} else {
			if (showOrHideFillList != null)
				showOrHideFillList.setText("Show Fill Manager");
			fmUI.setVisible(false);
		}
	}

	protected void toggleFillListVisibility() {
		assert SwingUtilities.isEventDispatchThread();
		synchronized (fmUI) {
			setFillListVisible(!fmUI.isVisible());
		}
	}

	protected void thresholdChanged(final double f) {
		fmUI.thresholdChanged(f);
	}

	protected boolean nearbySlices() {
		assert SwingUtilities.isEventDispatchThread();
		return showPartsNearby.isSelected();
	}

	private JMenu helpMenu() {
		final JMenu helpMenu = new JMenu("Help");
		final String URL = "https://imagej.net/SNT";
		JMenuItem mi = menuItemTriggeringURL("Main documentation page", URL);
		mi.setIcon(IconFactory.getMenuIcon(GLYPH.HOME));
		helpMenu.add(mi);
		helpMenu.addSeparator();

		mi = menuItemTriggeringURL("Overview", URL + ":_Overview");
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Tutorials", URL + "#Tutorials");
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Step-by-step Instructions", URL + ":_Step-By-Step_Instructions");
		mi.setIcon(IconFactory.getMenuIcon(GLYPH.FOOTPRINTS));
		helpMenu.add(mi);
		helpMenu.addSeparator();

		mi = menuItemTriggeringURL("List of shortcuts", URL + ":_Key_Shortcuts");
		mi.setIcon(IconFactory.getMenuIcon(GLYPH.KEYBOARD));
		helpMenu.add(mi);
		helpMenu.addSeparator();

		mi = menuItemTriggeringURL("FAQs", URL + ":_FAQ");
		mi.setIcon(IconFactory.getMenuIcon(GLYPH.QUESTION));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Ask a question", "https://forum.image.sc/tags/snt");
		mi.setIcon(IconFactory.getMenuIcon(GLYPH.COMMENTS));
		helpMenu.add(mi);
		helpMenu.addSeparator();

		mi = menuItemTriggeringURL("Python Notebooks", URL + ":_Python_Notebooks");
		helpMenu.add(mi);
		helpMenu.addSeparator();

		mi = menuItemTriggeringURL("Citing SNT", URL + ":_FAQ#citing");
		helpMenu.add(mi);
		return helpMenu;
	}

	private JMenuItem shollAnalysisHelpMenuItem() {
		final JMenuItem mi = new JMenuItem("Sholl Analysis...");
		mi.setIcon(IconFactory.getMenuIcon(GLYPH.BULLSEYE));
		mi.addActionListener(e -> {
			final Thread newThread = new Thread(() -> {
				if (noPathsError())
					return;
				final String modKey = "Alt+Shift";
				final String url1 = ShollUtils.URL + "#Analysis_of_Traced_Cells";
				final String url2 = "https://imagej.net/Simple_Neurite_Tracer:_Sholl_analysis";
				final StringBuilder sb = new StringBuilder();
				sb.append("<html>");
				sb.append("<div WIDTH=500>");
				sb.append("To initiate <a href='").append(ShollUtils.URL).append("'>Sholl Analysis</a>, ");
				sb.append("you must select a focal point. You can do it coarsely by ");
				sb.append("righ-clicking near a node and choosing <i>Sholl Analysis at Nearest ");
				sb.append("Node</i> from the contextual menu (Shortcut: \"").append(modKey).append("+A\").");
				sb.append("<p>Alternatively, for precise positioning of the center of analysis:</p>");
				sb.append("<ol>");
				sb.append("<li>Mouse over the path of interest. Press \"G\" to activate it</li>");
				sb.append("<li>Press \"").append(modKey).append("\" to select a node along the path</li>");
				sb.append("<li>Press \"").append(modKey).append("+A\" to start analysis</li>");
				sb.append("</ol>");
				sb.append("A walkthrough of this procedure is <a href='").append(url2)
						.append("'>available online</a>. ");
				sb.append("For batch processing, run <a href='").append(url1)
						.append("'>Analyze>Sholl>Sholl Analysis (From Tracings)...</a>. ");
				new HTMLDialog("Sholl Analysis How-to", sb.toString(), false);
			});
			newThread.start();
		});
		return mi;
	}

	private JMenuItem menuItemTriggeringURL(final String label, final String URL) {
		final JMenuItem mi = new JMenuItem(label);
		mi.addActionListener(e -> IJ.runPlugIn("ij.plugin.BrowserLauncher", URL));
		return mi;
	}

	/**
	 * Gets the Path Manager dialog.
	 *
	 * @return the {@link PathManagerUI} associated with this UI
	 */
	public PathManagerUI getPathManager() {
		return pmUI;
	}

	/**
	 * Gets the Fill Manager dialog.
	 *
	 * @return the {@link FillManagerUI} associated with this UI
	 */
	public FillManagerUI getFillManager() {
		return fmUI;
	}

	/**
	 * Gets the Reconstruction Viewer.
	 *
	 * @param initializeIfNull it true, initializes the Viewer if it has not yet
	 *                         been initialized
	 * @return the reconstruction viewer
	 */
	public Viewer3D getReconstructionViewer(final boolean initializeIfNull) {
		if (initializeIfNull && recViewer == null) {
			recViewer = new Viewer3D(plugin.getContext());
			recViewer.show();
			setReconstructionViewer(recViewer);
		}
		return recViewer;
	}

	public void setReconstructionViewer(final Viewer3D recViewer) {
		this.recViewer = recViewer;
		openRecViewer.setEnabled(recViewer == null);
	}

	protected void reset() {
		abortCurrentOperation();
		resetState();
		showStatus("Resetting", true);
	}

	protected void inputImageChanged() {
		final ImagePlus imp = plugin.getImagePlus();
		showPartsNearby.setEnabled(imp != null && !plugin.is2D());
		nearbyFieldSpinner.setEnabled(imp != null && !plugin.is2D());
		final JPanel newSourcePanel = sourcePanel(imp);
		final GridBagLayout layout = (GridBagLayout) newSourcePanel.getLayout();
		for (int i = 0; i < sourcePanel.getComponentCount(); i++) {
			sourcePanel.remove(i);
			final Component component = newSourcePanel.getComponent(i);
			sourcePanel.add(component, layout.getConstraints(component));
		}
		revalidate();
		repaint();
		final boolean validImage = plugin.accessToValidImageData();
		plugin.enableAstar(validImage);
		plugin.enableSnapCursor(validImage);
		resetState();
		arrangeCanvases(false);
	}

	protected void abortCurrentOperation() {// FIXME: MOVE TO SimpleNeuriteTracer?
		switch (currentState) {
		case (SEARCHING):
			updateStatusText("Cancelling path search...", true);
			plugin.cancelSearch(false);
			break;
		case (CRUNCHING_DATA):
			updateStatusText("Unloading cached data", true);
			if (activeWorker != null)
				activeWorker.kill();
			break;
		case (CALCULATING_GAUSSIAN):
			updateStatusText("Cancelling Gaussian generation...", true);
			plugin.cancelGaussian();
			break;
		case (WAITING_FOR_SIGMA_POINT):
			showStatus("Sigma adjustment cancelled...", true);
			listener.restorePreSigmaState();
			break;
		case (PARTIAL_PATH):
			showStatus("Last temporary path cancelled...", true);
			plugin.cancelTemporary();
			if (plugin.currentPath != null)
				plugin.cancelPath();
			break;
		case (QUERY_KEEP):
			showStatus("Last segment cancelled...", true);
			if (plugin.temporaryPath != null)
				plugin.cancelTemporary();
			plugin.cancelPath();
			break;
		case (FILLING_PATHS):
			showStatus("Filling out cancelled...", true);
			plugin.discardFill(); // will change UI state
			return;
		case (FITTING_PATHS):
			showStatus("Fitting cancelled...", true);
			pmUI.cancelFit(true); // will change UI state
			return;
		case (SNT_PAUSED):
			showStatus("SNT is now active...", true);
			if (plugin.getImagePlus() != null)
				plugin.getImagePlus().unlock();
			plugin.pause(false); // will change UI state
			return;
		case (TRACING_PAUSED):
			if (!plugin.accessToValidImageData()) {
				showStatus("All tasks terminated", true);
				return;
			}
			showStatus("Tracing is now active...", true);
			plugin.pauseTracing(false, false); // will change UI state
			return;
		case (EDITING):
			showStatus("Exited from 'Edit Mode'...", true);
			plugin.enableEditMode(false); // will change UI state
			return;
		case (WAITING_FOR_SIGMA_CHOICE):
			showStatus("Close the sigma palette to abort sigma input...", true);
			return; // do nothing: Currently we have no control over the sigma
					// palette window
		case (WAITING_TO_START_PATH):
			// If user is aborting something in this state, something
			// went awry!?. Try to abort all possible lingering tasks
			pmUI.cancelFit(true);
			plugin.cancelSearch(true);
			plugin.cancelGaussian();
			plugin.discardFill();
			if (plugin.currentPath != null)
				plugin.cancelPath();
			if (plugin.temporaryPath != null)
				plugin.cancelTemporary();
			if (activeWorker != null)
				activeWorker.kill();
			showStatus("All tasks terminated", true);
			return;
		default:
			break;
		}
		changeState(WAITING_TO_START_PATH);
	}

	private String getState(final int state) {
		switch (state) {
		case WAITING_TO_START_PATH:
			return "WAITING_TO_START_PATH";
		case PARTIAL_PATH:
			return "PARTIAL_PATH";
		case SEARCHING:
			return "SEARCHING";
		case QUERY_KEEP:
			return "QUERY_KEEP";
		case CRUNCHING_DATA:
			return "CACHING_DATA";
		// case LOGGING_POINTS:
		// return "LOGGING_POINTS";
		// case DISPLAY_EVS:
		// return "DISPLAY_EVS";
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
		case EDITING:
			return "EDITING_MODE";
		case SNT_PAUSED:
			return "PAUSED";
		case TRACING_PAUSED:
			return "ANALYSIS_MODE";
		default:
			return "UNKNOWN";
		}
	}

	protected void togglePathsChoice() {
		assert SwingUtilities.isEventDispatchThread();
		showPathsSelected.setSelected(!showPathsSelected.isSelected());
	}

	protected void enableFilteredImgTracing(final boolean enable) {
		if (plugin.isTracingOnFilteredImageAvailable()) {
			plugin.doSearchOnFilteredData = enable;
			filteredImgActivateCheckbox.setSelected(enable);
		} else if (enable) {
			noFilteredImgAvailableError();
		}
		refreshHessianPanelState();
	}

	private void refreshHessianPanelState() {
		GuiUtils.enableComponents(hessianPanel,
				!plugin.doSearchOnFilteredData && !plugin.tubularGeodesicsTracingEnabled);
	}

	private void noFilteredImgAvailableError() {
		guiUtils.error("Filtered image has not been loaded. Please load it first.", "Filtered Image Unavailable");
		filteredImgOverlayCheckbox.setSelected(false);
		filteredImgActivateCheckbox.setSelected(false);
		plugin.doSearchOnFilteredData = false;
		updateFilteredFileField();
	}

	protected void toggleFilteredImgTracing() {
		assert SwingUtilities.isEventDispatchThread();
		// Do nothing if we are not allowed to enable FilteredImgTracing
		if (!filteredImgActivateCheckbox.isEnabled()) {
			showStatus("Ignored: Filtered imaged not available", true);
			return;
		}
		enableFilteredImgTracing(!filteredImgActivateCheckbox.isSelected());
	}

	protected void toggleHessian() {
		assert SwingUtilities.isEventDispatchThread();
		if (ignorePreprocessEvents || !preprocess.isEnabled())
			return;
		enableHessian(!preprocess.isSelected());
	}

	protected void enableHessian(final boolean enable) {
		if (plugin.cachedTubeness == null) {
			if (enable) {
				preGaussianState = currentState;
			} else {
				changeState(preGaussianState);
			}
		}
		plugin.enableHessian(enable);
		preprocess.setSelected(enable); // will not trigger ActionEvent
		showStatus("Hessian " + ((enable) ? "enabled" : "disabled"), true);
	}

	/** Should only be called by {@link SimpleNeuriteTracer#enableAstar(boolean)} */
	protected void enableAStarGUI(final boolean enable) {
		SwingUtilities.invokeLater(() -> {
			aStarCheckBox.setSelected(enable);
			setEnableAutoTracingComponents(enable, true);
			showStatus("A* " + ((enable) ? "enabled" : "disabled"), true);
		});
	}

	protected void togglePartsChoice() {
		assert SwingUtilities.isEventDispatchThread();
		showPartsNearby.setSelected(!showPartsNearby.isSelected());
	}

	protected void toggleChannelAndFrameChoice() {
		assert SwingUtilities.isEventDispatchThread();
		onlyActiveCTposition.setSelected(!onlyActiveCTposition.isSelected());
	}

	private String hotKeyLabel(final String text, final String key) {
		final String label = text.replaceFirst(key, "<u><b>" + key + "</b></u>");
		return (text.startsWith("<HTML>")) ? label : "<HTML>" + label;
	}

	protected void noValidImageDataError() {
		guiUtils.error("This option requires valid image data to be loaded.");
	}

	private class GuiListener
			implements ActionListener, ItemListener, SigmaPalette.SigmaPaletteListener, ImageListener {

		private final static String EDIT_SIGMA_MANUALLY = "Adjust Settings Manually...";
		private final static String EDIT_SIGMA_VISUALLY = "Adjust Settings Visually...";
		private int preSigmaPaletteState;

		public GuiListener() {
			ImagePlus.addImageListener(this);
		}

		/* ImageListener */
		@Override
		public void imageClosed(final ImagePlus imp) {
			if (imp != plugin.getImagePlus())
				return;
			if (plugin.accessToValidImageData()) {
				plugin.pauseTracing(true, false);
			} else {
				updateRebuildCanvasButton();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see ij.ImageListener#imageOpened(ij.ImagePlus)
		 */
		@Override
		public void imageOpened(final ImagePlus imp) {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see ij.ImageListener#imageUpdated(ij.ImagePlus)
		 */
		@Override
		public void imageUpdated(final ImagePlus imp) {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see tracing.gui.SigmaPalette.SigmaPaletteListener#sigmaPaletteOKed(double,
		 * double)
		 */
		/* SigmaPaletteListener */
		@Override
		public void sigmaPaletteOKed(final double newSigma, final double newMultiplier) {
			SwingUtilities.invokeLater(() -> {
				restorePreSigmaState();
				setMultiplier(newMultiplier);
				setSigma(newSigma, true);
			});
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see tracing.gui.SigmaPalette.SigmaPaletteListener#sigmaPaletteCanceled()
		 */
		@Override
		public void sigmaPaletteCanceled() {
			SwingUtilities.invokeLater(() -> restorePreSigmaState());
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
		 */
		@Override
		public void itemStateChanged(final ItemEvent e) {
			assert SwingUtilities.isEventDispatchThread();

			final Object source = e.getSource();

			if (source == showPartsNearby) {
				plugin.justDisplayNearSlices(showPartsNearby.isSelected(), (int) nearbyFieldSpinner.getValue());
			} else if (source == useSnapWindow) {
				plugin.enableSnapCursor(useSnapWindow.isSelected());
			} else if (source == showPathsSelected) {
				plugin.setShowOnlySelectedPaths(showPathsSelected.isSelected());
			} else if (source == onlyActiveCTposition) {
				plugin.setShowOnlyActiveCTposPaths(onlyActiveCTposition.isSelected(), true);
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		@Override
		public void actionPerformed(final ActionEvent e) {
			assert SwingUtilities.isEventDispatchThread();

			final Object source = e.getSource();

			if (source == preprocess) {
				enableHessian(preprocess.isSelected());
			} else if (source == saveMenuItem && !noPathsError()) {

				final File saveFile = saveFile("Save Traces As...", null, "traces");
				if (saveFile == null)
					return; // user pressed cancel;
				showStatus("Saving traces to " + saveFile.getAbsolutePath(), false);

				final int preSavingState = currentState;
				changeState(SAVING);
				try {
					pathAndFillManager.writeXML(saveFile.getAbsolutePath(), plugin.useCompressedXML);
				} catch (final IOException ioe) {
					showStatus("Saving failed.", true);
					guiUtils.error(
							"Writing traces to '" + saveFile.getAbsolutePath() + "' failed. See Console for details.");
					changeState(preSavingState);
					ioe.printStackTrace();
					return;
				}
				changeState(preSavingState);
				showStatus("Saving completed.", true);

				plugin.unsavedPaths = false;

			} else if (source == loadTracesMenuItem || source == loadSWCMenuItem) {

				if (plugin.pathsUnsaved() && !guiUtils
						.getConfirmation("There are unsaved paths. Do you really want to load new traces?", "Warning"))
					return;
				final int preLoadingState = currentState;
				changeState(LOADING);
				if (source == loadTracesMenuItem)
					plugin.loadTracesFile();
				else
					plugin.loadSWCFile();
				changeState(preLoadingState);

			} else if (source == exportAllSWCMenuItem && !noPathsError()) {

				if (pathAndFillManager.usingNonPhysicalUnits() && !guiUtils.getConfirmation(
						"These tracings were obtained from a spatially uncalibrated "
								+ "image but the SWC specification assumes all coordinates to be " + "in "
								+ GuiUtils.micrometer() + ". Do you really want to proceed " + "with the SWC export?",
						"Warning"))
					return;

				final File saveFile = saveFile("Export All Paths as SWC...", null, ".swc");
				if (saveFile == null)
					return; // user pressed cancel
				if (saveFile.exists()) {
					if (!guiUtils.getConfirmation("The file " + saveFile.getAbsolutePath() + " already exists.\n"
							+ "Do you want to replace it?", "Override SWC file?"))
						return;
				}
				final String savePath = saveFile.getAbsolutePath();
				SNT.log("Exporting paths to " + saveFile);
				if (!checkOKToWriteAllAsSWC(savePath))
					return;
				plugin.unsavedPaths = !pathAndFillManager.exportAllPathsAsSWC(savePath);
			} else if (source == exportCSVMenuItem && !noPathsError()) {

				final File saveFile = saveFile("Export All Paths as CSV...", null, ".csv");
				if (saveFile == null)
					return; // user pressed cancel
				if (saveFile.exists()) {
					if (!guiUtils.getConfirmation("The file " + saveFile.getAbsolutePath() + " already exists.\n"
							+ "Do you want to replace it?", "Override CSV file?"))
						return;
				}
				final String savePath = saveFile.getAbsolutePath();
				showStatus("Exporting as CSV to " + savePath, false);

				final int preExportingState = currentState;
				changeState(SAVING);
				// Export here...
				try {
					pathAndFillManager.exportToCSV(saveFile);
				} catch (final IOException ioe) {
					showStatus("Exporting failed.", true);
					guiUtils.error("Writing traces to '" + savePath + "' failed. See Console for details.");
					changeState(preExportingState);
					ioe.printStackTrace();
					return;
				}
				showStatus("Export complete.", true);
				changeState(preExportingState);

			} else if (source == measureMenuItem && !noPathsError()) {
				final Tree tree = new Tree(pathAndFillManager.getPathsFiltered());
				tree.setLabel("All Paths");
				final TreeAnalyzer ta = new TreeAnalyzer(tree);
				ta.setContext(plugin.getContext());
				ta.setTable(pmUI.getTable(), PathManagerUI.TABLE_TITLE);
				ta.run();
				return;
			} else if (source == strahlerMenuItem && !noPathsError()) {
				final StrahlerCmd sa = new StrahlerCmd(new Tree(pathAndFillManager.getPathsFiltered()));
				sa.setContext(plugin.getContext());
				sa.setTable(new DefaultGenericTable(), "SNT: Horton-Strahler Analysis (All Paths)");
				sa.run();
				return;
			} else if (source == plotMenuItem && !noPathsError()) {
				final Map<String, Object> input = new HashMap<>();
				final Tree tree = new Tree(pathAndFillManager.getPathsFiltered());
				tree.setLabel("SNT Plotter");
				input.put("tree", tree);
				final CommandService cmdService = plugin.getContext().getService(CommandService.class);
				cmdService.run(PlotterCmd.class, true, input);
				return;
			} else if (source == loadLabelsMenuItem) {

				final File openFile = openFile("Select Labels File...", ".labels");
				if (openFile != null) { // null if user pressed cancel;
					plugin.loadLabelsFile(openFile.getAbsolutePath());
					return;
				}

			} else if (source == keepSegment) {

				plugin.confirmTemporary();

			} else if (source == junkSegment) {

				plugin.cancelTemporary();

			} else if (source == completePath) {

				plugin.finishedPath();

			} else if (source == quitMenuItem) {

				exitRequested();

			} else if (source == showOrHidePathList) {

				togglePathListVisibility();

			} else if (source == showOrHideFillList) {

				toggleFillListVisibility();

			} else if (e.getActionCommand().equals(EDIT_SIGMA_MANUALLY)) {

				if (plugin.cachedTubeness == null) {
					setSigmaFromUser();
				} else if (plugin.hessianMultiplier == -1) {
					// An image has been loaded and sigma is not known
					setMultiplierForCachedTubenessFromUser();
				} else if (okToFlushCachedTubeness()) {
					setSigmaFromUser();
				}

			} else if (e.getActionCommand().equals(EDIT_SIGMA_VISUALLY)) {

				if (okToFlushCachedTubeness()) {
					preSigmaPaletteState = currentState;
					changeState(WAITING_FOR_SIGMA_POINT);
					plugin.setCanvasLabelAllPanes("Choosing Sigma");
				}
			}

			else if (source == colorImageChoice) {

				if (!ignoreColorImageChoiceEvents)
					checkForColorImageChange();

			}
		}

		private void restorePreSigmaState() {
			changeState(preSigmaPaletteState);
			plugin.setCanvasLabelAllPanes(null);
		}

		private boolean checkOKToWriteAllAsSWC(final String prefix) {
			final List<Path> primaryPaths = Arrays.asList(pathAndFillManager.getPathsStructured());
			final int n = primaryPaths.size();
			StringBuilder errorMessage = new StringBuilder();
			for (int i = 0; i < n; ++i) {
				final File swcFile = pathAndFillManager.getSWCFileForIndex(prefix, i);
				if (swcFile.exists())
					errorMessage.append(swcFile.getAbsolutePath()).append("\n");
			}
			if (errorMessage.length() == 0)
				return true;
			else {
				errorMessage.insert(0, "The following files would be overwritten:\n");
				errorMessage.append("Continue to save, overwriting these files?");
				return guiUtils.getConfirmation(errorMessage.toString(), "Confirm overwriting SWC files?");
			}
		}
	}

	private class CmdRunner extends ActiveWorker {

		private final Class<? extends Command> cmd;
		private final int preRunState;
		private final boolean run;
		private HashMap<String, Object> inputs;

		// Cmd that does not require rebuilding canvas(es) nor changing UI state
		public CmdRunner(final Class<? extends Command> cmd) {
			this(cmd, null, getCurrentState());
		}

		public CmdRunner(final Class<? extends Command> cmd, final HashMap<String, Object> inputs,
				final int uiStateduringRun) {
			assert SwingUtilities.isEventDispatchThread();
			this.cmd = cmd;
			this.preRunState = getCurrentState();
			this.inputs = inputs;
			run = initialize();
			if (run && preRunState != uiStateduringRun)
				changeState(uiStateduringRun);
			activeWorker = this;
		}

		private boolean initialize() {
			if (preRunState == SNTUI.EDITING) {
				guiUtils.error(
						"Please finish editing " + plugin.getEditingPath().getName() + " before running this command.");
				return false;
			}
			final boolean rebuildCanvas = inputs != null && inputs.get("rebuildCanvas") == (Boolean) true;
			final boolean dataCouldBeLost = plugin.accessToValidImageData()
					|| (plugin.getImagePlus() != null && plugin.getImagePlus().changes);
			final boolean rebuild = rebuildCanvas && dataCouldBeLost && guiUtils.getConfirmation("<HTML><div WIDTH=500>" //
					+ "Coordinates of external reconstructions <i>may</i> fall outside the boundaries " //
					+ "of current image. Would you like to close active image and use a display canvas " //
					+ "with computed dimensions containing all the nodes of the imported file?", //
					"Change to Display Canvas?", "Yes. Use Display Canvas", "No. Use Current Image");
			if (inputs != null)
				inputs.put("rebuildCanvas", rebuild);
			if (rebuild)
				plugin.tracingHalted = true;
			return true;
		}

		@Override
		public String doInBackground() {
			if (!run)
				return "Ongoing task";
			try {
				final CommandService cmdService = plugin.getContext().getService(CommandService.class);
				final CommandModule cmdModule = cmdService.run(cmd, true, inputs).get();
				return (cmdModule.isCanceled()) ? cmdModule.getCancelReason() : "Command completed";
			} catch (final NullPointerException e1) {
				// This seems to happen if command is DynamicCommand
				return "Scijava Command...";
			} catch (final CancellationException | InterruptedException | ExecutionException e2) {
				return e2.getMessage();
			} catch (final IllegalArgumentException e3) {
				e3.printStackTrace();
				return "Unfortunately an error occured. See console for details.";
			}
		}

		@Override
		protected void done() {
			try {
				showStatus((String) get(), true);
			} catch (InterruptedException | ExecutionException e) {
				showStatus("Command terminated...", false);
			}
			if (run && preRunState != getCurrentState())
				changeState(preRunState);
		}
	}

	private class ActiveWorker extends SwingWorker<Object, Object> {

		@Override
		protected Object doInBackground() throws Exception {
			return null;
		}

		public boolean kill() {
			return cancel(true);
		}
	}
}
