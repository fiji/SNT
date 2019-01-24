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

import net.imagej.ImageJ;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;

import tracing.SNTPrefs;
import tracing.gui.GuiUtils;
import tracing.plugin.CallLegacyShollPlugin;
import tracing.plugin.PlotterCmd;
import tracing.plugin.ROIExporterCmd;
import tracing.plugin.ShollTracingsCmd;
import tracing.plugin.SkeletonizerCmd;
import tracing.plugin.StrahlerCmd;
import tracing.plugin.TreeMapperCmd;

/**
 * Command for resetting SNT Preferences.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false)
public class ResetPrefsCmd extends ContextCommand {

	@Parameter
	private UIService uiService;

	@Parameter
	private PrefService prefService;

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		final Result result = uiService.showDialog(
			"Reset preferences to defaults? (A restart may be required)",
			MessageType.QUESTION_MESSAGE);
		if (Result.YES_OPTION != result && Result.OK_OPTION != result) {
			return;
		}
		clearAll();
		uiService.showDialog(
			"Preferences Reset. You should now restart SNT for changes to take effect.",
			"Restart Required");

	}

	/**
	 * Clears all of SNT preferences.
	 */
	public void clearAll() {
		// gui.cmds
		prefService.clear(ColorMapReconstructionCmd.class);
		prefService.clear(CompareFilesCmd.class);
		prefService.clear(DistributionCmd.class);
		prefService.clear(LoadObjCmd.class);
		prefService.clear(LoadReconstructionCmd.class);
		prefService.clear(MLImporterCmd.class);
		prefService.clear(MultiSWCImporterCmd.class);
		prefService.clear(PathFitterCmd.class);
		prefService.clear(ReconstructionViewerCmd.class);
		prefService.clear(RecViewerPrefsCmd.class);
		prefService.clear(RemoteSWCImporterCmd.class);
		prefService.clear(ShowCorrespondencesCmd.class);
		prefService.clear(SNTLoaderCmd.class);
		prefService.clear(SWCTypeFilterCmd.class);
		prefService.clear(SWCTypeOptionsCmd.class);
		prefService.clear(TranslateReconstructionsCmd.class);

		// tracing.plugin
		prefService.clear(CallLegacyShollPlugin.class);
		prefService.clear(PlotterCmd.class);
		prefService.clear(ROIExporterCmd.class);
		prefService.clear(ShollTracingsCmd.class);
		prefService.clear(SkeletonizerCmd.class);
		prefService.clear(StrahlerCmd.class);
		prefService.clear(TreeMapperCmd.class);

		// Legacy (IJ1-based) preferences
		SNTPrefs.clearAll();
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(ResetPrefsCmd.class, true);
	}

}
