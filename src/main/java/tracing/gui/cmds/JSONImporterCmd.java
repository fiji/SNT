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

package tracing.gui.cmds;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.IntStream;

import org.json.JSONException;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;

import net.imagej.ImageJ;
import tracing.PathAndFillManager;
import tracing.SNT;
import tracing.SNTService;
import tracing.SNTUI;
import tracing.SimpleNeuriteTracer;
import tracing.Tree;
import tracing.gui.GuiUtils;
import tracing.io.MouseLightLoader;
import tracing.util.SWCPoint;

/**
 * Command for importing a (MouseLight) JSON file
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false,
	label = "Import ML JSON Data")
public class JSONImporterCmd extends ContextCommand {

	@Parameter
	private SNTService sntService;

	@Parameter(label = "File")
	private File file;

	@Parameter(required = false, label = "Colors", choices = {
		"Random (from distinct set)", "Specified below" })
	private String colorChoice;

	@Parameter(required = false, label = "<HTML>&nbsp;")
	private ColorRGB color;

	@Parameter(required = false, label = "Replace existing paths")
	private boolean clearExisting;

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		final SimpleNeuriteTracer snt = sntService.getPlugin();
		final SNTUI ui = sntService.getUI();
		final PathAndFillManager pafm = sntService.getPathAndFillManager();

		ui.showStatus("Importing file. Please wait...", false);
		SNT.log("Importing file " + file);

		final int lastExistingPathIdx = pafm.size() - 1;
		long failures = 0;
		try {
			final Map<String, TreeSet<SWCPoint>> nodes = MouseLightLoader.extractNodes(file, "all");
			final Map<String, Tree> result = pafm.importNeurons(nodes, getColor(), null);
			failures = result.values().stream().filter(Tree::isEmpty).count();
			if (failures == result.size()) {
				snt.error("No reconstructions could be retrieved. Invalid directory?");
				ui.showStatus("Error... No reconstructions imported", true);
				return;
			}

			if (clearExisting) {
				final int[] indices = IntStream.rangeClosed(0, lastExistingPathIdx).toArray();
				pafm.deletePaths(indices);
			}
			SNT.log("Rebuilding canvases...");
			if (failures > 0) {
				snt.error(String.format("%d/%d reconstructions could not be retrieved.", failures, result.size()));
				ui.showStatus("Partially successful import...", true);
				SNT.log("Import failed for the following files:");
				result.values().forEach(tree -> {
					if (tree.isEmpty())
						SNT.log(tree.getLabel());
				});
			} else {
				ui.showStatus("Successful imported " + result.size() + " reconstruction(s)...", true);
			}
		} catch (final FileNotFoundException | IllegalArgumentException | JSONException e) {
			snt.error(e.getMessage());
		}
	}

	private ColorRGB getColor() {
		return (colorChoice.contains("distinct")) ? null : color;
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(JSONImporterCmd.class, true);
	}

}
