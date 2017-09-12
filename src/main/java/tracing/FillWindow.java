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
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import ij.IJ;
import ij.io.SaveDialog;
import tracing.gui.GuiUtils;

@SuppressWarnings("serial")
public class FillWindow extends JFrame
		implements PathAndFillListener, ActionListener, ItemListener, FillerProgressCallback {

	protected SimpleNeuriteTracer plugin;
	protected PathAndFillManager pathAndFillManager;

	public FillWindow(final PathAndFillManager pathAndFillManager, final SimpleNeuriteTracer plugin) {
		this(pathAndFillManager, plugin, 200, 60);
	}

	protected JScrollPane scrollPane;

	protected JList<String> fillList;
	protected DefaultListModel<String> listModel;

	protected JButton deleteFills;
	protected JButton reloadFill;

	protected JPanel fillControlPanel;

	protected JLabel fillStatus;

	protected float maxThresholdValue = 0;

	protected JTextField thresholdField;
	protected JLabel maxThreshold;
	protected JButton setThreshold;
	protected JButton setMaxThreshold;

	protected JButton view3D;
	protected JCheckBox maskNotReal;
	protected JCheckBox transparent;

	protected boolean currentlyFilling = true;
	protected JButton pauseOrRestartFilling;

	protected JButton saveFill;
	protected JButton discardFill;

	protected JButton exportAsCSV;

	public void setEnabledWhileFilling() {
		assert SwingUtilities.isEventDispatchThread();
		fillList.setEnabled(false);
		deleteFills.setEnabled(false);
		reloadFill.setEnabled(false);
		fillStatus.setEnabled(true);
		thresholdField.setEnabled(true);
		maxThreshold.setEnabled(true);
		setThreshold.setEnabled(true);
		setMaxThreshold.setEnabled(true);
		view3D.setEnabled(true);
		maskNotReal.setEnabled(true);
		transparent.setEnabled(true);
		pauseOrRestartFilling.setEnabled(true);
		saveFill.setEnabled(false);
		discardFill.setEnabled(true);
	}

	public void setEnabledWhileNotFilling() {
		assert SwingUtilities.isEventDispatchThread();
		fillList.setEnabled(true);
		deleteFills.setEnabled(true);
		reloadFill.setEnabled(true);
		fillStatus.setEnabled(true);
		thresholdField.setEnabled(false);
		maxThreshold.setEnabled(false);
		setThreshold.setEnabled(false);
		setMaxThreshold.setEnabled(false);
		view3D.setEnabled(false);
		maskNotReal.setEnabled(false);
		transparent.setEnabled(false);
		pauseOrRestartFilling.setEnabled(false);
		saveFill.setEnabled(false);
		discardFill.setEnabled(false);
	}

	public void setEnabledNone() {
		assert SwingUtilities.isEventDispatchThread();
		fillList.setEnabled(false);
		deleteFills.setEnabled(false);
		reloadFill.setEnabled(false);
		fillStatus.setEnabled(false);
		thresholdField.setEnabled(false);
		maxThreshold.setEnabled(false);
		setThreshold.setEnabled(false);
		setMaxThreshold.setEnabled(false);
		view3D.setEnabled(false);
		maskNotReal.setEnabled(false);
		transparent.setEnabled(false);
		pauseOrRestartFilling.setEnabled(false);
		saveFill.setEnabled(false);
		discardFill.setEnabled(false);
	}

	public FillWindow(final PathAndFillManager pathAndFillManager, final SimpleNeuriteTracer plugin, final int x,
			final int y) {
		super("All Fills");
		assert SwingUtilities.isEventDispatchThread();

		new ClarifyingKeyListener().addKeyAndContainerListenerRecursively(this);

		this.plugin = plugin;
		this.pathAndFillManager = pathAndFillManager;
		setBounds(x, y, 350, 400);

		setLayout(new GridBagLayout());

		final GridBagConstraints c = new GridBagConstraints();

		listModel = new DefaultListModel<>();
		fillList = new JList<>(listModel);

		scrollPane = new JScrollPane();
		scrollPane.getViewport().add(fillList);

		c.gridx = 0;
		c.gridy = 0;
		c.insets = new Insets(8, 8, 1, 8);
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;

		add(scrollPane, c);

		c.weightx = 0;
		c.weighty = 0;

		{
			final JPanel fillListCommandsPanel = new JPanel();
			fillListCommandsPanel.setLayout(new BorderLayout());

			deleteFills = new JButton("Delete Fill(s)");
			deleteFills.addActionListener(this);
			fillListCommandsPanel.add(deleteFills, BorderLayout.WEST);

			reloadFill = new JButton("Reload Fill");
			reloadFill.addActionListener(this);
			fillListCommandsPanel.add(reloadFill, BorderLayout.EAST);

			c.insets = new Insets(1, 8, 8, 8);
			c.gridx = 0;
			++c.gridy;

			add(fillListCommandsPanel, c);
		}

		{

			final JPanel fillingOptionsPanel = new JPanel();
			fillingOptionsPanel.setLayout(new GridBagLayout());
			final GridBagConstraints cf = new GridBagConstraints();
			cf.gridx = 0;
			cf.gridy = 0;
			cf.gridwidth = 2;
			cf.anchor = GridBagConstraints.LINE_START;
			cf.fill = GridBagConstraints.HORIZONTAL;
			cf.insets = new Insets(0, 0, 0, 0);

			fillStatus = new JLabel("(Not filling at the moment.)");
			cf.gridwidth = 3;
			cf.fill = GridBagConstraints.REMAINDER;
			fillingOptionsPanel.add(fillStatus, cf);
			++cf.gridy;

			cf.gridx = 0;
			cf.gridwidth = 1;
			cf.fill = GridBagConstraints.NONE;
			fillingOptionsPanel.add(new JLabel("Threshold:"), cf);
			thresholdField = new JTextField("", 5);
			thresholdField.addActionListener(this);
			cf.gridx = 1;
			cf.gridwidth = 1;
			cf.fill = GridBagConstraints.NONE;
			fillingOptionsPanel.add(thresholdField, cf);

			maxThreshold = new JLabel("(Max. not yet determined)", SwingConstants.LEFT);
			cf.gridx = 2;
			cf.fill = GridBagConstraints.REMAINDER;
			fillingOptionsPanel.add(maxThreshold, cf);
			++cf.gridy;

			setThreshold = GuiUtils.smallButton("Set");
			setThreshold.addActionListener(this);
			cf.gridx = 1;
			cf.gridwidth = 1;
			cf.fill = GridBagConstraints.NONE;
			fillingOptionsPanel.add(setThreshold, cf);
			setMaxThreshold = GuiUtils.smallButton("Set Max");
			setMaxThreshold.setEnabled(false);
			setMaxThreshold.addActionListener(this);
			cf.gridx = 2;
			cf.fill = GridBagConstraints.REMAINDER;
			fillingOptionsPanel.add(setMaxThreshold, cf);
			cf.gridy++;

			transparent = new JCheckBox("Transparent fill display (slow!)");
			transparent.addItemListener(this);
			cf.anchor = GridBagConstraints.LINE_START;
			cf.gridx = 0;
			cf.insets = new Insets(0, 0, 0, 0);
			cf.gridwidth = 3;
			cf.fill = GridBagConstraints.REMAINDER;
			cf.gridy++;
			fillingOptionsPanel.add(transparent, cf);

			view3D = new JButton("Create Image Stack from Fill");
			view3D.addActionListener(this);
			cf.insets = new Insets(12, 0, 0, 0);
			cf.gridy++;
			fillingOptionsPanel.add(view3D, cf);

			maskNotReal = new JCheckBox("Create as mask");
			maskNotReal.addItemListener(this);
			cf.insets = new Insets(0, 0, 0, 0);
			cf.gridy++;
			fillingOptionsPanel.add(maskNotReal, cf);

			c.gridx = 0;
			++c.gridy;
			c.insets = new Insets(8, 8, 8, 8);
			c.fill = GridBagConstraints.NONE;
			c.anchor = GridBagConstraints.LINE_START;
			add(fillingOptionsPanel, c);

			{
				fillControlPanel = new JPanel();
				fillControlPanel.setLayout(new FlowLayout());

				pauseOrRestartFilling = new JButton("Pause");
				currentlyFilling = true;
				pauseOrRestartFilling.addActionListener(this);
				fillControlPanel.add(pauseOrRestartFilling);

				saveFill = new JButton("Save Fill");
				saveFill.addActionListener(this);
				fillControlPanel.add(saveFill);

				discardFill = new JButton("Cancel Fill");
				discardFill.addActionListener(this);
				fillControlPanel.add(discardFill);

				c.gridx = 0;
				++c.gridy;
				c.fill = GridBagConstraints.HORIZONTAL;
				c.anchor = GridBagConstraints.CENTER;

				add(fillControlPanel, c);
			}

			++c.gridy;
			c.insets = new Insets(0, 0, 0, 0);
			c.fill = GridBagConstraints.NONE;
			exportAsCSV = new JButton("Export as CSV");
			exportAsCSV.addActionListener(this);
			add(exportAsCSV, c);
		}
	}

	@Override
	public void setPathList(final String[] pathList, final Path justAdded, final boolean expandAll) {
	}

	@Override
	public void setFillList(final String[] newList) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				listModel.removeAllElements();
				for (int i = 0; i < newList.length; ++i)
					listModel.addElement(newList[i]);
			}
		});
	}

	@Override
	public void setSelectedPaths(final HashSet<Path> selectedPathSet, final Object source) {
		// This dialog doesn't deal with paths, so ignore this.
	}

	@Override
	public void actionPerformed(final ActionEvent ae) {
		assert SwingUtilities.isEventDispatchThread();

		final Object source = ae.getSource();

		if (source == deleteFills) {

			final int[] selectedIndices = fillList.getSelectedIndices();
			if (selectedIndices.length < 1) {
				SNT.error("No fill was selected for deletion.");
				return;
			}
			pathAndFillManager.deleteFills(selectedIndices);
			plugin.repaintAllPanes();

		} else if (source == reloadFill) {

			final int[] selectedIndices = fillList.getSelectedIndices();
			if (selectedIndices.length != 1) {
				SNT.error("You must have a single fill selected in order to reload.");
				return;
			}
			pathAndFillManager.reloadFill(selectedIndices[0]);

		} else if (source == setMaxThreshold) {

			plugin.setFillThreshold(maxThresholdValue);

		} else if (source == setThreshold || source == thresholdField) {

			try {
				final double t = Double.parseDouble(thresholdField.getText());
				if (t < 0) {
					SNT.error("The fill threshold cannot be negative.");
					return;
				}
				plugin.setFillThreshold(t);
			} catch (final NumberFormatException nfe) {
				SNT.error("The threshold '" + thresholdField.getText() + "' wasn't a valid number.");
				return;
			}

		} else if (source == discardFill) {

			plugin.discardFill();

		} else if (source == saveFill) {

			plugin.saveFill();

		} else if (source == pauseOrRestartFilling) {

			plugin.pauseOrRestartFilling();

		} else if (source == view3D) {

			plugin.viewFillIn3D(!maskNotReal.isSelected());

		} else if (source == exportAsCSV) {

			final SaveDialog sd = new SaveDialog("Export fill summary as...", "fills", ".csv");

			if (sd.getFileName() == null) {
				return;
			}

			final File saveFile = new File(sd.getDirectory(), sd.getFileName());
			if (saveFile.exists()) {
				if (!IJ.showMessageWithCancel("Export data...",
						"The file " + saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Exporting CSV data to " + saveFile.getAbsolutePath());

			try {
				pathAndFillManager.exportFillsAsCSV(saveFile);

			} catch (final IOException ioe) {
				SNT.error("Saving to " + saveFile.getAbsolutePath() + " failed");
				return;
			}

		} else {
			SNT.error("BUG: FillWindow received an event from an unknown source.");
		}

	}

	@Override
	public void itemStateChanged(final ItemEvent ie) {
		assert SwingUtilities.isEventDispatchThread();
		if (ie.getSource() == transparent)
			plugin.setFillTransparent(transparent.isSelected());
	}

	protected DecimalFormat df4 = new DecimalFormat("#.0000");

	public void thresholdChanged(final double f) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				assert SwingUtilities.isEventDispatchThread();
				thresholdField.setText(df4.format(f));
			}
		});
	}

	@Override
	public void maximumDistanceCompletelyExplored(final SearchThread source, final float f) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				maxThreshold.setText("(Max: " + df4.format(f) + ")");
				maxThresholdValue = f;
				setMaxThreshold.setEnabled(true);
			}
		});
	}

	@Override
	public void pointsInSearch(final SearchInterface source, final int inOpen, final int inClosed) {
		// Do nothing...
	}

	@Override
	public void finished(final SearchInterface source, final boolean success) {
		// Do nothing...
	}

	@Override
	public void threadStatus(final SearchInterface source, final int currentStatus) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
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
			}
		});
	}

}
