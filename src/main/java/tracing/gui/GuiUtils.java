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
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.scijava.ui.swing.SwingDialog;
import org.scijava.ui.swing.widget.SwingColorWidget;
import org.scijava.util.PlatformUtils;

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
		error(msg, SNT.VERSION, false);
	}

	public void error(final String msg, final String title) {
		error(msg, title, false);
	}

	public void msg(final String msg, final String title) {
		error(msg, title, false); //TODO: this could be something fancier
	}

	public void error(final String msg, final String title, final boolean icon) {
		simpleMsg(msg, title, icon ? JOptionPane.ERROR_MESSAGE : JOptionPane.PLAIN_MESSAGE);
	public void tempMsg(final String msg, final boolean snapToParent) {
		tempMsg(msg, -1, -1, snapToParent);
	}

	public void tempMsg(final String msg, final Point location) {
		tempMsg(msg, location.x, location.y, false);
	}

	public void tempMsg(final String msg, final int x, final int y) {
		tempMsg(msg, x, y, false);
	}

	private void tempMsg(final String msg, final int x, final int y,
		final boolean snapToParent)
	{
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
		dialog.setVisible(true);
	}

	public int yesNoDialog(final String msg, final String title) {
		final SwingDialog d = new SwingDialog(getLabel(msg),
			JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, false);
		d.setTitle(title);
		if (parent != null) d.setParent(parent);
		return d.show();
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

	public Number getNumber(final String promptMsg, final String promptTitle,
		final Number defaultValue)
	{
		return (Number) getObj(promptMsg, promptTitle, defaultValue);
	}

	public File saveFile(final String title, final File file) {
		final JFileChooser chooser = fileChooser(title, file, JFileChooser.FILES_ONLY);
		if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		return null;
	}

	public File openFile(final String title, final File file) {
		final JFileChooser chooser = fileChooser(title, file, JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		return null;
	}

	public File chooseDirectory(final String title, final File file) {
		final JFileChooser chooser = fileChooser(title, file, JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		return null;
	}

	private JFileChooser fileChooser(final String title, final File file, final int type) {
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

	public String ctrlKey() {
		return (PlatformUtils.isMac()) ? "CMD" : "CTRL";
	}

	private int simpleMsg(final String msg, final String title, final int type) {
		final SwingDialog d = new SwingDialog(getLabel(msg), type, false);
		d.setTitle(title);
		if (parent != null) d.setParent(parent);
		return d.show();
	}

	private JLabel getLabel(final String text) {
		if (text==null || text.startsWith("<") || text.length() < 60)
			new JLabel(text);
		return new JLabel("<html><body><div style='width:400;'>" + text);
	}

}
