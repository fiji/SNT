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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;
import org.scijava.widget.Button;

import net.imagej.ImageJ;
import tracing.PathAndFillManager;
import tracing.SNT;
import tracing.SNTService;
import tracing.SNTUI;
import tracing.SimpleNeuriteTracer;
import tracing.Tree;
import tracing.gui.GuiUtils;
import tracing.io.NeuroMorphoLoader;
import tracing.plot.TreePlot3D;

/**
 * Command for importing NeuroMorpho.org reconstructions
 * 
 * @see {@link NeuroMorphoLoader}
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Import NeuroMorpho.org Reconstructions", initializer = "init")
public class NMImporterCmd extends DynamicCommand {

	@Parameter
	private StatusService statusService;

	@Parameter
	private SNTService sntService;

	@Parameter(required = true, persist = true, label = "IDs (comma- or space- separated list)", description = "e.g., AA0001 or 10.25378/janelia.5527672")
	private String query;

	@Parameter(required = false, label = "Colors", choices = {"Distinct (each cell labelled uniquely)", "Common color specified below"})
	private String colorChoice;

	@Parameter(required = false, label = "<HTML>&nbsp;")
	private ColorRGB commonColor;

	@Parameter(required = false, persist = true, label = "Replace existing paths")
	private boolean clearExisting;

	@Parameter(label = "Check Database Access", callback = "pingServer")
	private Button ping;

	@Parameter(persist = false, visibility = ItemVisibility.MESSAGE)
	private String pingMsg;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.INVISIBLE)
	private TreePlot3D recViewer;

	private NeuroMorphoLoader loader;
	private SimpleNeuriteTracer snt;
	private SNTUI ui;
	private PathAndFillManager pafm;

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		final boolean standAloneViewer = recViewer != null;
		if (!standAloneViewer && !sntService.isActive()) {
			error("No Reconstruction Viewer specified and no active instance of SimpleNeuriteTracer was found.");
			return;
		}

		final LinkedHashMap<String, String> urlsMap = getURLmapFromQuery(query);
		if (urlsMap == null || urlsMap.isEmpty()) {
			error("Invalid query. No reconstructions retrieved.");
			return;
		}
		if (!loader.isDatabaseAvailable()) {
			error(getPingMsg(false));
			return;
		}

		if (standAloneViewer) {
			pafm = new PathAndFillManager();
		}
		else if (sntService.isActive()) {
			snt = sntService.getPlugin();
			ui = sntService.getUI();
			pafm = sntService.getPathAndFillManager();
			recViewer = (ui == null) ? null : ui.getReconstructionViewer(false);
		} else {
			throw new IllegalArgumentException("Somehow neither a Viewer nor a SNT instance are available");
		}

		status("Retrieving cells. Please wait...");
		SNT.log("NeuroMorpho.org import: Downloading from URL(s)...");
		final int lastExistingPathIdx = pafm.size() - 1;
		final List<Tree> result = pafm.importSWCs(urlsMap, getColor());
		final long failures = result.stream().filter(tree -> tree == null || tree.isEmpty()).count();
		if (failures == result.size()) {
			error("No reconstructions could be retrieved. Invalid Query?");
			status("Error... No reconstructions imported");
			return;
		}

		if (clearExisting) {
			if (standAloneViewer) {
				recViewer.removeAll();
			}
			else {
				// We are importing into a functional SNTUI with Path Manager
				final int[] indices = IntStream.rangeClosed(0, lastExistingPathIdx).toArray();
				pafm.deletePaths(indices);
			}
		}

		if (standAloneViewer) {
			recViewer.setViewUpdatesEnabled(false);
			result.forEach(tree -> {
				if (tree != null && !tree.isEmpty()) recViewer.add(tree);
			});
			recViewer.setViewUpdatesEnabled(true);
			recViewer.validate();
		}
		else if (snt != null) {
			SNT.log("Rebuilding canvases...");
			snt.rebuildDisplayCanvases();
			if (recViewer != null) recViewer.syncPathManagerList();
		}

		if (failures > 0) {
			error(String.format("%d/%d reconstructions could not be retrieved.", failures, result.size()));
			status("Partially successful import...");
			SNT.log("Import failed for the following queried morphologies:");
			result.forEach(tree -> { if (tree.isEmpty()) SNT.log(tree.getLabel()); });
		} else {
			status("Successful imported " + result.size() + " reconstruction(s)...");
		}
	}

	private void status(final String statusMsg) {
		if (ui == null) {
			statusService.showStatus(statusMsg);
		} else {
			ui.showStatus(statusMsg, false);
		}
	}

	private void error(final String msg) {
		if (snt != null) {
			snt.error(msg);
		} else {
			cancel(msg);
		}
	}

	private ColorRGB getColor() {
		return (colorChoice.contains("unique")) ? null : commonColor;
	}

	private LinkedHashMap<String, String> getURLmapFromQuery(final String query) {
		if (query == null)
			return null;
		final List<String> ids = new LinkedList<String>(Arrays.asList(query.split("\\s*(,|\\s)\\s*")));
		if (ids.isEmpty())
			return null;
		Collections.sort(ids);
		final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
		ids.forEach(id -> map.put(id, loader.getReconstructionURL(id)));
		return map;
	}

	@SuppressWarnings("unused")
	private void init() {
		loader = new NeuroMorphoLoader();
		if (query == null || query.isEmpty())
			query = "cnic_001";
		pingMsg = "Internet connection required. Retrieval of long lists may be rather slow...           ";
		if (recViewer != null) {
			// If a stand-alone viewer was specified, customize options specific
			// to the SNT UI
			final MutableModuleItem<Boolean> clearExistingInput = getInfo().getMutableInput("clearExisting", Boolean.class);
			clearExistingInput.setLabel("Clear existing reconstructions");
		}
	}

	@SuppressWarnings("unused")
	private void pingServer() {
		pingMsg = getPingMsg(loader.isDatabaseAvailable());
	}

	private String getPingMsg(final boolean pingResponse) {
		return (pingResponse) ? "Successfully connected to the NeuroMorpho database."
				: "NeuroMorpho.org not reached. It is either down or you have no internet access.";
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(NMImporterCmd.class, true);
	}

}