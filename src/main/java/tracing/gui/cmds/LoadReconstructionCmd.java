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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.ImageJ;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;
import org.scijava.widget.FileWidget;

import tracing.SNT;
import tracing.Tree;
import tracing.plot.TreePlot3D;
import tracing.util.SWCColor;

/**
 * Command for loading Reconstruction files in Reconstruction Viewer. Loaded
 * paths are not listed in the Path Manager.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, initializer = "init",
	label = "Load Reconstruction(s)...")
public class LoadReconstructionCmd extends CommonDynamicCmd {

	private static final String COLOR_CHOICE_MONO =
		"Common color specified below";
	private static final String COLOR_CHOICE_POLY =
		"Distinct (each file labelled uniquely)";

	@Parameter(label = "File", required = true,
		description = "Supported extensions: traces, (e)SWC, json")
	private File file;

	@Parameter(required = false, label = "Color", choices = { COLOR_CHOICE_MONO,
		COLOR_CHOICE_POLY })
	private String colorChoice;

	@Parameter(label = "<HTML>&nbsp;", required = false,
		description = "Rendering color of imported file(s)")
	private ColorRGB color;

	@Parameter(persist = false, visibility = ItemVisibility.MESSAGE)
	private String msg;

	@Parameter(required = false)
	private TreePlot3D recViewer;

	@Parameter(required = false, visibility = ItemVisibility.INVISIBLE)
	private boolean importDir = false;

	@SuppressWarnings("unused")
	private void init() {
		if (importDir) {
			final MutableModuleItem<File> fileMitem = getInfo().getMutableInput(
				"file", File.class);
			fileMitem.setWidgetStyle(FileWidget.DIRECTORY_STYLE);
			fileMitem.setLabel("Directory");
			fileMitem.setDescription(
				"<HTML>Path to directory containing multiple files." +
					"<br>Supported extensions: traces, (e)SWC, json");
		}
		else {
			final MutableModuleItem<String> colorChoiceMitem = getInfo()
				.getMutableInput("colorChoice", String.class);
			colorChoiceMitem.setRequired(false);
			final List<String> options = new ArrayList<>();
			options.add("Color specified below");
			options.add("Black");
			options.add("Blue");
			options.add("Cyan");
			options.add("Green");
			options.add("Magenta");
			options.add("Orange");
			options.add("Red");
			options.add("Yellow");
			options.add("White");
			colorChoiceMitem.setChoices(options);
			colorChoiceMitem.setCallback("colorChoiceChanged");
		}
		if (recViewer != null && recViewer.isSNTInstance()) {
			msg = "NB: Loaded file(s) will not be listed in Path Manager";
		}
		else {
			msg = " ";
		}
	}

	@SuppressWarnings("unused")
	private void colorChoiceChanged() {
		final ColorRGB colorTemp = Colors.getColor(colorChoice.toLowerCase());
		if (colorTemp != null) color = colorTemp;
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
			if (recViewer == null) recViewer = sntService.getReconstructionViewer();
		}
		catch (final UnsupportedOperationException exc) {
			error(
				"SNT's Reconstruction Viewer is not open and no other Viewer was specified.");
		}

		if (!file.exists()) error(file.getAbsolutePath() +
			" is no longer available");

		if (file.isFile()) {
			try {
				final Tree tree = new Tree(file.getAbsolutePath());
				tree.setColor(color);
				if (tree.isEmpty()) cancel(
					"No Paths could be extracted from file. Invalid path?");
				recViewer.add(tree);
				recViewer.validate();
				return;
			}
			catch (final IllegalArgumentException ex) {
				cancel(ex.getMessage());
			}
		}

		if (file.isDirectory()) {
			final File[] files = file.listFiles((FilenameFilter) (dir, name) -> {
				final String lcName = name.toLowerCase();
				return (lcName.endsWith("swc") || lcName.endsWith("traces") || lcName
					.endsWith("json"));
			});
			recViewer.setViewUpdatesEnabled(false);
			int failures = 0;
			final ColorRGB[] colors = (colorChoice.contains("unique")) ? SWCColor
				.getDistinctColors(files.length) : null;
			int idx = 0;
			for (final File file : files) {
				final Tree tree = new Tree(file.getAbsolutePath());
				if (tree.isEmpty()) {
					SNT.log("Skipping file... No Paths extracted from " + file
						.getAbsolutePath());
					failures++;
					continue;
				}
				tree.setColor((colors == null) ? color : colors[idx++]);
				recViewer.add(tree);
			}
			if (failures == files.length) {
				error("No files imported. Invalid Directory?");
			}
			recViewer.setViewUpdatesEnabled(true);
			recViewer.validate();
			final String msg = "" + (files.length - failures) + "/" + files.length +
				" files successfully imported.";
			msg(msg, (failures == 0) ? "All Reconstructions Imported"
				: "Partially Successful Import");
		}
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input = new HashMap<>();
		input.put("importDir", true);
		ij.command().run(LoadReconstructionCmd.class, true, input);
	}
}
