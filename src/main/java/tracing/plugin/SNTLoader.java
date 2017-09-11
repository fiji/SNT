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

package tracing.plugin;

import io.scif.services.DatasetIOService;

import java.io.File;
import java.io.IOException;

import net.imagej.ImageJ;
import net.imagej.display.ImageDisplayService;
import net.imagej.legacy.LegacyService;

import org.scijava.ItemVisibility;
import org.scijava.command.DynamicCommand;
import org.scijava.display.DisplayService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import ij.ImagePlus;
import tracing.SNT;

@Plugin(type = DynamicCommand.class, visible = true, menuPath = "SNT>SNTLoader",
	initializer = "initialize")
public class SNTLoader extends DynamicCommand {

	@Parameter
	private DatasetIOService datasetIOService;
	@Parameter
	private DisplayService displayService;
	@Parameter
	private ImageDisplayService imageDisplayService;
	@Parameter
	private LegacyService legacyService;
	@Parameter
	private UIService uiService;

	@Parameter(required = false, label = "Image", style = FileWidget.OPEN_STYLE,
		callback = "sourceImageChanged")
	private File sourceFile;

	@Parameter(required = false, label = "Traces/(e)SWC file",
		style = FileWidget.OPEN_STYLE)
	private File tracesFile;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = "<html><body><div style='width:130;'><br><b>Tracing Options:")
	private String HEADER3;

	@Parameter(required = false, label = "Tracing channel", min = "1")
	private int channel;

	@Parameter(required = false, label = "Use three pane view")
	private boolean three_pane;

	@Parameter(label = "Use current image", callback = "loadActiveImage")
	private Button useCurrentImg;

	private ImagePlus sourceImp;
	private boolean loadActiveImage;

	@SuppressWarnings("unused")
	private void loadActiveImage() {
		sourceImp = legacyService.getImageMap().lookupImagePlus(imageDisplayService
			.getActiveImageDisplay());
		if (sourceImp == null) {
			uiService.showDialog("There are no images open");
			return;
		}
		if (sourceImp.getOriginalFileInfo() != null) {
			final String dir = sourceImp.getOriginalFileInfo().directory;
			final String file = sourceImp.getOriginalFileInfo().fileName;
			sourceFile = (dir == null || file == null) ? null : new File(dir + file);
		}
		loadActiveImage = true;
		sourceImageChanged();
	}

	private void sourceImageChanged() {

		if (sourceFile == null || !sourceFile.exists()) return;
		for (final String ext : new String[] { "traces", "swc" }) {
			final File candidate = SNT.findClosestPair(sourceFile, ext);
			if (candidate != null && candidate.exists()) {
				tracesFile = candidate;
				break;
			}

			if (sourceImp == null) return;
			final MutableModuleItem<Integer> channelInput = getInfo().getMutableInput(
				"channel", Integer.class);
			channelInput.setMaximumValue(sourceImp.getNChannels());
		}

	}

	@Override
	public void initialize() {
		// TODO: load defaults from prefService?
		sourceImp = legacyService.getImageMap().lookupImagePlus(imageDisplayService
			.getActiveImageDisplay());
		if (sourceImp == null) {
			final MutableModuleItem<Button> useCurrentImgInput = getInfo()
				.getMutableInput("useCurrentImg", Button.class);
			removeInput(useCurrentImgInput);
		}
	}

	@Override
	public void run() {

		if (loadActiveImage && sourceImp == null) {
			cancel("An image is required but none was found");
			return;
		}
		if (sourceFile == null || !sourceFile.exists()) {
			cancel("No valid image chosen.");
			return;
		}

		try { // sourceFile is valid but image is not displayed
			displayService.createDisplay(datasetIOService.open(sourceFile
				.getAbsolutePath()));
		}
		catch (final IOException exc) {
			cancel("Could not open\n" + sourceFile.getAbsolutePath());
			return;
		}

		// TODO: logic to load SNT
	}

	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(SNTLoader.class, true);
	}

}
