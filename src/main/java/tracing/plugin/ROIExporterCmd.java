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

package tracing.plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.imagej.ImageJ;
import tracing.Path;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

/**
 * Command providing a GUI for {@link RoiExporterCmd} and allowing export
 * of {@link Path}s to the IJ1 ROI Manager.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false)
public class ROIExporterCmd implements Command {

	@Parameter
	private UIService uiService;

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter(required = false, label = "Convert", choices = { "Path segments", "Branch Points", "Tips" })
	private String roiChoice;

	@Parameter(required = false, label = "View", choices = { "XY (default)", "XZ", "ZY" })
	private String viewChoice;

	@Parameter(required = false, label = "Impose SWC colors")
	private boolean useSWCcolors;

	@Parameter(required = false, label = "Adopt path diameter as line thickness")
	private boolean avgWidth;

	@Parameter(required = false, label = "Discard existing ROIs in ROI Manager")
	private boolean discardExisting;

	@Parameter(required= true)
	private ArrayList<Path> paths;

	private Overlay overlay;
	private RoiConverter converter;


	@Override
	public void run() {

		converter = new RoiConverter(paths);

		if (converter.getParsedPaths().isEmpty()) {
			uiService.getDefaultUI().dialogPrompt("No Paths to Convert",
					"Error", DialogPrompt.MessageType.WARNING_MESSAGE,
					DialogPrompt.OptionType.DEFAULT_OPTION);
			return;
		}

		final int skippedPaths = paths.size() - converter.getParsedPaths().size(); 
		if (skippedPaths > 0)
			logService.warn(""+ skippedPaths + " were rejected and will not be converted");
		logService.info("Converting paths...");
		statusService.showStatus("Converting paths...");

		converter.useSWCcolors(useSWCcolors);
		converter.setStrokeWidth((avgWidth) ? -1 : 0);
		overlay = new Overlay();

		if (viewChoice.contains("XZ"))
			converter.setView(RoiConverter.XZ_PLANE);
		else if (viewChoice.contains("ZY"))
			converter.setView(RoiConverter.ZY_PLANE);
		else
			converter.setView(RoiConverter.XY_PLANE);

		roiChoice = roiChoice.toLowerCase();
		if (roiChoice.contains("tips"))
			converter.convertTips(overlay);
		else if (roiChoice.contains("branch points"))
			converter.convertBranchPoints(overlay);
		else
			converter.convertPaths(overlay);
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null)
			rm = new RoiManager();
		else if (discardExisting)
			rm.reset();
		// Prefs.showAllSliceOnly = !plugin.is2D();
		// rm.setEditMode(plugin.getImagePlus(), false);
		for (final Roi roi : overlay.toArray())
			rm.addRoi(roi);
		rm.runCommand("sort");
		// rm.setEditMode(plugin.getImagePlus(), true);
		rm.runCommand("show all without labels");
		statusService.clearStatus();

	}

	/**
	 * IDE debug method
	 * 
	 * @throws IOException
	 **/
	public static void main(final String[] args) throws IOException {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input= new HashMap<>();
		input.put("paths", new ArrayList<Path>());
		ij.command().run(ROIExporterCmd.class, true, input);
	}

}
