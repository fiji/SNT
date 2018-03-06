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

package tracing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.scijava.util.ClassUtils;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.HTMLDialog;
import ij.gui.StackWindow;
import ij.measure.Calibration;
import ij3d.ImageWindow3D;
import net.imagej.table.DefaultGenericTable;
import sholl.Sholl_Analysis;
import tracing.gui.ColorChangedListener;
import tracing.gui.ColorChooserButton;
import tracing.gui.GuiUtils;
import tracing.gui.SigmaPalette;
import tracing.hyperpanes.MultiDThreePanes;
import tracing.plugin.PathAnalyzer;
import tracing.plugin.StrahlerAnalyzer;

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
	private JComboBox<String> filterChoice;
	private JRadioButton showPathsSelected;
	private JRadioButton showPathsAll;
	protected JRadioButton showPartsNearby;
	protected JRadioButton showPartsAll;
	protected JCheckBox useSnapWindow;
	protected JSpinner snapWindowXYsizeSpinner;
	protected JSpinner snapWindowZsizeSpinner;
	private JSpinner nearbyFieldSpinner;
	private JPanel hessianPanel;
	private JCheckBox preprocess;
	private JButton displayFiltered;
	private JButton showOrHidePathList;
	private JButton showOrHideFillList;
	private JMenuItem loadTracesMenuItem;
	private JMenuItem loadSWCMenuItem;
	private JMenuItem loadLabelsMenuItem;
	private JMenuItem saveMenuItem;
	private JMenuItem exportCSVMenuItem;
	private JMenuItem exportAllSWCMenuItem;
	private JMenuItem quitMenuItem;
	private JMenuItem measureMenuItem;
	private JMenuItem strahlerMenuItem;
	private JMenuItem sendToTrakEM2;
	private JLabel statusText;
	private JLabel statusBarText;
	private JButton keepSegment;
	private JButton junkSegment;
	protected JButton abortButton;
	private JButton completePath;

	// UI controls for loading 'filtered image'
	private JPanel filteredImgPanel;
	private JTextField filteredImgPathField;
	private JButton filteredImgInitButton;
	private JButton filteredImgLoadButton;
	private JComboBox<String> filteredImgParserChoice;
	private final List<String> filteredImgAllowedExts = Arrays.asList("tif", "nrrd");
	private JCheckBox filteredImgActivateCheckbox;
	private SwingWorker<?, ?> filteredImgLoadingWorker;

	private JPanel colorPanel;
	private static final int MARGIN = 4;
	private volatile int currentState;
	private volatile double currentSigma;
	private volatile double currentMultiplier;
	private volatile boolean ignoreColorImageChoiceEvents = false;
	private volatile boolean ignorePreprocessEvents = false;
	private volatile int preGaussianState;

	private final SimpleNeuriteTracer plugin;
	private final PathAndFillManager pathAndFillManager;
	private final GuiUtils guiUtils;
	private final PathWindow pw;
	private final FillWindow fw;
	protected final GuiListener listener;

	/* These are the states that the UI can be in: */
	static final int WAITING_TO_START_PATH = 0;
	static final int PARTIAL_PATH = 1;
	static final int SEARCHING = 2;
	static final int QUERY_KEEP = 3;
	// static final int LOGGING_POINTS = 4;
	// static final int DISPLAY_EVS = 5;
	static final int FILLING_PATHS = 6;
	static final int CALCULATING_GAUSSIAN = 7;
	static final int WAITING_FOR_SIGMA_POINT = 8;
	static final int WAITING_FOR_SIGMA_CHOICE = 9;
	static final int SAVING = 10;
	static final int LOADING = 11;
	static final int FITTING_PATHS = 12;
	static final int LOADING_FILTERED_IMAGE = 13;
	static final int EDITING_MODE = 14;
	static final int PAUSED = 15;
	static final int IMAGE_CLOSED = -1;

	// TODO: Internal preferences: should be migrated to SNTPrefs
	protected boolean confirmTemporarySegments = true;
	protected boolean finishOnDoubleConfimation = true;
	protected boolean discardOnDoubleCancellation = true;

	public NeuriteTracerResultsDialog(final SimpleNeuriteTracer plugin) {

		super(plugin.legacyService.getIJ1Helper().getIJ(), "SNT v" + SNT.VERSION, false);
		guiUtils = new GuiUtils(this);
		this.plugin = plugin;
		new ClarifyingKeyListener(plugin).addKeyAndContainerListenerRecursively(this);
		listener = new GuiListener();

		assert SwingUtilities.isEventDispatchThread();

		pathAndFillManager = plugin.getPathAndFillManager();
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(final WindowEvent e) {
				exitRequested();
			}
		});

		final JTabbedPane tabbedPane = new JTabbedPane();
		final JPanel tab1 = getTab();
		final GridBagConstraints c = GuiUtils.defaultGbc();
		//c.insets.left = MARGIN * 2;
		c.anchor = GridBagConstraints.NORTHEAST;
		GuiUtils.addSeparator(tab1, "Cursor Auto-snapping:", false, c);
		++c.gridy;
		tab1.add(snappingPanel(), c);
		++c.gridy;
		GuiUtils.addSeparator(tab1, "Auto-tracing:", true, c);
		++c.gridy;
		tab1.add(autoTracingPanel(), c);
		++c.gridy;
		tab1.add(filteredImagePanel(), c);
		++c.gridy;
		GuiUtils.addSeparator(tab1, "Path Rendering:", true, c);
		++c.gridy;
		tab1.add(renderingPanel(), c);
		++c.gridy;
		GuiUtils.addSeparator(tab1, "Path Labelling:", true, c);
		++c.gridy;
		tab1.add(colorPanel = colorOptionsPanel(), c);
		++c.gridy;
		GuiUtils.addSeparator(tab1, "", true, c); // empty separator
		++c.gridy;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0, 0, 0, 0);
		tab1.add(hideWindowsPanel(), c);
		tabbedPane.addTab(" Main ", tab1);

		final JPanel tab2 = getTab();
		tab2.setLayout(new GridBagLayout());
		final GridBagConstraints c2 = GuiUtils.defaultGbc();
		c.insets.left = MARGIN * 2;
		c2.anchor = GridBagConstraints.NORTHEAST;
		c2.gridwidth = GridBagConstraints.REMAINDER;
		GuiUtils.addSeparator(tab2, "Data Source:", false, c2);
		++c2.gridy;
		tab2.add(sourcePanel(), c2);
		++c2.gridy;
		GuiUtils.addSeparator(tab2, "Views:", true, c2);
		++c2.gridy;
		tab2.add(viewsPanel(), c2);
		++c2.gridy;
		GuiUtils.addSeparator(tab2, "Temporary Paths:", true, c2);
		++c2.gridy;
		tab2.add(tracingPanel(), c2);
		++c2.gridy;
		GuiUtils.addSeparator(tab2, "UI Interaction:", true, c2);
		++c2.gridy;
		tab2.add(interactionPanel(), c2);
		++c2.gridy;
		GuiUtils.addSeparator(tab2, "Misc:", true, c2);
		++c2.gridy;
		c2.weighty = 1;
		tab2.add(miscPanel(), c2);
		tabbedPane.addTab(" Options ", tab2);
		tabbedPane.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				if (tabbedPane.getSelectedIndex() == 1 &&
						getCurrentState() > WAITING_TO_START_PATH && getCurrentState() < EDITING_MODE) {
					tabbedPane.setSelectedIndex(0);
					guiUtils.blinkingError(statusText,
						"Please complete current task before selecting the \"Options\" tab.");
				}
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
					guiUtils.centeredMsg("The calibration of '" + intendedColorImage
						.getTitle() + "' is different from the image you're tracing ('" +
						image.getTitle() + "')'\nThis may produce unexpected results.", "Warning");
				}
				if (!(intendedColorImage.getWidth() == image.getWidth() &&
					intendedColorImage.getHeight() == image.getHeight() &&
					intendedColorImage.getStackSize() == image.getStackSize())) guiUtils.centeredMsg(
						"the dimensions (in voxels) of '" + intendedColorImage
							.getTitle() + "' is different from the image you're tracing ('" +
							image.getTitle() + "')'\nThis may produce unexpected results.", "Warning");
				
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
				updateHessianLabel();
			}
		});
	}

	public void setSigma(final double sigma, final boolean mayStartGaussian) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				currentSigma = sigma;
				updateHessianLabel();
				if (!mayStartGaussian) return;
				preprocess.setSelected(false);

				// Turn on the checkbox: according to the documentation this doesn't
				// generate an event, so we manually turn on the Gaussian calculation
				ignorePreprocessEvents = true;
				preprocess.setSelected(true);
				ignorePreprocessEvents = false;
				enableHessian(true);
			}
		});
	}

	private void updateHessianLabel() {
		final String label = hotKeyLabel("Hessian-based analysis (\u03C3 = " + SNT.formatDouble(
				currentSigma, 2) + "; \u00D7 = " + SNT.formatDouble(currentMultiplier,2) +
				")", "H");
		assert SwingUtilities.isEventDispatchThread();
		preprocess.setText(label);
	}

	public double getSigma() {
		return currentSigma;
	}

	public double getMultiplier() {
		return currentMultiplier;
	}

	protected void exitRequested() {
		assert SwingUtilities.isEventDispatchThread();
		if (plugin.pathsUnsaved() && !guiUtils.getConfirmation(
			"There are unsaved paths. Do you really want to quit?", "Really quit?"))
			return;
		if (pw.measurementsUnsaved() && !guiUtils.getConfirmation(
			"There are unsaved measurements. Do you really want to quit?", "Really quit?"))
			return;
		plugin.cancelSearch(true);
		plugin.notifyListeners(new SNTEvent(SNTEvent.QUIT));
		plugin.prefs.savePluginPrefs(true);
		pw.dispose();
		pw.closeTable();
		fw.dispose();
		dispose();
		plugin.closeAndResetAllPanes();
	}

	private void setEnableAutoTracingComponents(final boolean enable) {
		GuiUtils.enableComponents(hessianPanel, enable);
		GuiUtils.enableComponents(filteredImgPanel, enable);
		if (enable) updateFilteredFileField();
	}

	protected void disableImageDependentComponents() {
		assert SwingUtilities.isEventDispatchThread();
		loadLabelsMenuItem.setEnabled(false);
		fw.setEnabledNone();
		setEnableAutoTracingComponents(false);
		GuiUtils.enableComponents(colorPanel, false);
	}

	private void disableEverything() {
		assert SwingUtilities.isEventDispatchThread();
		disableImageDependentComponents();
		abortButton.setEnabled(getState()!=WAITING_TO_START_PATH);
		loadTracesMenuItem.setEnabled(false);
		loadSWCMenuItem.setEnabled(false);
		exportCSVMenuItem.setEnabled(false);
		exportAllSWCMenuItem.setEnabled(false);
		measureMenuItem.setEnabled(false);
		sendToTrakEM2.setEnabled(false);
		saveMenuItem.setEnabled(false);
		quitMenuItem.setEnabled(false);
	}

	public void changeState(final int newState) {

		SNT.log("changing state to: " + getState(newState));
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				switch (newState) {

					case WAITING_TO_START_PATH:
						updateStatusText("Click somewhere to start a new path...");

						keepSegment.setEnabled(false);
						junkSegment.setEnabled(false);
						completePath.setEnabled(false);
						abortButton.setEnabled(false);

						pw.valueChanged(null); // Fake a selection change in the path tree:
						showPartsNearby.setEnabled(isStackAvailable());
						setEnableAutoTracingComponents(!plugin.isAstarDisabled());
						fw.setEnabledWhileNotFilling();
						loadLabelsMenuItem.setEnabled(true);
						saveMenuItem.setEnabled(true);
						loadTracesMenuItem.setEnabled(true);
						loadSWCMenuItem.setEnabled(true);

						exportCSVMenuItem.setEnabled(true);
						exportAllSWCMenuItem.setEnabled(true);
						measureMenuItem.setEnabled(true);
						sendToTrakEM2.setEnabled(plugin.anyListeners());
						quitMenuItem.setEnabled(true);
						GuiUtils.enableComponents(colorPanel, true);
						showPathsSelected.setEnabled(true);
						showOrHideFillList.setEnabled(true);
						break;

					case PARTIAL_PATH:
						updateStatusText("Select a point further along the structure...");
						disableEverything();
						keepSegment.setEnabled(false);
						junkSegment.setEnabled(false);
						completePath.setEnabled(true);
						showPartsNearby.setEnabled(isStackAvailable());
						setEnableAutoTracingComponents(!plugin.isAstarDisabled());
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
						fw.setEnabledWhileFilling();
						break;

					case FITTING_PATHS:
						updateStatusText("Fitting volumes around selected paths...");
						abortButton.setEnabled(true);
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

					case EDITING_MODE:
						if (noPathsError()) return;
						updateStatusText("Editing Mode. Tracing functions disabled...");
						disableEverything();
						keepSegment.setEnabled(false);
						junkSegment.setEnabled(false);
						completePath.setEnabled(false);
						showPartsNearby.setEnabled(isStackAvailable());
						setEnableAutoTracingComponents(false);
						getFillWindow().setVisible(false);
						showOrHideFillList.setEnabled(false);
						break;

					case PAUSED:
						updateStatusText("SNT is paused. Tracing functions disabled...");
						disableEverything();
						keepSegment.setEnabled(false);
						junkSegment.setEnabled(false);
						abortButton.setEnabled(true);
						completePath.setEnabled(false);
						showPartsNearby.setEnabled(isStackAvailable());
						setEnableAutoTracingComponents(false);
						getFillWindow().setVisible(false);
						showOrHideFillList.setEnabled(false);
						break;

					case IMAGE_CLOSED:
						updateStatusText("Tracing image is no longer available...");
						disableImageDependentComponents();
						plugin.discardFill(false);
						quitMenuItem.setEnabled(true);
						return;

					default:
						SNT.error("BUG: switching to an unknown state");
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
		return plugin != null && !plugin.is2D();
	}

	private JPanel sourcePanel() { // User inputs for multidimensional images

		final JPanel sourcePanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		gdb.gridwidth = 1;

		final boolean hasChannels = plugin.getImagePlus().getNChannels() > 1;
		final boolean hasFrames = plugin.getImagePlus().getNFrames() > 1;
		final JPanel positionPanel = new JPanel(new FlowLayout(FlowLayout.LEADING,
			4, 0));
		positionPanel.add(GuiUtils.leftAlignedLabel("Channel", hasChannels));
		final JSpinner channelSpinner = GuiUtils.integerSpinner(plugin.channel, 1,
			plugin.getImagePlus().getNChannels(), 1);
		positionPanel.add(channelSpinner);
		positionPanel.add(GuiUtils.leftAlignedLabel(" Frame", hasFrames));
		final JSpinner frameSpinner = GuiUtils.integerSpinner(plugin.frame, 1,
			plugin.getImagePlus().getNFrames(), 1);
		positionPanel.add(frameSpinner);
		final JButton applyPositionButton = new JButton("Reload");
		final ChangeListener spinnerListener = new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				applyPositionButton.setText(((int) channelSpinner
					.getValue() == plugin.channel && (int) frameSpinner
						.getValue() == plugin.frame) ? "Reload" : "Apply");
			}
		};
		channelSpinner.addChangeListener(spinnerListener);
		frameSpinner.addChangeListener(spinnerListener);
		channelSpinner.setEnabled(hasChannels);
		frameSpinner.setEnabled(hasFrames);
		applyPositionButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				if (getState()==IMAGE_CLOSED) {
					guiUtils.error("Tracing image is no longer available.");
					return;
				}
				final int newC = (int) channelSpinner.getValue();
				final int newT = (int) frameSpinner.getValue();
				final boolean reload = newC == plugin.channel && newT == plugin.frame;
				if (!reload && !guiUtils.getConfirmation(
					"You are currently tracing position C=" + plugin.channel + ", T=" +
						plugin.frame + ". Start tracing C=" + newC + ", T=" + newT + "?",
					"Change Hyperstack Position?"))
				{
					return;
				}
				plugin.reloadImage(newC, newT);
				preprocess.setSelected(false);
				showStatus(reload ? "Image reloaded into memory..." : null);
			}
		});
		positionPanel.add(applyPositionButton);
		sourcePanel.add(positionPanel, gdb);
		return sourcePanel;
	}

	private JPanel viewsPanel() {
		final JPanel viewsPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		gdb.gridwidth = 1;

		final JPanel mipPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		final JCheckBox mipOverlayCheckBox = new JCheckBox("Overlay MIP(s) at");
		mipOverlayCheckBox.setEnabled(!plugin.is2D());
		mipPanel.add(mipOverlayCheckBox);
		final JSpinner mipSpinner = GuiUtils.integerSpinner(20, 10, 80, 1);
		mipSpinner.setEnabled(!plugin.is2D());
		mipSpinner.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				mipOverlayCheckBox.setSelected(false);
			}
		});
		mipPanel.add(mipSpinner);
		mipPanel.add(GuiUtils.leftAlignedLabel(" % opacity", !plugin.is2D()));
		mipOverlayCheckBox.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				plugin.showMIPOverlays((mipOverlayCheckBox.isSelected())
					? (int) mipSpinner.getValue() * 0.01 : 0);
			}
		});
		viewsPanel.add(mipPanel, gdb);
		++gdb.gridy;

		final JCheckBox diametersCheckBox = new JCheckBox(
			"Draw diameters in XY view", plugin.getDrawDiametersXY());
		diametersCheckBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				plugin.setDrawDiametersXY(e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		viewsPanel.add(diametersCheckBox, gdb);
		++gdb.gridy;

		final JCheckBox zoomAllPanesCheckBox = new JCheckBox("Apply zoom changes to all views", !plugin.isZoomAllPanesDisabled());
		zoomAllPanesCheckBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				plugin.disableZoomAllPanes(e.getStateChange() == ItemEvent.DESELECTED);
			}
		});
		viewsPanel.add(zoomAllPanesCheckBox, gdb);
		++gdb.gridy;

		String bLabel = (plugin.getSinglePane()) ? "Display" : "Rebuild";
		final JButton refreshPanesButton = new JButton(bLabel + " ZY/XZ views");
		refreshPanesButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				if (getState()==IMAGE_CLOSED) {
					guiUtils.error("Tracing image is no longer available.");
					return;
				}
				plugin.rebuildZYXZpanes();
				showStatus("ZY/XZ views reloaded...");
				refreshPanesButton.setText("Rebuild ZY/XZ views");
				arrangeCanvases();
			}
		});
		gdb.fill = GridBagConstraints.NONE;
		viewsPanel.add(refreshPanesButton, gdb);
		return viewsPanel;
	}

	private JPanel tracingPanel() {
		final JPanel tPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = GuiUtils.defaultGbc();
		final JCheckBox confirmTemporarySegmentsCheckbox = new JCheckBox("Confirm temporary segments",
				confirmTemporarySegments);
		final JCheckBox confirmCheckbox = new JCheckBox("Pressing 'Y' twice finishes path",
				finishOnDoubleConfimation);
		final JCheckBox finishCheckbox = new JCheckBox(
				"Pressing 'N' twice cancels path", discardOnDoubleCancellation);
		confirmTemporarySegmentsCheckbox.addItemListener(new ItemListener() {

				@Override
				public void itemStateChanged(final ItemEvent e) {
					confirmTemporarySegments = (e.getStateChange() == ItemEvent.SELECTED);
					confirmCheckbox.setEnabled(confirmTemporarySegments);
					finishCheckbox.setEnabled(confirmTemporarySegments);
				}
			});
		
		confirmCheckbox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				finishOnDoubleConfimation = (e.getStateChange() == ItemEvent.SELECTED);
			}
		});
		confirmCheckbox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				discardOnDoubleCancellation = (e.getStateChange() == ItemEvent.SELECTED);
			}
		});
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

		final JCheckBox canvasCheckBox = new JCheckBox(
				"Activate canvas on mouse hovering", plugin.autoCanvasActivation);
			canvasCheckBox.addItemListener(new ItemListener() {

				@Override
				public void itemStateChanged(final ItemEvent e) {
					plugin.enableAutoActivation(e.getStateChange() == ItemEvent.SELECTED);
				}
			});
		intPanel.add(canvasCheckBox, gdb);
		++gdb.gridy;
		return intPanel;
	}

	private JPanel nodePanel() {
		final JSpinner nodeSpinner = GuiUtils.doubleSpinner(plugin.getXYCanvas().nodeDiameter(), 0, 100, 1, 0);
		nodeSpinner.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				final double value = (double) (nodeSpinner.getValue());
				plugin.xy_tracer_canvas.setNodeDiameter(value);
				if (!plugin.getSinglePane()) {
					plugin.xz_tracer_canvas.setNodeDiameter(value);
					plugin.zy_tracer_canvas.setNodeDiameter(value);
				}
				plugin.repaintAllPanes();
			};
		});
		final JButton defaultsButton = new JButton("Default");
		defaultsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				plugin.xy_tracer_canvas.setNodeDiameter(-1);
				if (!plugin.getSinglePane()) {
					plugin.xz_tracer_canvas.setNodeDiameter(-1);
					plugin.zy_tracer_canvas.setNodeDiameter(-1);
				}
				nodeSpinner.setValue(plugin.xy_tracer_canvas.nodeDiameter());
				showStatus("Node scale reset");
			}
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
		hm.put("Canvas annotations", plugin.getXYCanvas().getAnnotationsColor());
		hm.put("Fills", plugin.getXYCanvas().getFillColor());
		hm.put("Unconfirmed paths", plugin.getXYCanvas().getUnconfirmedPathColor());
		hm.put("Temporary paths", plugin.getXYCanvas().getTemporaryPathColor());

		final JComboBox<String> colorChoice = new JComboBox<>();
		for (final Entry<String, Color> entry : hm.entrySet())
			colorChoice.addItem(entry.getKey());

		final String selectedKey = String.valueOf(colorChoice
			.getSelectedItem());
		final ColorChooserButton cChooser = new ColorChooserButton(hm.get(
			selectedKey), "Change...", 1, SwingConstants.RIGHT);

		colorChoice.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				cChooser.setSelectedColor(hm.get(String.valueOf(colorChoice
					.getSelectedItem())), false);
			}
		});

		cChooser.addColorChangedListener(new ColorChangedListener() {

			@Override
			public void colorChanged(final Color newColor) {
				final String selectedKey = String.valueOf(colorChoice
					.getSelectedItem());
				switch (selectedKey) {
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
			}
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
		final JCheckBox winLocCheckBox = new JCheckBox("Remember window locations",
			plugin.prefs.isSaveWinLocations());
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
					guiUtils.centeredMsg("You should now restart SNT for changes to take effect",
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
		statusChoicesPanel.setBorder(new EmptyBorder(0, 0, MARGIN*2, 0));
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		gbc.ipadx = 0;
		gbc.ipady = 0;
		
		gbc.insets = new Insets(0, 0, 0, 0);
		keepSegment = GuiUtils.smallButton(hotKeyLabel("Yes", "Y"));
		keepSegment.addActionListener(listener);
		gbc.weightx = 0.25;
		statusChoicesPanel.add(keepSegment, gbc);
		gbc.ipadx = 2;
		junkSegment = GuiUtils.smallButton(hotKeyLabel("&thinsp;No&thinsp;", "N"));
		junkSegment.addActionListener(listener);
		gbc.gridx = 1;
		statusChoicesPanel.add(junkSegment, gbc);
		completePath = GuiUtils.smallButton(hotKeyLabel("Finish", "F"));
		completePath.addActionListener(listener);
		gbc.gridx = 2;
		statusChoicesPanel.add(completePath, gbc);
		gbc.gridx = 3;
		abortButton = GuiUtils.smallButton(hotKeyLabel(hotKeyLabel("Cancel/Esc", "C"), "Esc"));
		abortButton.addActionListener(listener);
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
		statusText.setBackground(Color.WHITE);
		statusText.setBorder(BorderFactory.createCompoundBorder(BorderFactory
			.createBevelBorder(BevelBorder.LOWERED), BorderFactory.createEmptyBorder(
				MARGIN, MARGIN, MARGIN, MARGIN)));
		statusPanel.add(statusText, BorderLayout.CENTER);
		final JPanel buttonPanel = statusButtonPanel();
		statusPanel.add(buttonPanel, BorderLayout.SOUTH);
		statusPanel.setBorder(BorderFactory.createEmptyBorder(MARGIN,MARGIN,MARGIN*2,MARGIN));
		return statusPanel;
	}

	private JPanel filteredImagePanel() {
		filteredImgPathField = new JTextField();
		filteredImgLoadButton = GuiUtils.smallButton("Choose...");
		filteredImgParserChoice = new JComboBox<>();
		filteredImgParserChoice.addItem("Simple Neurite Tracer");
		filteredImgParserChoice.addItem("ITK: Tubular Geodesics");
		filteredImgInitButton = GuiUtils.smallButton("Initialize...");
		filteredImgActivateCheckbox = new JCheckBox(hotKeyLabel("Trace using filtered Image", "I"));
		filteredImgActivateCheckbox.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				enableFilteredImgTracing(filteredImgActivateCheckbox.isSelected());
			}
		});

		filteredImgPathField.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(final DocumentEvent e) {
				updateFilteredFileField();
			}

			public void removeUpdate(final DocumentEvent e) {
				updateFilteredFileField();
			}

			public void insertUpdate(final DocumentEvent e) {
				updateFilteredFileField();
			}

		});
		
		filteredImgLoadButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				final File file = guiUtils.openFile("Choose filtered image", new File(filteredImgPathField.getText()), filteredImgAllowedExts);
				if (file == null) return;
				filteredImgPathField.setText(file.getAbsolutePath());
			}
		});

		filteredImgInitButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				if (plugin.isTracingOnFilteredImageAvailable()) { // Toggle: set action to disable filtered tracing
					if (!guiUtils.getConfirmation("Disable access to filtered image?", "Unload Image?"))
						return;

					// reset cached filtered image/Tubular Geodesics
					plugin.filteredData = null;
					plugin.doSearchOnFilteredData = false;
					if (plugin.tubularGeodesicsTracingEnabled) {
						if (plugin.tubularGeodesicsThread != null)
							plugin.tubularGeodesicsThread.requestStop();
						plugin.tubularGeodesicsThread = null;
						plugin.tubularGeodesicsTracingEnabled = false;
					}
					System.gc();
					updateFilteredImgFields();

				} else { // toggle: set action to enable filtered tracing
					final File file = new File(filteredImgPathField.getText());
					if (!SNT.fileAvailable(file)) {
						guiUtils.error(file.getAbsolutePath() + " is not available. Image could not be loaded.",
								"File Unavailable");
						return;
					}
					plugin.setFilteredImage(file);

					if (filteredImgParserChoice.getSelectedIndex() == 0) { // SNT if (!"Simple Neurite Tracer".equals(parserChoice.getSelectedItem()) {
						final int byteDepth = 32 / 8;
						final ImagePlus tracingImp = plugin.getImagePlus();
						final long megaBytesExtra = (((long) tracingImp.getWidth()) * tracingImp.getHeight()
								* tracingImp.getNSlices() * byteDepth * 2) / (1024 * 1024);
						final long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
						if (!guiUtils.getConfirmation(
								"Load " + file.getAbsolutePath() + "? This operation will likely require "
										+ megaBytesExtra + "MiB of RAM (currently available: " + maxMemory + " MiB).",
								"Confirm Loading?"))
							return;
						loadFilteredImage();
	
					} else if (filteredImgParserChoice.getSelectedIndex() == 1) { // Tubular Geodesics

						if (ClassUtils.loadClass("FijiITKInterface.TubularGeodesics") == null) {
							guiUtils.error("The 'Tubular Geodesics' plugin does not seem to be installed!");
							return;
						}
						plugin.tubularGeodesicsTracingEnabled = true;
						updateFilteredImgFields();
					}
				}
			}
		});

		filterChoice = new JComboBox<>();
		filterChoice.addItem("None");
		filterChoice.addItem("Frangi Vesselness");
		filterChoice.addItem("Tubeness");
		filterChoice.addItem("Tubular Geodesics");
		filterChoice.addItem("Other...");
		filterChoice.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				displayFiltered.setEnabled(filterChoice.getSelectedIndex()>0);
				guiUtils.centeredMsg("This feature is not yet implemented", "Not Yet Implemented");
				filterChoice.setSelectedIndex(0);
			}
		});

		filteredImgPanel = new JPanel();
		filteredImgPanel.setLayout(new GridBagLayout());
		GridBagConstraints c = GuiUtils.defaultGbc();
		c.gridwidth = GridBagConstraints.REMAINDER;

		// Header
		GuiUtils.addSeparator(filteredImgPanel, "Tracing on Filtered Image:", true, c);

		c = new GridBagConstraints();
		c.ipadx = 0; 
		c.insets = new Insets(0, 0, 0, 0);
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		// row 1
		c.gridy = 1;
		c.gridx = 0;
		filteredImgPanel.add(GuiUtils.leftAlignedLabel("Image: ", true), c);
		c.gridx++;
		c.weightx = 1;
		filteredImgPanel.add(filteredImgPathField, c);
		c.gridx++;
		c.weightx = 0;
		filteredImgPanel.add(filteredImgLoadButton, c);
		c.gridx++;

		// row 2
		c.gridy++; c.gridx = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		filteredImgPanel.add(GuiUtils.leftAlignedLabel("Parser: ", true), c);
		c.gridx++;
		filteredImgPanel.add(filteredImgParserChoice, c);
		c.gridx++;
		c.gridwidth = GridBagConstraints.REMAINDER;
		filteredImgPanel.add(filteredImgInitButton, c);
		c.gridx++;

		// row 3
		c.gridy++; c.gridx=0;
		filteredImgPanel.add(filteredImgActivateCheckbox, c);
		return filteredImgPanel;
	}

	private void loadFilteredImage() {
		filteredImgLoadingWorker = new SwingWorker<Object, Object>() {

			@Override
			protected Object doInBackground() throws Exception {

				try {
					plugin.loadFilteredImage();
				} catch (final IllegalArgumentException e1) {
					guiUtils.error("Could not load " + plugin.getFilteredImage().getAbsolutePath() + ":<br>"
							+ e1.getMessage());
					return null;
				} catch (final IOException e2) {
					guiUtils.error("Loading of image failed. See Console for details");
					e2.printStackTrace();
					return null;
				} catch (final OutOfMemoryError e3) {
					plugin.filteredData = null;
					guiUtils.error("It seems you there is not enough memory to proceed. See Console for details");
					e3.printStackTrace();
				}
				return null;
			}

			@Override
			protected void done() {
				changeState(WAITING_TO_START_PATH);
				updateFilteredImgFields();
			}
		};
		changeState(LOADING_FILTERED_IMAGE);
		filteredImgLoadingWorker.run();

	}

	private void updateFilteredImgFields() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				final boolean successfullyLoaded = plugin.isTracingOnFilteredImageAvailable();
				filteredImgParserChoice.setEnabled(!successfullyLoaded);
				filteredImgPathField.setEnabled(!successfullyLoaded);
				filteredImgLoadButton.setEnabled(!successfullyLoaded);
				filteredImgInitButton.setText((successfullyLoaded) ? "Reset" : "Initialize...");
				filteredImgInitButton.setEnabled(successfullyLoaded);
				filteredImgActivateCheckbox.setEnabled(successfullyLoaded);
				if (!successfullyLoaded) filteredImgActivateCheckbox.setSelected(false);
			}
		});
	}

	private void updateFilteredFileField() {
		final String path = filteredImgPathField.getText();
		final boolean validFile = path != null && SNT.fileAvailable(new File(path))
				&& filteredImgAllowedExts.stream().anyMatch(e -> path.endsWith(e));
		filteredImgPathField.setForeground((validFile) ? new JTextField().getForeground() : Color.RED);
		filteredImgInitButton.setEnabled(validFile);
		filteredImgParserChoice.setEnabled(validFile);
		filteredImgActivateCheckbox.setEnabled(validFile);
		filteredImgPathField.setToolTipText((validFile) ? path : "Not a valid file path");
	}

	private JMenuBar createMenuBar() {
		final JMenuBar menuBar = new JMenuBar();
		final JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);
		final JMenu importSubmenu = new JMenu("Import");
		final JMenu exportSubmenu = new JMenu("Export (All Paths)");
		final JMenu analysisMenu = new JMenu("Utilities");
		menuBar.add(analysisMenu);
		final JMenu viewMenu = new JMenu("View");
		menuBar.add(viewMenu);
		menuBar.add(helpMenu());

		loadTracesMenuItem = new JMenuItem("Load Traces...");
		loadTracesMenuItem.addActionListener(listener);
		fileMenu.add(loadTracesMenuItem);

		saveMenuItem = new JMenuItem("Save Traces...");
		saveMenuItem.addActionListener(listener);
		fileMenu.add(saveMenuItem);
		JMenuItem saveTable = new JMenuItem("Save Measurements...");
		saveTable.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				pw.saveTable();
				return;
			}
		});
		fileMenu.add(saveTable);

		sendToTrakEM2 = new JMenuItem("Send to TrakEM2");
		sendToTrakEM2.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				plugin.notifyListeners(new SNTEvent(SNTEvent.SEND_TO_TRAKEM2));
			}
		});
		fileMenu.addSeparator();
		fileMenu.add(sendToTrakEM2);
		fileMenu.addSeparator();

		loadSWCMenuItem = new JMenuItem("(e)SWC...");
		loadSWCMenuItem.addActionListener(listener);
		importSubmenu.add(loadSWCMenuItem);
		loadLabelsMenuItem = new JMenuItem("Labels (AmiraMesh)...");
		loadLabelsMenuItem.addActionListener(listener);
		importSubmenu.add(loadLabelsMenuItem);
		fileMenu.add(importSubmenu);

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

		analysisMenu.addSeparator();
		measureMenuItem = new JMenuItem("Quick Statistics");
		measureMenuItem.addActionListener(listener);
		strahlerMenuItem = new JMenuItem("Strahler Analysis");
		strahlerMenuItem.addActionListener(listener);
		final JMenuItem correspondencesMenuItem = new JMenuItem(
			"Show correspondences with file..");
		correspondencesMenuItem.setEnabled(false); // disable command until it is re-written
		correspondencesMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				final File tracesFile = guiUtils.openFile("Select other traces file...",
					null, Collections.singletonList(".traces"));
				if (tracesFile == null) return;
				if (!tracesFile.exists()) {
					guiUtils.error(tracesFile.getAbsolutePath() + " is not available");
					return;
				}
				// FIXME: 3D VIEWER exclusive method
