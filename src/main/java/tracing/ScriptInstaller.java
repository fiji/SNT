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

import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Future;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuKeyEvent;
import javax.swing.event.MenuKeyListener;

import org.scijava.Context;
import org.scijava.app.AppService;
import org.scijava.plugin.Parameter;
import org.scijava.script.ScriptInfo;
import org.scijava.script.ScriptModule;
import org.scijava.script.ScriptService;
import org.scijava.ui.swing.script.TextEditor;
import org.scijava.util.FileUtils;

import tracing.gui.IconFactory;
import tracing.gui.IconFactory.GLYPH;

/** Utility class for discovery of scripts scripting SNT */
class ScriptInstaller implements MenuKeyListener {

	@Parameter
	private Context context;

	@Parameter
	private ScriptService scriptService;

	@Parameter
	private AppService appService;

	private final SNTUI ui;
	private final TreeSet<ScriptInfo> scripts;
	private boolean openInsteadOfRun;

	protected ScriptInstaller(final Context context, final SNTUI ui) {

		context.inject(this);
		this.ui = ui;

		scripts = new TreeSet<>(new Comparator<ScriptInfo>() {
			// ensure files will be listed in alphabetic order
			@Override
			public int compare(final ScriptInfo o1, final ScriptInfo o2) {
				return getScriptLabel(o1).compareTo(getScriptLabel(o2));
			}
		});

		// 1. Include script_templates that are not discovered by ScriptService
		final File baseDir = appService.getApp().getBaseDirectory();
		final Map<String, URL> map = FileUtils.findResources(null, "script_templates/Neuroanatomy", baseDir);
		if (map != null) {
			map.forEach((k, v) -> {
				try {
					scripts.add(new ScriptInfo(context, v, k));
				} catch (final IOException ignored) {
					// just skip file
				}
			});
		}

		// 2. Include discovered scripts
		for (final ScriptInfo si : scriptService.getScripts()) {
			final boolean pathMatch = si.getPath() != null
					&& (si.getPath().contains("SNT") || si.getPath().toLowerCase().contains("neuroanatomy"));
			if (pathMatch)
				scripts.add(si);
		}

	}

	private void runScript(final ScriptInfo si) {
		ui.showStatus("Running script...", false);
		final Future<ScriptModule> fsm = scriptService.run(si, true, (Map<String, Object>) null);
		if (fsm.isCancelled()) {
			ui.showStatus("Script canceled...", true);
		} else if (fsm.isDone()) {
			ui.showStatus("Script completed...", true);
		}
	}

	private void openScript(final ScriptInfo si) {
		ui.showStatus("Opening script...", false);
		final TextEditor editor = new TextEditor(context);
		final BufferedReader reader = si.getReader();
		if (reader == null) { // local file
			editor.open(new File(si.getPath()));
		} else { // jar file
			try {
				final StringBuffer stringBuffer = new StringBuffer();
				String line = null;
				while ((line = reader.readLine()) != null) {
					stringBuffer.append(line).append("\n");
				}
				editor.createNewDocument(getScriptLabel(si), stringBuffer.toString());
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
		editor.setVisible(true);
		ui.showStatus("", false);
	}

	private JMenu getMenu(final String folder) {
		final JMenu sMenu = new JMenu((folder == null) ? "Full List" : folder);
		sMenu.addMenuKeyListener(this);
		for (final ScriptInfo si : scripts) {
			final String path = si.getPath();
			if (path == null || (folder != null && !path.contains(folder)))
				continue;
			final JMenuItem mItem = new JMenuItem(getScriptLabel2(si));
			sMenu.add(mItem);
			mItem.addMenuKeyListener(this);
			mItem.addActionListener(e -> {
				if (openInsteadOfRun || (e.getModifiers() & InputEvent.SHIFT_MASK) != 0) {
					openScript(si);
				} else {
					runScript(si);
				}
				openInsteadOfRun = false;
			});
		}
		return sMenu;
	}

	/** Returns a UI list of the SNT 'Utilities' scripts **/
	protected JMenu getUtilScriptsMenu() {
		final JMenu menu = getMenu("Utilities");
		for (int i = 0; i < menu.getItemCount(); i++) {
			final JMenuItem mItem = menu.getItem(i);
			mItem.setText(SNT.stripExtension(mItem.getText()) + "...");
		}
		return menu;
	}

	/** Returns a UI list with all available scripts scripting SNT **/
	protected JMenu getScriptsMenu() {
		final JMenu sMenu = new JMenu("Scripts");
		sMenu.add(getMenu("Analysis"));
		sMenu.add(getMenu("Tracing"));
		//sMenu.add(getMenu("Utilities"));
		sMenu.addSeparator();
		final JMenu listMenu = getMenu(null);
		listMenu.setIcon(IconFactory.getMenuIcon(GLYPH.LIST));
		sMenu.add(listMenu);
		sMenu.addSeparator();
		sMenu.add(about());
		return sMenu;
	}

	private String getScriptLabel2(final ScriptInfo si) {
		return getScriptLabel(si).replace('_', ' ');
	}

	private String getScriptLabel(final ScriptInfo si) {
		String label = si.getLabel();
		if (label != null)
			return label;
		label = si.getName();
		if (label != null)
			return label;
		label = si.getPath();
		if (label != null)
			return label.substring(label.lastIndexOf(File.separator) + 1);
		return si.getIdentifier(); // never null
	}

	private JMenuItem about() {
		final JMenuItem mItem = new JMenuItem("About SNT Scripts...");
		mItem.setIcon(IconFactory.getMenuIcon(GLYPH.QUESTION));
		mItem.addActionListener(e -> {
			ui.guiUtils.centeredMsg("This menu lists scripting routines that enhance SNT functionality. "
					+ "The list is automatically populated at startup.<br><br>"
					+ "To have your own scripts listed here, save them in the <tt>scripts</tt> "
					+ "directory while including <i>SNT</i> in the filename (e.g., <tt>"
					+ appService.getApp().getBaseDirectory() + File.separator + " scripts" + File.separator
					+ "My_SNT_script.py</tt>) <br><br>"
					+ "To edit a listed script hold \"Shift\" while clicking on its menu entry.<br><br>"
					+ "Several programming examples are available through the Script Editor's "
					+ "<i>Templates>Neuroanatomy></i> menu.  Please submit a pull request to SNT source "
					+ "code repository if you would like to have your scripts distributed with Fiji.",
					"About SNT Scripts...");
		});
		return mItem;
	}

	@Override
	public void menuKeyTyped(final MenuKeyEvent e) {
		// ignored
	}

	@Override
	public void menuKeyPressed(final MenuKeyEvent e) {
		openInsteadOfRun = e.isShiftDown();
	}

	@Override
	public void menuKeyReleased(final MenuKeyEvent e) {
		openInsteadOfRun = false;

	}

}
