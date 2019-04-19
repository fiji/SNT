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
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import ij.ImagePlus;
import net.imagej.ImageJ;
import tracing.gui.GuiUtils;

/**
 * Creates the "Fill Manager" Dialog.
 *
 * @author Tiago Ferreira
 */
public class FillManagerUI extends JDialog implements PathAndFillListener,
	ActionListener, ItemListener, FillerProgressCallback
{

	private static final long serialVersionUID = 1L;
	private final SimpleNeuriteTracer plugin;
	private final PathAndFillManager pathAndFillManager;
	private JScrollPane scrollPane;
	private final JList<String> fillList;
	private final DefaultListModel<String> listModel;
	private JButton deleteFills;
	private JButton reloadFill;
	private JPanel fillControlPanel;
	protected JLabel fillStatus;
	private float maxThresholdValue = 0;
	private JTextField thresholdField;
	private JLabel maxThreshold;
	private JLabel currentThreshold;
	private JButton setThreshold;
	private JButton setMaxThreshold;
	private JButton view3D;
	private JPopupMenu viewFillsMenu;
	private JCheckBox transparent;
	protected JButton pauseOrRestartFilling;
	private JButton saveFill;
	private JButton discardFill;
	private JButton exportAsCSV;
	private JRadioButton manualRButton;
	private JRadioButton maxRButton;

	private final GuiUtils gUtils;

	private final int MARGIN = 10;

	/**
	 * Instantiates a new Fill Manager Dialog
	 *
	 * @param plugin the the {@link SimpleNeuriteTracer} instance to be associated
	 *               with this FillManager. It is assumed that its {@link SNTUI} is
	 *               available.
	 */
	public FillManagerUI(final SimpleNeuriteTracer plugin) {
		super(plugin.getUI(), "Fill Manager");

		this.plugin = plugin;
		pathAndFillManager = plugin.getPathAndFillManager();
		pathAndFillManager.addPathAndFillListener(this);

		gUtils = new GuiUtils(this);
		listModel = new DefaultListModel<>();
		fillList = new JList<>(listModel);

		assert SwingUtilities.isEventDispatchThread();

		fillList.setVisibleRowCount(5);
		fillList.setPrototypeCellValue("Fill (90) from Path (90)");
		setLocationRelativeTo(plugin.getUI());
		setLayout(new GridBagLayout());
		final GridBagConstraints c = GuiUtils.defaultGbc();

		{
			scrollPane = new JScrollPane();
			scrollPane.getViewport().add(fillList);
			final JPanel listPanel = new JPanel(new BorderLayout());
			listPanel.add(scrollPane, BorderLayout.CENTER);
			add(listPanel, c);
			++c.gridy;
		}

		{
			deleteFills = new JButton("Delete Fill(s)");
			deleteFills.addActionListener(this);
			reloadFill = new JButton("Reload Fill");
			reloadFill.addActionListener(this);
			final JPanel fillListCommandsPanel = new JPanel(new BorderLayout());
			fillListCommandsPanel.add(deleteFills, BorderLayout.WEST);
			fillListCommandsPanel.add(reloadFill, BorderLayout.EAST);
			add(fillListCommandsPanel, c);
			++c.gridy;
		}

		GuiUtils.addSeparator((JComponent) getContentPane(),
			" Distance Threshold for Fill Search:", true, c);
		++c.gridy;

		{
			final JPanel distancePanel = new JPanel(new GridBagLayout());
			final GridBagConstraints gdb = GuiUtils.defaultGbc();
			final JRadioButton cursorRButton = new JRadioButton(
				"Set by clicking on traced strucure (preferred)");

			final JPanel t1Panel = leftAlignedPanel();
			t1Panel.add(cursorRButton);
			distancePanel.add(t1Panel, gdb);
			++gdb.gridy;

			manualRButton = new JRadioButton("Specify manually:");
			thresholdField = new JTextField("", 6);
			setThreshold = GuiUtils.smallButton("Apply");
			setThreshold.addActionListener(this);
			final JPanel t2Panel = leftAlignedPanel();
			t2Panel.add(manualRButton);
			t2Panel.add(thresholdField);
			t2Panel.add(setThreshold);
			distancePanel.add(t2Panel, gdb);
			++gdb.gridy;

			maxRButton = new JRadioButton("Use explored maximum");
			setMaxThreshold = GuiUtils.smallButton("Apply");
			setMaxThreshold.setEnabled(false);
			setMaxThreshold.addActionListener(this);
			final JPanel t3Panel = leftAlignedPanel();
			t3Panel.add(maxRButton);
			t3Panel.add(setMaxThreshold);
			distancePanel.add(t3Panel, gdb);
			add(distancePanel, c);
			++c.gridy;

			final ButtonGroup group = new ButtonGroup();
			group.add(maxRButton);
			group.add(manualRButton);
			group.add(cursorRButton);
			final RadioGroupListener listener = new RadioGroupListener();
			cursorRButton.addActionListener(listener);
			manualRButton.addActionListener(listener);
			maxRButton.addActionListener(listener);
			cursorRButton.setSelected(true);
			thresholdField.setEnabled(false);
			setThreshold.setEnabled(false);
			setMaxThreshold.setEnabled(false);
		}

		GuiUtils.addSeparator((JComponent) getContentPane(), " Search Status:",
			true, c);
		++c.gridy;

		{
			currentThreshold = GuiUtils.leftAlignedLabel(
				"No Pahs are currently being filled...", false);
			maxThreshold = GuiUtils.leftAlignedLabel(
				"This message will be updated once a Fill-out", false);
			fillStatus = GuiUtils.leftAlignedLabel(
				"command is run from the Path Manager...", false);
			final int storedPady = c.ipady;
			final Insets storedInsets = c.insets;
			c.ipady = 0;
			c.insets = new Insets(0, MARGIN, 0, MARGIN);
			add(currentThreshold, c);
			++c.gridy;
			add(maxThreshold, c);
			++c.gridy;
			add(fillStatus, c);
			++c.gridy;
			c.ipady = storedPady;
			c.insets = storedInsets;
		}

		GuiUtils.addSeparator((JComponent) getContentPane(), " Rendering Options:",
			true, c);
		++c.gridy;

		{
			transparent = new JCheckBox(
				" Transparent overlay (may slow down filling)");
			transparent.addItemListener(this);
			final JPanel transparencyPanel = leftAlignedPanel();
			transparencyPanel.add(transparent);
			add(transparencyPanel, c);
			c.gridy++;
		}

		GuiUtils.addSeparator((JComponent) getContentPane(),
			" Filler Thread Controls:", true, c);
		++c.gridy;

		{
			pauseOrRestartFilling = new JButton("Pause");
			pauseOrRestartFilling.addActionListener(this);
			discardFill = new JButton("Stop");
			discardFill.addActionListener(this);
			saveFill = new JButton("Stash Progress");
			saveFill.addActionListener(this);
			fillControlPanel = centerAlignedPanel();
			fillControlPanel.add(pauseOrRestartFilling);
			fillControlPanel.add(discardFill);
			fillControlPanel.add(saveFill);
			add(fillControlPanel, c);
			++c.gridy;
		}

		GuiUtils.addSeparator((JComponent) getContentPane(), " Export Fill(s):",
			true, c);
		++c.gridy;

		{
			exportAsCSV = new JButton("CSV Summary...");
			exportAsCSV.addActionListener(this);
			assembleViewFillsMenu();
			view3D = new JButton("Image Stack...");
			view3D.addMouseListener(new MouseAdapter() {

				@Override
				public void mousePressed(final MouseEvent e) {
					viewFillsMenu.show(e.getComponent(), e.getX(), e.getY());
				}
			});
			final JPanel exportPanel = centerAlignedPanel();
			exportPanel.add(exportAsCSV);
			exportPanel.add(view3D);
			add(exportPanel, c);
			c.gridy++;
		}

		pack();
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); // prevent
																																		// closing

	}

	private JPanel leftAlignedPanel() {
		return getPanel(FlowLayout.LEADING);
	}

	private JPanel centerAlignedPanel() {
		return getPanel(FlowLayout.CENTER);
	}

	private JPanel getPanel(final int alignment) {
		final JPanel panel = new JPanel(new FlowLayout(alignment));
		panel.setBorder(BorderFactory.createEmptyBorder(0, MARGIN, 0, MARGIN));
		return panel;
	}

	protected void setEnabledWhileFilling() {
		assert SwingUtilities.isEventDispatchThread();
		fillList.setEnabled(false);
		deleteFills.setEnabled(false);
		reloadFill.setEnabled(false);
		fillStatus.setEnabled(true);
		manualRButton.setEnabled(true);
		maxThreshold.setEnabled(maxRButton.isSelected());
		currentThreshold.setEnabled(true);
		fillStatus.setEnabled(true);
		maxRButton.setEnabled(maxRButton.isSelected());
		view3D.setEnabled(true);
		exportAsCSV.setEnabled(true);
		transparent.setEnabled(true);
		pauseOrRestartFilling.setEnabled(true);
		saveFill.setEnabled(false);
		discardFill.setEnabled(true);
	}

	protected void setEnabledWhileNotFilling() {
		assert SwingUtilities.isEventDispatchThread();
		fillList.setEnabled(true);
		deleteFills.setEnabled(true);
		reloadFill.setEnabled(true);
		fillStatus.setEnabled(true);
		manualRButton.setEnabled(false);
		maxThreshold.setEnabled(false);
		currentThreshold.setEnabled(false);
		fillStatus.setEnabled(false);
		maxRButton.setEnabled(false);
		view3D.setEnabled(false);
		exportAsCSV.setEnabled(false);
		transparent.setEnabled(false);
		pauseOrRestartFilling.setEnabled(false);
		saveFill.setEnabled(false);
		discardFill.setEnabled(false);
	}

	protected void setEnabledNone() {
		assert SwingUtilities.isEventDispatchThread();
		fillList.setEnabled(false);
		deleteFills.setEnabled(false);
		reloadFill.setEnabled(false);
		fillStatus.setEnabled(false);
		manualRButton.setEnabled(false);
		maxThreshold.setEnabled(false);
		currentThreshold.setEnabled(false);
		fillStatus.setEnabled(false);
		maxRButton.setEnabled(false);
		view3D.setEnabled(false);
		exportAsCSV.setEnabled(false);
		transparent.setEnabled(false);
		pauseOrRestartFilling.setEnabled(false);
		saveFill.setEnabled(false);
		discardFill.setEnabled(false);
	}

	/* (non-Javadoc)
	 * @see tracing.PathAndFillListener#setPathList(java.lang.String[], tracing.Path, boolean)
	 */
	@Override
	public void setPathList(final String[] pathList, final Path justAdded,
		final boolean expandAll)
	{}

	/* (non-Javadoc)
	 * @see tracing.PathAndFillListener#setFillList(java.lang.String[])
	 */
	@Override
	public void setFillList(final String[] newList) {
		SwingUtilities.invokeLater(() -> {
			listModel.removeAllElements();
			for (String s : newList) listModel.addElement(s);
		});
	}

	/* (non-Javadoc)
	 * @see tracing.PathAndFillListener#setSelectedPaths(java.util.HashSet, java.lang.Object)
	 */
	@Override
	public void setSelectedPaths(final HashSet<Path> selectedPathSet,
		final Object source)
	{
		// This dialog doesn't deal with paths, so ignore this.
	}

	/* (non-Javadoc)
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed(final ActionEvent ae) {
		assert SwingUtilities.isEventDispatchThread();

		final Object source = ae.getSource();

		if (source == deleteFills) {

			final int[] selectedIndices = fillList.getSelectedIndices();
			if (selectedIndices.length < 1) {
				gUtils.error("No fill was selected for deletion.");
				return;
			}
			pathAndFillManager.deleteFills(selectedIndices);
			plugin.updateAllViewers();

		}
		else if (source == reloadFill) {

			final int[] selectedIndices = fillList.getSelectedIndices();
			if (selectedIndices.length != 1) {
				gUtils.error(
					"You must have a single fill selected in order to reload.");
				return;
			}
			pathAndFillManager.reloadFill(selectedIndices[0]);

		}
		else if (source == setMaxThreshold) {

			plugin.setFillThreshold(maxThresholdValue);

		}
		else if (source == setThreshold || source == thresholdField) {

			try {
				final double t = Double.parseDouble(thresholdField.getText());
				if (t < 0) {
					gUtils.error("The fill threshold cannot be negative.");
					return;
				}
				plugin.setFillThreshold(t);
			}
			catch (final NumberFormatException nfe) {
				gUtils.error("The threshold '" + thresholdField.getText() +
					"' wasn't a valid number.");
				return;
			}

		}
		else if (source == discardFill) {

			plugin.discardFill();

		}
		else if (source == saveFill) {

			plugin.saveFill();

		}
		else if (source == pauseOrRestartFilling) {

			plugin.pauseOrRestartFilling();

		}
		else if (source == exportAsCSV) {

			final File saveFile = plugin.getUI().saveFile("Export CSV Summary...", null, ".csv");
			if (saveFile == null) return; // user pressed cancel;
			plugin.getUI().showStatus("Exporting CSV data to " + saveFile
				.getAbsolutePath(), false);
			try {
				pathAndFillManager.exportFillsAsCSV(saveFile);
			}
			catch (final IOException ioe) {
				gUtils.error("Saving to " + saveFile.getAbsolutePath() +
					" failed. See console for details");
				SNT.error("IO Error", ioe);
				return;
			}
			plugin.getUI().showStatus("Done... ", true);
		}
		else {
			SNT.error("BUG: FillWindow received an event from an unknown source.");
		}

	}

	private void assembleViewFillsMenu() {
		viewFillsMenu = new JPopupMenu();
		viewFillsMenu.add(new JMenuItem(new AbstractAction(
			"As Grayscale Image...")
		{

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				plugin.viewFillIn3D(false);
			}
		}));
		viewFillsMenu.add(new JMenuItem(new AbstractAction("As Binary Mask...") {

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				plugin.viewFillIn3D(true);
			}
		}));
	}

	/* (non-Javadoc)
	 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
	 */
	@Override
	public void itemStateChanged(final ItemEvent ie) {
		assert SwingUtilities.isEventDispatchThread();
		if (ie.getSource() == transparent) plugin.setFillTransparent(transparent
			.isSelected());
	}

	protected void thresholdChanged(final double f) {
		SwingUtilities.invokeLater(() -> {
			assert SwingUtilities.isEventDispatchThread();
			final String value = SNT.formatDouble(f, 3);
			thresholdField.setText(SNT.formatDouble(f, 3));
			currentThreshold.setText("Current threshold distance: " + value);
		});
	}

	/* (non-Javadoc)
	 * @see tracing.FillerProgressCallback#maximumDistanceCompletelyExplored(tracing.SearchThread, float)
	 */
	@Override
	public void maximumDistanceCompletelyExplored(final SearchThread source,
		final float f)
	{
		SwingUtilities.invokeLater(() -> {
			maxThreshold.setText("Max. explored distance: " + SNT.formatDouble(f, 3));
			maxThresholdValue = f;
		});
	}

	/* (non-Javadoc)
	 * @see tracing.SearchProgressCallback#pointsInSearch(tracing.SearchInterface, int, int)
	 */
	@Override
	public void pointsInSearch(final SearchInterface source, final int inOpen,
		final int inClosed)
	{
		// Do nothing...
	}

	/* (non-Javadoc)
	 * @see tracing.SearchProgressCallback#finished(tracing.SearchInterface, boolean)
	 */
	@Override
	public void finished(final SearchInterface source, final boolean success) {
		// Do nothing...
	}

	/* (non-Javadoc)
	 * @see tracing.SearchProgressCallback#threadStatus(tracing.SearchInterface, int)
	 */
	@Override
	public void threadStatus(final SearchInterface source,
		final int currentStatus)
	{
		SwingUtilities.invokeLater(() -> {
			switch (currentStatus) {
				case SearchThread.STOPPING:
					pauseOrRestartFilling.setText("Stopped");
					pauseOrRestartFilling.setEnabled(false);
					saveFill.setEnabled(false);

					break;
				case SearchThread.PAUSED:
					pauseOrRestartFilling.setText("Continue");
					saveFill.setEnabled(true);
					break;
				case SearchThread.RUNNING:
					pauseOrRestartFilling.setText("Pause");
					saveFill.setEnabled(false);
					break;
			}
			fillControlPanel.doLayout();
		});
	}

	protected void showMouseThreshold(final float t) {
		SwingUtilities.invokeLater(() -> {
			String newStatus = null;
			if (t < 0) {
				newStatus = "Cursor position: Not reached by search yet";
			}
			else {
				newStatus = "Cursor position: Distance from path is " + SNT
					.formatDouble(t, 3);
			}
			fillStatus.setText(newStatus);
		});
	}

	/* IDE debug method */
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		final ImagePlus imp = new ImagePlus();
		final SimpleNeuriteTracer snt = new SimpleNeuriteTracer(ij.context(), imp);
		final FillManagerUI fm = new FillManagerUI(snt);
		fm.setVisible(true);
	}

	private class RadioGroupListener implements ActionListener {

		@Override
		public void actionPerformed(final ActionEvent e) {
			setThreshold.setEnabled(manualRButton.isSelected());
			thresholdField.setEnabled(manualRButton.isSelected());
			setMaxThreshold.setEnabled(maxRButton.isSelected());
		}
	}
}
