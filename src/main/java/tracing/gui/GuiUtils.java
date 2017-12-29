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

package tracing.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.colorchooser.AbstractColorChooserPanel;

import org.scijava.ui.awt.AWTWindows;
import org.scijava.ui.swing.SwingDialog;
import org.scijava.ui.swing.widget.SwingColorWidget;
import org.scijava.util.PlatformUtils;

import ij.ImagePlus;
import tracing.SNT;

public class GuiUtils {

	private final Component parent;

	public GuiUtils(final Component parent) {
		this.parent = parent;
	}

	public GuiUtils() {
		this(null);
	}

	public void error(final String msg) {
		error(msg, "SNT v" + SNT.VERSION);
	}

	public void error(final String msg, final String title) {
		centeredDialog(msg, title, JOptionPane.ERROR_MESSAGE);
	}

	public void tempMsg(final String msg, final boolean snapToParent) {
		tempMsg(msg, -1, -1, snapToParent);
	}

	public void tempMsg(final String msg, final Point location) {
		tempMsg(msg, location.x, location.y, true);
	}

	private void tempMsg(final String msg, final int x, final int y,
		final boolean snapToParent)
	{
		assert SwingUtilities.isEventDispatchThread();
		final JDialog dialog = new JDialog();
		dialog.setUndecorated(true);
		dialog.setModal(false);
		dialog.getContentPane().setBackground(Color.WHITE);
		dialog.setBackground(Color.WHITE);
		final JLabel label = new JLabel(msg);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(new EmptyBorder(10, 10, 10, 10));
		label.setBackground(Color.WHITE);
		dialog.add(label);
		dialog.pack();
		if (snapToParent && parent != null) {
			dialog.setPreferredSize(new Dimension(parent.getWidth(), dialog
				.getHeight()));
			final Point p = new Point(parent.getWidth() / 2 - dialog.getWidth() / 2,
				parent.getHeight() / 2 - dialog.getHeight() / 2);
			dialog.setLocation(p.x + parent.getX(), p.y + parent.getY());
		}
		else if (x != -1 && y != -1) {
			dialog.setLocation(x, y);
		}
		else {
			dialog.setLocationRelativeTo(null);
		}
		final Timer timer = new Timer(2500, new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				dialog.setVisible(false);
				dialog.dispose();
			}
		});
		timer.setRepeats(false);
		timer.start();
		dialog.setAlwaysOnTop(true);
		dialog.toFront();
		dialog.setVisible(true);
	}

	public int yesNoDialog(final String msg, final String title) {
		final JOptionPane optionPane = new JOptionPane(getLabel(msg), JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
		final JDialog d = optionPane.createDialog(title);
		d.setModalityType(ModalityType.APPLICATION_MODAL);
		if (parent != null) {
			AWTWindows.centerWindow(parent.getBounds(), d);
			// we could also use d.setLocationRelativeTo(parent);
		}
		d.setVisible(true);
		d.dispose();
		final Object result = optionPane.getValue();
		if (result == null || (!(result instanceof Integer))) return SwingDialog.UNKNOWN_OPTION;
		return (Integer) result;
	}

	public boolean getConfirmation(final String msg, final String title) {
		return (yesNoDialog(msg, title) == JOptionPane.YES_OPTION);
	}

	public String getString(final String promptMsg, final String promptTitle,
		final String defaultValue)
	{
		return (String) getObj(promptMsg, promptTitle, defaultValue);
	}

	public Color getColor(final String title, final Color defaultValue) {
		return SwingColorWidget.showColorDialog(parent, title, defaultValue);
	}

	/**
	 * Simplified color chooser.
	 *
	 * @param title the title of the chooser dialog
	 * @param defaultValue the initial color set in the chooser
	 * @param panes the panes a list of strings specifying which tabs should be
	 *          displayed. In most platforms this includes: "Swatches", "HSB" and
	 *          "RGB". Note that e.g., the GTK L&F may only include the default
	 *          GtkColorChooser pane
	 * @return the color
	 */
	public Color getColor(final String title, final Color defaultValue,
		final String... panes)
	{

		assert SwingUtilities.isEventDispatchThread();

		final JColorChooser chooser = new JColorChooser(defaultValue != null
			? defaultValue : Color.WHITE);

		// remove preview pane
		chooser.setPreviewPanel(new JPanel());

		// remove spurious panes
		List<String> allowedPanels = new ArrayList<>();
		if (panes != null) {
			allowedPanels = Arrays.asList(panes);
			for (final AbstractColorChooserPanel accp : chooser.getChooserPanels()) {
				if (!allowedPanels.contains(accp.getDisplayName()) && chooser
					.getChooserPanels().length > 1) chooser.removeChooserPanel(accp);
			}
		}

		class ColorTracker implements ActionListener {

			private final JColorChooser chooser;
			private Color color;

			public ColorTracker(final JColorChooser c) {
				chooser = c;
			}

			@Override
			public void actionPerformed(final ActionEvent e) {
				color = chooser.getColor();
			}

			public Color getColor() {
				return color;
			}
		}

		final ColorTracker ok = new ColorTracker(chooser);
		final JDialog dialog = JColorChooser.createDialog(parent, title, true,
			chooser, ok, null);
		dialog.setVisible(true);
		dialog.toFront();
		return ok.getColor();
	}

	public Double getDouble(final String promptMsg, final String promptTitle,
		final double defaultValue)
	{
		try {
			return Double.parseDouble((String) getObj(promptMsg, promptTitle,
				defaultValue));
		}
		catch (final NullPointerException ignored) {
			return null; // user pressed cancel
		}
		catch (final NumberFormatException ignored) {
			return Double.NaN; // invalid user input
		}
	}

	public File saveFile(final String title, final File file) {
		final JFileChooser chooser = fileChooser(title, file,
			JFileChooser.FILES_ONLY);
		if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		return null;
	}

	public File openFile(final String title, final File file) {
		final JFileChooser chooser = fileChooser(title, file,
			JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		return null;
	}

	public File chooseDirectory(final String title, final File file) {
		final JFileChooser chooser = fileChooser(title, file,
			JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		return null;
	}

	private JFileChooser fileChooser(final String title, final File file,
		final int type)
	{
		final JFileChooser chooser = new JFileChooser(file);
		chooser.setDialogTitle(title);
		chooser.setFileSelectionMode(type);
		chooser.setDragEnabled(true);
		return chooser;
	}

	private Object getObj(final String promptMsg, final String promptTitle,
		final Object defaultValue)
	{
		return JOptionPane.showInputDialog(parent, promptMsg, promptTitle,
			JOptionPane.PLAIN_MESSAGE, null, null, defaultValue);
	}

	public void centeredMsg(final String msg, final String title) {
		centeredDialog(msg, title, JOptionPane.PLAIN_MESSAGE);
	}

	private int centeredDialog(final String msg, final String title, final int type) {
		/* if SwingDialogs could be centered, we could simply use */
		//final SwingDialog d = new SwingDialog(getLabel(msg), type, false);
		//if (parent != null) d.setParent(parent);
		//return d.show();
		final JOptionPane optionPane = new JOptionPane(getLabel(msg), type, JOptionPane.DEFAULT_OPTION);
		final JDialog d = optionPane.createDialog(title);
		if (parent != null) {
			AWTWindows.centerWindow(parent.getBounds(), d);
			// we could also use d.setLocationRelativeTo(parent);
		}
		d.setVisible(true);
		final Object result = optionPane.getValue();
		if (result == null || (!(result instanceof Integer))) return SwingDialog.UNKNOWN_OPTION;
		return (Integer) result;
	}

	private JLabel getLabel(final String text) {
		if (text == null || text.startsWith("<") || text.length() < 60) new JLabel(
			text);
		return new JLabel("<html><body><div style='width:400;'>" + text);
	}

	public void blinkingError(final JComponent blinkingComponent,
		final String msg)
	{
		final Color prevColor = blinkingComponent.getForeground();
		final Color flashColor = Color.RED;
		final Timer blinkTimer = new Timer(400, new ActionListener() {

			private int count = 0;
			private final int maxCount = 100;
			private boolean on = false;

			@Override
			public void actionPerformed(final ActionEvent e) {
				if (count >= maxCount) {
					blinkingComponent.setForeground(prevColor);
					((Timer) e.getSource()).stop();
				}
				else {
					blinkingComponent.setForeground(on ? flashColor : prevColor);
					on = !on;
					count++;
				}
			}
		});
		blinkTimer.start();
		if (centeredDialog(msg, "Ongoing Operation",JOptionPane.PLAIN_MESSAGE) > Integer.MIN_VALUE)
		{ // Dialog dismissed
			blinkTimer.stop();
		}
		blinkingComponent.setForeground(prevColor);
	}

	/**
	 * Displays an auto-dismiss dialog (temporary message) at current cursor
	 * location.
	 *
	 * @param msg the message to be displayed. HMTL allowed.
	 */
	public void msgAtPointer(final String msg) {
		final Point loc = MouseInfo.getPointerInfo().getLocation();
		tempMsg(msg, loc.x, loc.y, false);
		return;
	}

	/* Static methods */

	public static String ctrlKey() {
		return (PlatformUtils.isMac()) ? "CMD" : "CTRL";
	}

	public static String modKey() {
		return (PlatformUtils.isMac()) ? "ALT" : "CTRL";
	}

	public static GridBagConstraints defaultGbc() {
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

	public static Color getDisabledComponentColor() {
		try {
			return UIManager.getColor("CheckBox.disabledText");
		}
		catch (final Exception ignored) {
			return Color.GRAY;
		}
	}

	public static JButton smallButton(final String text) {
		final double SCALE = .85;
		final JButton button = new JButton(text);
		final Font font = button.getFont();
		button.setFont(font.deriveFont((float) (font.getSize() * SCALE)));
		final Insets insets = button.getMargin();
		button.setMargin(new Insets((int) (insets.top * SCALE), (int) (insets.left *
			SCALE), (int) (insets.bottom * SCALE), (int) (insets.right * SCALE)));
		return button;
	}

	public static JSpinner integerSpinner(final int value, final int min,
		final int max, final int step)
	{
		final int maxDigits = Integer.toString(max).length();
		final SpinnerModel model = new SpinnerNumberModel(value, min, max, step);
		final JSpinner spinner = new JSpinner(model);
		final JFormattedTextField textfield = ((DefaultEditor) spinner.getEditor())
			.getTextField();
		textfield.setColumns(maxDigits);
		textfield.setEditable(false);
		return spinner;
	}

	public static double extractDouble(final JTextField textfield) {
		try {
			return Double.parseDouble(textfield.getText());
		}
		catch (final NullPointerException | NumberFormatException ignored) {
			return Double.NaN;
		}
	}

	public static void enableComponents(final java.awt.Container container,
		final boolean enable)
	{
		final Component[] components = container.getComponents();
		for (final Component component : components) {
			if (!(component instanceof JPanel)) component.setEnabled(enable); //otherwise JPanel background will change
			if (component instanceof java.awt.Container) {
				enableComponents((java.awt.Container) component, enable);
			}
		}
	}

	public static String micrometre() {
		return String.valueOf('\u00B5') + 'm';
	}

	public static void errorPrompt(final String msg) {
		new GuiUtils().error(msg, "SNT v"+ SNT.VERSION);
	}

}
