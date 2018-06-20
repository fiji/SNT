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
import tracing.PathWindow;
import tracing.SNT;
import tracing.Tree;
import tracing.analysis.TreeColorizer;
import tracing.analysis.TreePlot;

/**
 * Command for color coding trees according to their properties using
 * {@link TreeColorizer} and {@link TreePlot}
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Tree Color Coder", initializer = "init")
public class TreeColorizerCmd extends DynamicCommand {

	@Parameter
	private PrefService prefService;

	@Parameter
	private LUTService lutService;

	@Parameter
	private StatusService statusService;

	@Parameter(required = true, label = "Color by", choices = { TreeColorizer.BRANCH_ORDER, TreeColorizer.LENGTH,
			TreeColorizer.N_BRANCH_POINTS, TreeColorizer.N_NODES, TreeColorizer.MEAN_RADIUS, TreeColorizer.NODE_RADIUS,
			TreeColorizer.X_COORDINATES, TreeColorizer.Y_COORDINATES, TreeColorizer.Z_COORDINATES })
	private String measurementChoice;

	@Parameter(label = "LUT", callback = "lutChoiceChanged")
	private String lutChoice;

	@Parameter(required = false, label = "<HTML>&nbsp;")
	private ColorTable colorTable;

	@Parameter(required = false, label = "Show SVG plot")
	private boolean showPlot;

	@Parameter(required = false, label = "Remove Existing Color Coding", callback = "removeColorCoding")
	private Button removeColorCoding;

	@Parameter(required = true)
	private Tree tree;

	@Parameter(required = false)
	private PathWindow manager;

	private Map<String, URL> luts;
	private TreePlot plot;

	@Override
	public void run() {
		if (tree == null || tree.isEmpty())
			cancel("Invalid input tree");
		statusService.showStatus("Applying Color Code...");
		SNT.log("Color Coding Tree (" + measurementChoice + ") using " + lutChoice);
		plot = new TreePlot(context());
		plot.addTree(tree, measurementChoice, lutChoice);
		if (showPlot) {
			SNT.log("Plotting...");
			plot.addLookupLegend();
			plot.showPlot();
		}
		SNT.log("Finished...");
		if (manager != null)
			manager.refresh();
		statusService.clearStatus();
	}

	@SuppressWarnings("unused")
	private void init() {
		if (lutChoice == null)
			lutChoice = prefService.get(getClass(), "lutChoice", "mpl-viridis.lut");
		setLUTs();
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
			manager.refresh();
		statusService.showStatus("Color code removed...");
	}

	/**
	 * IDE debug method
	 *
	 * @throws IOException
	 **/
	public static void main(final String[] args) throws IOException {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input = new HashMap<>();
		final Tree tree = new Tree(DistributionCmd.randomPaths());
		input.put("tree", tree);
		ij.command().run(TreeColorizerCmd.class, true, input);
	}

}
