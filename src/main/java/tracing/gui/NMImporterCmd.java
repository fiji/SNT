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

package tracing.gui;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;

import net.imagej.ImageJ;
import tracing.PathAndFillManager;
import tracing.SNT;
import tracing.SNTService;
import tracing.SNTUI;
import tracing.SimpleNeuriteTracer;
import tracing.io.NeuroMorphoLoader;

/**
 * Scijava-based GUI for {@link NeuroMorphoLoader}
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Import NeuroMorpho.org Reconstructions", initializer = "init")
public class NMImporterCmd extends ContextCommand {

	@Parameter
	private SNTService sntService;

	@Parameter(required = true, persist = true, label = "IDs (comma- or space- separated list)", description = "e.g., AA0001 or 10.25378/janelia.5527672")
	private String query;

	@Parameter(required = false, persist = true, label = "Replace existing paths")
	private boolean clearExisting;

	@Parameter(label = "Check Database Access", callback = "pingServer")
	private Button ping;

	@Parameter(persist = false, visibility = ItemVisibility.MESSAGE)
	private String pingMsg;

	private NeuroMorphoLoader loader;

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if (!sntService.isActive()) {
			cancel("No active instance of SimpleNeuriteTracer was found.");
			return;
		}
		final LinkedHashMap<String, String> urlsMap = getURLmapFromQuery(query);
		if (urlsMap == null || urlsMap.isEmpty()) {
			cancel("Invalid query. No reconstructions retrieved.");
			return;
		}
		if (!loader.isDatabaseAvailable()) {
			cancel(getPingMsg(false));
			return;
		}

		final SimpleNeuriteTracer snt = sntService.getPlugin();
		final SNTUI ui = sntService.getUI();
		final PathAndFillManager pafm = sntService.getPathAndFillManager();

		ui.showStatus("Retrieving cells. Please wait...", false);
		SNT.log("NeuroMorpho.org import: Downloading from URL(s)...");
		final int lastExistingPathIdx = pafm.size() - 1;
		final Map<String, Boolean> result = pafm.importSWCs(urlsMap);
		if (!result.containsValue(true)) {
			snt.error("No reconstructions could be retrieved. Invalid Query?");
			ui.showStatus("Error... No reconstructions imported", true);
			return;
		}
		if (clearExisting) {
			final int[] indices = IntStream.rangeClosed(0, lastExistingPathIdx).toArray();
			pafm.deletePaths(indices);
		}
		SNT.log("Rebuilding canvases...");
		snt.rebuildDisplayCanvases();
		final long failures = result.values().stream().filter(p -> p == false).count();
		if (failures > 0) {
			snt.error(String.format("%d/%d reconstructions could not be retrieved.", failures, result.size()));
			ui.showStatus("Partially successful import...", true);
			SNT.log("Import failed for the following queried morphologies:");
			result.forEach((key, value) -> { if (!value) SNT.log(key); });
		} else {
			ui.showStatus("Successful imported " + result.size() + " reconstruction(s)...", true);
		}
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