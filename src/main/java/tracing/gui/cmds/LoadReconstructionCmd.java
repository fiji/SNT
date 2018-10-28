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

package tracing.gui.cmds;

import java.io.File;
import java.io.FilenameFilter;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.ColorRGB;

import net.imagej.ImageJ;
import tracing.SNT;
import tracing.SNTService;
import tracing.Tree;
import tracing.plot.TreePlot3D;

/**
 * Command for loading Reconstruction files in Reconstruction Viewer. Loaded
 * paths are not listed in the Path Manager.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, initializer = "init", label = "Load Reconstruction(s)...")
public class LoadReconstructionCmd extends DynamicCommand {

	@Parameter SNTService sntService;

	@Parameter UIService uiService;

	@Parameter(label = "File/Directory Path", required = true, description = "<HTML>Path to single file, or directory containing multiple files."
			+ "<br>Supported extensions: traces, (e)SWC, json")
	private File file;

	@Parameter(label = "Color", required = false, description = "Rendering color of imported file(s)")
	private ColorRGB color;

	@Parameter(persist = false, visibility = ItemVisibility.MESSAGE)
	private String msg;

	@Parameter(required = false)
	private TreePlot3D recViewer;

	@SuppressWarnings("unused")
	private void init() {
		final MutableModuleItem<String> mItem = getInfo().getMutableInput("msg", String.class);
		if (recViewer != null && recViewer.isSNTInstance()) {
			msg = "NB: Loaded file(s) will not be listed in Path Manager";
		} else {
			msg = " ";
		}
	}

	/*
	 * 
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		try {
			if (recViewer == null)
				recViewer = sntService.getReconstructionViewer();
		} catch (final UnsupportedOperationException exc) {
			cancel("SNT's Reconstruction Viewer is not open");
		}

		if (!file.exists())
			cancel(file.getAbsolutePath() + " is no longer available");

		if (file.isFile()) {
			final Tree tree = new Tree(file.getAbsolutePath());
			tree.setColor(color);
			if (tree.isEmpty())
				cancel("No Paths could be extracted from file. Invalid path?");
			recViewer.add(tree);
			recViewer.validate();
			return;
		}

		if (file.isDirectory()) {
			final File[] files = file.listFiles((FilenameFilter) (dir, name) -> {
				final String lcName = name.toLowerCase();
				return (lcName.endsWith("swc") || lcName.endsWith("traces") || lcName.endsWith("json"));
			});
			recViewer.setViewUpdatesEnabled(false);
			int failures = 0;
			for (final File file : files) {
				final Tree tree = new Tree(file.getAbsolutePath());
				if (tree.isEmpty()) {
					SNT.log("Skipping file... No Paths extracted from " + file.getAbsolutePath());
					failures++;
					continue;
				}
				tree.setColor(color);
				recViewer.add(tree);
			}
			if (failures == files.length) {
				cancel("No files imported. Invalid Directory?");
			}
			recViewer.setViewUpdatesEnabled(true);
			recViewer.validate();
			final String msg = "" + (files.length - failures) + "/" + files.length + " files successfully imported.";
			uiService.showDialog(msg, (failures == 0) ? "All Reconstructions Imported" : "Partially Successful Import");
		}
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(LoadReconstructionCmd.class, true);
	}
}