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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import net.imglib2.display.ColorTable;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.Button;

import tracing.Path;
import tracing.PathManagerUI;
import tracing.SNT;
import tracing.SNTService;
import tracing.Tree;
import tracing.analysis.TreeColorizer;
import tracing.plot.TreePlot2D;
import tracing.plot.TreePlot3D;

/**
 * Command for color coding trees according to their properties using
 * {@link TreeColorizer} with options to display result in
 *  {@link TreePlot2D} and {@link TreePlot3D}
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Tree Color Coder", initializer = "init")
public class TreeColorizerCmd extends DynamicCommand {

	@Parameter
	private SNTService sntService;

	@Parameter
	private PrefService prefService;

	@Parameter
	private LUTService lutService;

	@Parameter
	private StatusService statusService;

	@Parameter(required = true, label = "Color by", choices = { //
			TreeColorizer.BRANCH_ORDER, TreeColorizer.LENGTH, TreeColorizer.N_BRANCH_POINTS, //
			TreeColorizer.N_NODES, TreeColorizer.PATH_DISTANCE, TreeColorizer.MEAN_RADIUS, //
			TreeColorizer.NODE_RADIUS, TreeColorizer.X_COORDINATES, TreeColorizer.Y_COORDINATES, //
			TreeColorizer.Z_COORDINATES, TreeColorizer.FIRST_TAG})
	private String measurementChoice;

	@Parameter(label = "LUT", callback = "lutChoiceChanged")
	private String lutChoice;

	@Parameter(required = false, label = "<HTML>&nbsp;")
	private ColorTable colorTable;

	@Parameter(required = false, label = "Show in Reconstruction Viewer")
	private boolean showInRecViewer = true;

	@Parameter(required = false, label = "Create 2D plot")
	private boolean showPlot = false;

	@Parameter(required = false, label = "Remove Existing Color Coding", callback = "removeColorCoding")
	private Button removeColorCoding;

	@Parameter(required = true)
	private Tree tree;

	private Map<String, URL> luts;
	private TreePlot2D plot;

	private PathManagerUI manager;
	private TreePlot3D recViewer;


	@Override
	public void run() {
		if (tree == null || tree.isEmpty())
			cancel("Invalid input tree");
		statusService.showStatus("Applying Color Code...");
		SNT.log("Color Coding Tree (" + measurementChoice + ") using " + lutChoice);
		final TreeColorizer colorizer = new TreeColorizer(context());
		colorizer.setMinMax(Double.NaN, Double.NaN);
		try {
			colorizer.colorize(tree, measurementChoice, colorTable);
		} catch (IllegalArgumentException exc) {
			cancel(exc.getMessage());
			return;
		}
		final double[] minMax = colorizer.getMinMax();
		if (showPlot) {
			SNT.log("Creating 2D plot...");
			plot = new TreePlot2D(context());
			plot.addTree(tree);
			plot.addColorBarLegend(colorTable, minMax[0], minMax[1]);
			plot.showPlot();
		}
		if (showInRecViewer) {
			SNT.log("Displaying in Reconstruction Viewer...");
			final boolean newInstance = recViewer == null;
			final boolean sntActive = sntService.isActive();
			recViewer = (sntActive) ? sntService.getReconstructionViewer() : new TreePlot3D(getContext());
			recViewer.addColorBarLegend(colorTable, (float) minMax[0], (float) minMax[1]);
			if (sntActive) {
				recViewer.syncPathManagerList();
			} else if (newInstance) {
				recViewer.add(tree);
			}
			recViewer.show();
		}
		SNT.log("Finished...");
		if (manager != null)
			manager.update();
		statusService.clearStatus();
	}

	@SuppressWarnings("unused")
	private void init() {
		if (lutChoice == null)
			lutChoice = prefService.get(getClass(), "lutChoice", "mpl-viridis.lut");
		setLUTs();
		if (!sntService.isActive()) return;
		manager = sntService.getUI().getPathManager();
		recViewer = sntService.getUI().getReconstructionViewer(false);
	}

	private void setLUTs() {
		luts = lutService.findLUTs();
		if (luts.isEmpty()) {
			cancel("This command requires at least one LUT to be installed.");
		}
		final ArrayList<String> choices = new ArrayList<>();
		for (final Map.Entry<String, URL> entry : luts.entrySet()) {
			choices.add(entry.getKey());
		}

		// define a valid LUT choice
		Collections.sort(choices);
		if (lutChoice == null || !choices.contains(lutChoice)) {
			lutChoice = choices.get(0);
		}

		final MutableModuleItem<String> input = getInfo().getMutableInput("lutChoice", String.class);
		input.setChoices(choices);
		input.setValue(this, lutChoice);
		lutChoiceChanged();
	}

	private void lutChoiceChanged() {
		try {
			colorTable = lutService.loadLUT(luts.get(lutChoice));
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unused")
	private void removeColorCoding() {
		for (final Path p : tree.list()) {
			p.setColor(null);
			p.setNodeColors(null);
		}
		if (manager != null)
			manager.update();
		statusService.showStatus("Color code removed...");
	}

	/* IDE debug method **/
	public static void main(final String[] args) throws IOException {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input = new HashMap<>();
		final Tree tree = new Tree(DistributionCmd.randomPaths());
		input.put("tree", tree);
		ij.command().run(TreeColorizerCmd.class, true, input);
	}

}
