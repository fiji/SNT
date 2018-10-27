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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import net.imglib2.display.ColorTable;
import tracing.SNTService;
import tracing.gui.GuiUtils;
import tracing.plot.TreePlot3D;

/**
 * Implements Reconstruction Viewer's 'Add Color Legend' command
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Add Color Legend", initializer = "init")
public class ColorRampCmd extends DynamicCommand {

	@Parameter
	private SNTService sntService;

	@Parameter
	private LUTService lutService;

	@Parameter(label = "Min")
	private float min;

	@Parameter(label = "Max")
	private float max;

	@Parameter(label = "LUT", callback = "lutChoiceChanged")
	private String lutChoice = "Ice.lut";

	@Parameter(required = false, label = "<HTML>&nbsp;", persist = false)
	private ColorTable colorTable;

	@Parameter(required = false)
	private TreePlot3D recViewer;

	private Map<String, URL> luts;

	private void lutChoiceChanged() {
		try {
			colorTable = lutService.loadLUT(luts.get(lutChoice));
		} catch (final IOException ignored) {
			// move on
		}
	}

	@SuppressWarnings("unused")
	private void init() {
		// see net.imagej.lut.LUTSelector
		luts = lutService.findLUTs();
		final ArrayList<String> choices = new ArrayList<>();
		for (final Map.Entry<String, URL> entry : luts.entrySet()) {
			choices.add(entry.getKey());
		}
		Collections.sort(choices);
		final MutableModuleItem<String> input = getInfo().getMutableInput("lutChoice", String.class);
		input.setChoices(choices);
		input.setValue(this, lutChoice);
		lutChoiceChanged();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
			cancel("Invalid Limits " + min + "-" + max);
		} else {
			try {
				if (recViewer == null) {
					recViewer = sntService.getReconstructionViewer();
				}
				recViewer.addColorBarLegend(colorTable, min, max);
			} catch (final UnsupportedOperationException | NullPointerException exc) {
				cancel("SNT's Reconstruction Viewer is not available");
			}
		}
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(ColorRampCmd.class, true);
	}

}