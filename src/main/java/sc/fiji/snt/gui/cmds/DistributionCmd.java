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

package sc.fiji.snt.gui.cmds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.ImageJ;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import sc.fiji.snt.analysis.PathProfiler;
import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.analysis.TreeColorMapper;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.TreeStatistics;
import sc.fiji.snt.gui.GuiUtils;

/**
 * Command for plotting distributions of morphometric properties of
 * {@link Tree}s
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false,
	label = "Distribution Analysis", initializer = "init")
public class DistributionCmd extends CommonDynamicCmd {

	@Parameter
	private PrefService prefService;

	@Parameter(required = true, label = "Measurement")
	private String measurementChoice;

	@Parameter(required = true)
	private Tree tree;

	@Parameter(required = false, visibility = ItemVisibility.INVISIBLE)
	private boolean setValuesFromSNTService;

	protected void init() {
		super.init(false);
		final MutableModuleItem<String> measurementChoiceInput = getInfo()
			.getMutableInput("measurementChoice", String.class);
		final List<String> choices = new ArrayList<>();
		Collections.addAll(choices, TreeAnalyzer.COMMON_MEASUREMENTS);
		if (setValuesFromSNTService) choices.add(TreeAnalyzer.VALUES);
		Collections.sort(choices);
		measurementChoiceInput.setChoices(choices);
		measurementChoiceInput.setValue(this, prefService.get(getClass(),
			"measurementChoice", TreeAnalyzer.PATH_ORDER));
		resolveInput("setValuesFromSNTService");
	}

	@Override
	public void run() {
		if (setValuesFromSNTService && TreeColorMapper.VALUES.equals(
			measurementChoice))
		{
			SNTUtils.log("Assigning values...");
			final PathProfiler profiler = new PathProfiler(tree, sntService
				.getPlugin().getLoadedDataAsImp());
			profiler.assignValues();
		}
		final TreeStatistics treeStats = new TreeStatistics(tree);
		treeStats.getHistogram(measurementChoice).setVisible(true);
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Tree tree = new Tree(SNTUtils.randomPaths());
		tree.setLabel("Bogus test");
		final Map<String, Object> input = new HashMap<>();
		input.put("tree", tree);
		ij.command().run(DistributionCmd.class, true, input);
	}

}
