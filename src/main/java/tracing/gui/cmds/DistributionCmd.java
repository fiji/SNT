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

import java.util.HashMap;
import java.util.Map;

import net.imagej.ImageJ;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import tracing.SNT;
import tracing.Tree;
import tracing.analysis.TreeAnalyzer;
import tracing.analysis.TreeStatistics;
import tracing.gui.GuiUtils;

/**
 * Command for plotting distributions of morphometric properties of
 * {@link Tree}s
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false,
	label = "Distribution of Morphometric Traits")
public class DistributionCmd implements Command {

	@Parameter(required = true, label = "Measurement", choices = {
		TreeAnalyzer.BRANCH_ORDER, TreeAnalyzer.INTER_NODE_DISTANCE,
		TreeAnalyzer.LENGTH, TreeAnalyzer.N_BRANCH_POINTS, TreeAnalyzer.N_NODES,
		TreeAnalyzer.NODE_RADIUS, TreeAnalyzer.MEAN_RADIUS,
		TreeAnalyzer.X_COORDINATES, TreeAnalyzer.Y_COORDINATES,
		TreeAnalyzer.Z_COORDINATES })
	private String measurementChoice;

	@Parameter(required = true)
	private Tree tree;

	@Override
	public void run() {
		final TreeStatistics treeStats = new TreeStatistics(tree);
		treeStats.getHistogram(measurementChoice).setVisible(true);
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Tree tree = new Tree(SNT.randomPaths());
		tree.setLabel("Bogus test");
		final Map<String, Object> input = new HashMap<>();
		input.put("tree", tree);
		ij.command().run(DistributionCmd.class, true, input);
	}

}