//				Overlay overlay = plugin.showCorrespondencesTo(tracesFile, Color.YELLOW, 2.5);
//				plugin.getXYCanvas().setOverlay(overlay);
			}
		});
		analysisMenu.add(shollAnalysisHelpMenuItem());
		analysisMenu.add(strahlerMenuItem);
		analysisMenu.add(measureMenuItem);
		analysisMenu.addSeparator();
		analysisMenu.add(correspondencesMenuItem);


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
		final JMenuItem resetZoomMenuItem = new JMenuItem("Reset Zoom Levels");
		resetZoomMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				try {
					plugin.resetZoomAllPanes();
					Thread.sleep(50); // allow windows to resize if needed
					plugin.resetZoomAllPanes();
				} catch (final InterruptedException exc) {
					// do nothing
				}
			}
		});
		viewMenu.add(resetZoomMenuItem);
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

		final JPanel col1 = new JPanel();
		col1.setLayout(new BoxLayout(col1, BoxLayout.Y_AXIS));
		showPathsAll = new JRadioButton(hotKeyLabel("All", "A"), !plugin.showOnlySelectedPaths);
		showPathsAll.addItemListener(listener);
		showPathsSelected = new JRadioButton("Selected", plugin.showOnlySelectedPaths);
		showPathsSelected.addItemListener(listener);

		final ButtonGroup col1Group = new ButtonGroup();
		col1Group.add(showPathsAll);
		col1Group.add(showPathsSelected);
		col1.add(showPathsAll);
		col1.add(showPathsSelected);

		final JPanel col2 = new JPanel();
		col2.setLayout(new BoxLayout(col2, BoxLayout.Y_AXIS));
		final ButtonGroup col2Group = new ButtonGroup();
		showPartsAll = new JRadioButton(hotKeyLabel("Z-stack projection", "Z"));
		col2Group.add(showPartsAll);
		final JPanel row1Panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row1Panel.add(showPartsAll);
		showPartsAll.setSelected(true);
		showPartsAll.setEnabled(isStackAvailable());
		showPartsAll.addItemListener(listener);

		showPartsNearby = new JRadioButton("Up to");
		col2Group.add(showPartsNearby);
		showPartsNearby.setEnabled(isStackAvailable());
		showPartsNearby.addItemListener(listener);
		final JPanel nearbyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		nearbyPanel.add(showPartsNearby);
		nearbyFieldSpinner = GuiUtils.integerSpinner(plugin.depth == 1 ? 1 : 2, 1,
			plugin.depth, 1);
		nearbyFieldSpinner.setEnabled(isStackAvailable());
		nearbyFieldSpinner.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				showPartsNearby.setSelected(true);
				plugin.justDisplayNearSlices(true, (int) nearbyFieldSpinner.getValue());
			}
		});

		nearbyPanel.add(nearbyFieldSpinner);
		nearbyPanel.add(GuiUtils.leftAlignedLabel(" nearby slices", isStackAvailable()));
		col2.add(row1Panel);
		col2.add(nearbyPanel);

		final JPanel viewOptionsPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gdb = new GridBagConstraints();
		gdb.weightx = 0.5;
		viewOptionsPanel.add(col1, gdb);
		gdb.gridx = 1;
		viewOptionsPanel.add(col2, gdb);


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
			plugin.deselectedColor, "Deselected Paths");
		colorChooser2.setName("Color for Deselected Paths");
		colorChooser2.addColorChangedListener(new ColorChangedListener() {

			@Override
			public void colorChanged(final Color newColor) {
				plugin.setDeselectedColor(newColor);
			}
		});
		colorButtonPanel.add(colorChooser1);
		colorButtonPanel.add(colorChooser2);

		final JComboBox<String> pathsColorChoice = new JComboBox<>();
		pathsColorChoice.addItem("Default colors");
		pathsColorChoice.addItem("Path Manager tags (if any)");
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
			FlowLayout.LEADING, 0, 0));
		useSnapWindow = new JCheckBox(hotKeyLabel("Enable Snapping within: XY", "S"),
			plugin.snapCursor);
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

		final JLabel z_spinner_label = GuiUtils.leftAlignedLabel("  Z ", isStackAvailable());
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
		// ensure same alignment of all other panels using defaultGbc
		final JPanel container = new JPanel(new GridBagLayout());
		container.add(tracingOptionsPanel, GuiUtils.defaultGbc());
		return container;
	}

	private JPanel autoTracingPanel() {
		final JPanel autoTracePanel = new JPanel(new GridBagLayout());
		final GridBagConstraints atp_c = GuiUtils.defaultGbc();
		final JCheckBox aStarCheckBox = new JCheckBox(
				"Enable A* search algorithm", !plugin.isAstarDisabled());
		aStarCheckBox.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					final boolean disable = !aStarCheckBox.isSelected();
					if (disable && !guiUtils.getConfirmation(
							"Disable computation of paths? All segmentation tasks will be disabled.",
							"Enable Manual Tracing?")) {
						aStarCheckBox.setSelected(true);
						return;
					}
					plugin.disableAstar(disable);
					setEnableAutoTracingComponents(!disable);
				}
			});
		autoTracePanel.add(aStarCheckBox, atp_c);
		++atp_c.gridy;

		hessianPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints hc = GuiUtils.defaultGbc();
		preprocess = new JCheckBox();
		setSigma(plugin.getMinimumSeparation(), false);
		setMultiplier(4);
		updateHessianLabel();
		preprocess.addActionListener(listener);
		hessianPanel.add(preprocess, hc);
		++hc.gridy;

		// Add sigma ui
		final JPanel sigmaPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 2, 0));
		sigmaPanel.add(GuiUtils.leftAlignedLabel("Choose Sigma: ", !plugin.isAstarDisabled()));	
		final JButton editSigma = GuiUtils.smallButton(GuiListener.EDIT_SIGMA_MANUALLY);
		editSigma.addActionListener(listener);
		sigmaPanel.add(editSigma);
		final JButton sigmaWizard = GuiUtils.smallButton(GuiListener.EDIT_SIGMA_VISUALLY);
		sigmaWizard.addActionListener(listener);
		sigmaPanel.add(sigmaWizard);
		hessianPanel.add(sigmaPanel, hc);
		autoTracePanel.add(hessianPanel, atp_c);
		return autoTracePanel;
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
		statusBarText.setBorder(BorderFactory.createEmptyBorder(0, MARGIN, MARGIN/2, 0));
		statusBar.add(statusBarText);
		refreshStatus();
		return statusBar;
	}

	private void refreshStatus() {
		showStatus(null);
	}

	public void showStatus(String msg) {
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

	private JPanel getTab() {
		final JPanel tab = new JPanel();
		tab.setBorder(BorderFactory.createEmptyBorder(MARGIN *2, MARGIN/2, MARGIN/2, MARGIN));
		tab.setLayout(new GridBagLayout());
		return tab;
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
					newStatus = "Last cursor position: Not reached by search yet";
				}
				else {
					newStatus = "Last cursor position: Distance from path is " + SNT.formatDouble(t, 3);
				}
				fw.fillStatus.setText(newStatus);
			}
		});
	}

	private void setSigmaFromUser() {
		final JTextField sigmaField = new JTextField(SNT.formatDouble(getSigma(),
			5), 5);
		final JTextField multiplierField = new JTextField(SNT.formatDouble(
			getMultiplier(), 1), 5);
		final Object[] contents = {
			"<html><b>Sigma</b><br>Enter the approximate radius of the structures you are<br>" +
				"tracing (the default is the minimum voxel separation,<br>i.e., " +
				SNT.formatDouble(plugin.getMinimumSeparation(), 3) + plugin
					.getImagePlus().getCalibration().getUnit() + ")", sigmaField,
			"<html><br><b>Multiplier</b><br>Enter the scaling factor to apply " +
				"(the default is 4.0):", multiplierField, };
		final int result = JOptionPane.showConfirmDialog(this, contents,
			"Select Scale of Traced Structures", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION) {
			final double sigma = GuiUtils.extractDouble(sigmaField);
			final double multiplier = GuiUtils.extractDouble(multiplierField);
			if (Double.isNaN(sigma) || sigma <= 0 || Double.isNaN(multiplier) ||
				multiplier <= 0)
			{
				guiUtils.error("Sigma and multiplier must be positive numbers.",
					"Invalid Input");
				return;
			}
			preprocess.setSelected(false); // should never be on when setSigma is
																			// called
			setSigma(sigma, true);
			setMultiplier(multiplier);
		}
	}

	private void arrangeDialogs() {
		Point loc = plugin.prefs.getPathWindowLocation();
		if (loc != null) pw.setLocation(loc);
		loc = plugin.prefs.getFillWindowLocation();
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
		if (getImagePlus(pane) == null) {
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

	/**
	 * Gets the Image associated with a view pane.
	 *
	 * @param pane the flag specifying the view either
	 *          {@link MultiDThreePanes.XY_PLANE},
	 *          {@link MultiDThreePanes.XZ_PLANE} or
	 *          {@link MultiDThreePanes.ZY_PLANE}.
	 * @return the image associate with the specified view, or null if the view is
	 *         not being displayed
	 */
	public ImagePlus getImagePlus(final int pane) {
		final StackWindow win = plugin.getWindow(pane);
		return (win == null) ? null : win.getImagePlus();
	}

	private boolean noPathsError() {
		final boolean noPaths = pathAndFillManager.size() == 0;
		if (noPaths) guiUtils.error("There are no traced paths.");
		return noPaths;
	}

	private void setPathListVisible(final boolean makeVisible,
		final boolean toFront)
	{
		assert SwingUtilities.isEventDispatchThread();
		if (makeVisible) {
			showOrHidePathList.setText("  Hide Path Manager");
			pw.setVisible(true);
			if (toFront) pw.toFront();
		}
		else {
			showOrHidePathList.setText("Show Path Manager");
			pw.setVisible(false);
		}
	}

	private void togglePathListVisibility() {
		assert SwingUtilities.isEventDispatchThread();
		synchronized (pw) {
			setPathListVisible(!pw.isVisible(), true);
		}
	}

	protected void setFillListVisible(final boolean makeVisible) {
		assert SwingUtilities.isEventDispatchThread();
		if (makeVisible) {
			showOrHideFillList.setText("  Hide Fill Manager");
			fw.setVisible(true);
			fw.toFront();
		}
		else {
			showOrHideFillList.setText("Show Fill Manager");
			fw.setVisible(false);
		}
	}

	protected void toggleFillListVisibility() {
		assert SwingUtilities.isEventDispatchThread();
		synchronized (fw) {
			setFillListVisible(!fw.isVisible());
		}
	}

	protected void thresholdChanged(final double f) {
		fw.thresholdChanged(f);
	}

	protected boolean nearbySlices() {
		assert SwingUtilities.isEventDispatchThread();
		return showPartsNearby.isSelected();
	}

	private JMenu helpMenu() {
		final JMenu helpMenu = new JMenu("Help");
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
						final String modKey = GuiUtils.modKey() + "+Shift";
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
		abortCurrentOperation();
		showStatus("Resetting");
		changeState(WAITING_TO_START_PATH);
	}

	protected void abortCurrentOperation() {//FIXME: MOVE TO Simple NeuriteTracer
		switch (currentState) {
			case (SEARCHING):
				updateStatusText("Cancelling path search...", true);
				plugin.cancelSearch(false);
				break;
			case (LOADING_FILTERED_IMAGE):
				updateStatusText("Unloading filtered image", true);
				if (filteredImgLoadingWorker != null) filteredImgLoadingWorker.cancel(true);
				plugin.doSearchOnFilteredData = false;
				plugin.tubularGeodesicsTracingEnabled = false;
				plugin.filteredData = null;
				changeState(WAITING_TO_START_PATH);
				break;
			case (CALCULATING_GAUSSIAN):
				updateStatusText("Cancelling Gaussian generation...", true);
				plugin.cancelGaussian();
				break;
			case (WAITING_FOR_SIGMA_POINT):
				showStatus("Sigma adjustment cancelled...");
				listener.restorePreSigmaState();
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
				plugin.discardFill(); // will change status
				break;
			case (FITTING_PATHS):
				showStatus("Fitting cancelled...");
				pw.cancelFit(true);
				break;
			case (PAUSED):
				showStatus("Tracing mode reinstated...");
				plugin.pause(false);
				break;
			case (EDITING_MODE):
				showStatus("Tracing mode reinstated...");
				plugin.enableEditMode(false);
				break;
			case (WAITING_FOR_SIGMA_CHOICE):
				showStatus("Close the sigma palette to abort sigma input...");
				break; // do nothing: Currently we have no control over the sigma palette window
			case (WAITING_TO_START_PATH):
			case (LOADING):
			case (SAVING):
			case (IMAGE_CLOSED):
				showStatus("Instruction ignored: No task to be aborted");
				break; // none of this states needs to be aborted
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
//			case LOGGING_POINTS:
//				return "LOGGING_POINTS";
//			case DISPLAY_EVS:
//				return "DISPLAY_EVS";
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
			case EDITING_MODE:
				return "EDITING_MODE";
			case PAUSED:
				return "PAUSED";
			case IMAGE_CLOSED:
				return "IMAGE_CLOSED";
			default:
				return "UNKNOWN";
		}
	}

	protected void togglePathsChoice() {
		assert SwingUtilities.isEventDispatchThread();
		if (showPathsAll.isSelected())
			showPathsSelected.setSelected(true);
		else
			showPathsAll.setSelected(true);
	}

	protected void enableFilteredImgTracing(final boolean enable) {
		if (plugin.isTracingOnFilteredImageAvailable()) {
			if (filteredImgParserChoice.getSelectedIndex() == 0) {
				plugin.doSearchOnFilteredData = enable;
			} else if (filteredImgParserChoice.getSelectedIndex() == 1) {
				plugin.tubularGeodesicsTracingEnabled = enable;
			}
			filteredImgActivateCheckbox.setSelected(enable);
		}
		else if (enable) {
			guiUtils.error("Filtered image has not yet been loaded. Please "
					+ (!SNT.fileAvailable(plugin.getFilteredImage()) ? "specify the file path of filtered image, then "
							: "")
					+ "initialize its parser.", "Filtered Image Unavailable");
			filteredImgActivateCheckbox.setSelected(false);
			plugin.doSearchOnFilteredData = false;
			updateFilteredFileField();
		}
	}

	protected void toggleFilteredImgTracing() {
		assert SwingUtilities.isEventDispatchThread();
		// Do nothing if we are not allowed to enable FilteredImgTracing
		if (!filteredImgActivateCheckbox.isEnabled()) {
			showStatus("Ignored: Filtered imaged not available");
			return;
		}
		enableFilteredImgTracing(!filteredImgActivateCheckbox.isSelected());
	}

	protected void toggleHessian() {
		assert SwingUtilities.isEventDispatchThread();
		if (ignorePreprocessEvents || !preprocess.isEnabled()) return;
		enableHessian(!preprocess.isSelected());
	}

	protected void enableHessian(final boolean enable) {
		if (enable) {
			preGaussianState = currentState;
		} else {
			changeState(preGaussianState);
		}
		plugin.enableHessian(enable);
		preprocess.setSelected(enable); // will not trigger ActionEvent
		showStatus("Hessisan "+ ((enable) ? "enabled" : "disabled"));
	}

	protected void togglePartsChoice() {
		assert SwingUtilities.isEventDispatchThread();
		if (showPartsNearby.isSelected())
			showPartsAll.setSelected(true);
		else
			showPartsNearby.setSelected(true);
	}

	private String hotKeyLabel(final String text, final String key) {
		final String label = text.replaceFirst(key, "<u><b>" + key + "</b></u>");
		return (text.startsWith("<HTML>")) ? label : "<HTML>" + label;
	}

	private class GuiListener implements ActionListener, ItemListener,
		SigmaPalette.SigmaPaletteListener, ImageListener
	{

		private final static String EDIT_SIGMA_MANUALLY = "Manually...";
		private final static String EDIT_SIGMA_VISUALLY = "Visually...";
		private int preSigmaPaletteState;

		public GuiListener(){
			ImagePlus.addImageListener(this);
		}

		/* ImageListener */
		@Override
		public void imageClosed(final ImagePlus imp) {
			// updateColorImageChoice(); //FIXME
			if (plugin.getImagePlus() == imp) changeState(
				NeuriteTracerResultsDialog.IMAGE_CLOSED);
		}

		@Override
		public void imageOpened(final ImagePlus imp) {}

		@Override
		public void imageUpdated(final ImagePlus imp) {}

		/* SigmaPaletteListener */
		@Override
		public void sigmaPaletteOKed(double newSigma, double newMultiplier) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					changeState(preSigmaPaletteState);
					setMultiplier(newMultiplier);
					setSigma(newSigma, true);
				}
			});
		}

		@Override
		public void sigmaPaletteCanceled() {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					restorePreSigmaState();
				}
			});
		}

		@Override
		public void itemStateChanged(final ItemEvent e) {
			assert SwingUtilities.isEventDispatchThread();

			final Object source = e.getSource();

			if (source == showPartsNearby) {
				plugin.justDisplayNearSlices(showPartsNearby.isSelected(), (int) nearbyFieldSpinner
					.getValue());
			}
			else if (source == showPartsAll) {
				plugin.justDisplayNearSlices(!showPartsAll.isSelected(), (int) nearbyFieldSpinner.getValue());
			}
			else if (source == useSnapWindow) {
				plugin.enableSnapCursor(useSnapWindow.isSelected());
			}
			else if (source == showPathsSelected) {
				plugin.setShowOnlySelectedPaths(showPathsSelected.isSelected());
			} else if (source == showPathsAll) {
				plugin.setShowOnlySelectedPaths(!showPathsAll.isSelected());
			}
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			assert SwingUtilities.isEventDispatchThread();

			final Object source = e.getSource();

			if (source == preprocess) {
				enableHessian(preprocess.isSelected());
			}
			else if (source == saveMenuItem && !noPathsError()) {

				final File suggestedFile = SNT.findClosestPair(plugin.prefs.getRecentFile(), "traces");
				final File saveFile = guiUtils.saveFile("Save traces as...", suggestedFile, Collections.singletonList(".traces"));
				if (saveFile == null) return; // user pressed cancel;
				if (saveFile.exists() && !guiUtils.getConfirmation("The file " +
						saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?", "Override traces file?")) {
						return;
				}

				showStatus("Saving traces to " + saveFile.getAbsolutePath());

				final int preSavingState = currentState;
				changeState(SAVING);
				try {
					pathAndFillManager.writeXML(saveFile.getAbsolutePath(), plugin.useCompressedXML);
				}
				catch (final IOException ioe) {
					showStatus("Saving failed.");
					guiUtils.error("Writing traces to '" + saveFile.getAbsolutePath() + "' failed. See Console for details.");
					changeState(preSavingState);
					ioe.printStackTrace();
					return;
				}
				changeState(preSavingState);
				showStatus("Saving completed.");

				plugin.unsavedPaths = false;

			}
			else if (source == loadTracesMenuItem || source == loadSWCMenuItem) {

				if (plugin.pathsUnsaved() && !guiUtils
						.getConfirmation("There are unsaved paths. Do you really want to load new traces?", "Warning"))
					return;
				final int preLoadingState = currentState;
				changeState(LOADING);
				if (source == loadTracesMenuItem )
					plugin.loadTracesFile();
				else
					plugin.loadSWCFile();
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

				final File suggestedFile = SNT.findClosestPair(plugin.loadedImageFile(), ".swc)");
				final File saveFile = guiUtils.saveFile("Export All Paths as SWC...", suggestedFile, Collections.singletonList(".swc"));
				if (saveFile == null) return; // user pressed cancel
				if (saveFile.exists()) {
					if (!guiUtils.getConfirmation("The file " +
						saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?", "Override SWC file?"))
						return;
				}
				final String savePath = saveFile.getAbsolutePath();
				SNT.log("Exporting paths to "+ saveFile);
				if (!pathAndFillManager.checkOKToWriteAllAsSWC(savePath)) return;
				pathAndFillManager.exportAllAsSWC(savePath);

			}
			else if (source == exportCSVMenuItem && !noPathsError()) {

				final File suggestedFile = SNT.findClosestPair(plugin.loadedImageFile(), ".csv)");
				final File saveFile = guiUtils.saveFile("Export All Paths as CSV...", suggestedFile, Collections.singletonList(".csv"));
				if (saveFile == null) return; // user pressed cancel
				if (saveFile.exists()) {
					if (!guiUtils.getConfirmation("The file " +
						saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?", "Override CSV file?"))
						return;
				}
				final String savePath = saveFile.getAbsolutePath();
				showStatus("Exporting as CSV to " + savePath);

				final int preExportingState = currentState;
				changeState(SAVING);
				// Export here...
				try {
					pathAndFillManager.exportToCSV(saveFile);
				}
				catch (final IOException ioe) {
					showStatus("Exporting failed.");
					guiUtils.error("Writing traces to '" + savePath + "' failed. See Console for details.");
					changeState(preExportingState);
					ioe.printStackTrace();
					return;
				}
				showStatus("Export complete.");
				changeState(preExportingState);

			}
			else if (source == measureMenuItem && !noPathsError()) {
				final PathAnalyzer pa = new PathAnalyzer(pathAndFillManager.getPathsFiltered());
				pa.setContext(plugin.getContext());
				pa.setTable(pw.getTable(), PathWindow.TABLE_TITLE);
				pa.run();
				return;
			}
			else if (source == strahlerMenuItem && !noPathsError()) {
				final StrahlerAnalyzer sa = new StrahlerAnalyzer(pathAndFillManager.getPathsFiltered());
				sa.setContext(plugin.getContext());
				sa.setTable(new DefaultGenericTable(), "SNT: Horton-Strahler Analysis (All Paths)");
				sa.run();
				return;
			}
			else if (source == loadLabelsMenuItem) {

				final File suggestedFile = SNT.findClosestPair(plugin.loadedImageFile(), ".labels)");
				final File saveFile = guiUtils.openFile("Select Labels File...", suggestedFile, Collections.singletonList("labels"));
				if (saveFile == null) return; // user pressed cancel;
				if (saveFile.exists()) {
					if (!guiUtils.getConfirmation("The file " +
						saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?", "Override SWC file?"))
						return;
				}
				if (saveFile != null) {
					plugin.loadLabelsFile(saveFile.getAbsolutePath());
					return;
				}

			}
			else if (source == abortButton) {

				abortCurrentOperation();
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
			else if (source == quitMenuItem) {

				exitRequested();

			}
			else if (source == showOrHidePathList) {

				togglePathListVisibility();

			}
			else if (source == showOrHideFillList) {

				toggleFillListVisibility();

			}
			else if (e.getActionCommand().equals(EDIT_SIGMA_MANUALLY)) {

				setSigmaFromUser();

			}
			else if (e.getActionCommand().equals(EDIT_SIGMA_VISUALLY)) {

				preSigmaPaletteState = currentState;
				changeState(WAITING_FOR_SIGMA_POINT);
				plugin.setCanvasLabelAllPanes("Choosing Sigma");
			}

			else if (source == colorImageChoice) {

				if (!ignoreColorImageChoiceEvents) checkForColorImageChange();

			}
		}

		private void restorePreSigmaState() {
			changeState(preSigmaPaletteState);
			plugin.setCanvasLabelAllPanes(null);
		}
	}

}