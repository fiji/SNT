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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultGenericTable;

import net.imagej.ImageJ;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.MultiTreeStatistics;
import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.gui.GuiUtils;

/**
 * GUI Command for measuring Tree(s).
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label="Measure...", initializer = "init")
public class DetailedMeasurementsCmd extends CommonDynamicCmd {

	private static final String TABLE_TITLE = "SNT Measurements";

	@Parameter(required=false, label="File/Directory:", description="<HTML><div WIDTH=500>Path to reonstruction "
			+ "file(s) to be measured. If a directory is specified, all of its reconstructions files will be "
			+ "measured. NB: The \"Browser\" prompt may not allow folders to be selected. In that case, select "
			+ "a file inside the folder of interest, then edit its path in the text field.")
	private File file;

	@Parameter(label = "<HTML>&nbsp;<br><b>Metrics:", persist = false, required = false, visibility = ItemVisibility.MESSAGE)
	private String SPACER1;

	@Parameter(label = MultiTreeStatistics.LENGTH)
	private boolean cableLength;

	@Parameter(label = MultiTreeStatistics.TERMINAL_LENGTH)
	private boolean terminalLength;

	@Parameter(label = MultiTreeStatistics.PRIMARY_LENGTH)
	private boolean primaryLength;

	@Parameter(label = MultiTreeStatistics.N_BRANCH_POINTS)
	private boolean nBPs;

	@Parameter(required = true, label = MultiTreeStatistics.N_TIPS)
	private boolean nTips;

	@Parameter(label = MultiTreeStatistics.N_BRANCHES)
	private boolean nBranches;

	@Parameter(label = MultiTreeStatistics.N_PRIMARY_BRANCHES)
	private boolean nPrimaryBranches;

	@Parameter(label = MultiTreeStatistics.N_TERMINAL_BRANCHES)
	private boolean nTerminalBranches;

	@Parameter(label = MultiTreeStatistics.STRAHLER_NUMBER)
	private boolean sNumber;

	@Parameter(label = MultiTreeStatistics.STRAHLER_RATIO)
	private boolean sRatio;

	@Parameter(label = MultiTreeStatistics.WIDTH)
	private boolean width;

	@Parameter(label = MultiTreeStatistics.HEIGHT)
	private boolean height;

	@Parameter(label = MultiTreeStatistics.DEPTH)
	private boolean depth;

	@Parameter(label = MultiTreeStatistics.MEAN_RADIUS)
	private boolean meanRadius;

	@Parameter(label = "<HTML>&nbsp;<br><b>Options:", persist = false, required = false, visibility = ItemVisibility.MESSAGE)
	private String SPACER2;

	@Parameter(label = "Action", choices = {"Choose", "Select All", "Select None"}, callback="actionChoiceSelected")
	private String actionChoice;

	@Parameter(label = "Distinguish compartments", description="<HTML><div WIDTH=500>Wether measurements "
			+ "should be grouped by cellular compartment (e.g., \"axon\", \"dendrites\", etc.)")
	private boolean splitByType;

	@Parameter(required = false)
	private Tree tree;

	private ArrayList<String> metrics;


	@SuppressWarnings("unused")
	private void init() {
		super.init(false);
		final MutableModuleItem<File> fileInput = getInfo().getMutableInput("file", File.class);
			resolveInput("multiTreeMappingLabels");
		if (tree != null) {
			fileInput.setLabel("");
			resolveInput("file");
		} 
	}

	@SuppressWarnings("unused")
	private void actionChoiceSelected() {
		if (actionChoice.contains("All")) {
			setAllCheckboxesEnabled(true);
		} else if (actionChoice.contains("None")) {
			setAllCheckboxesEnabled(false);
		}
	}

	private void setAllCheckboxesEnabled(final boolean enable) {
		cableLength = enable;
		depth = enable;
		height = enable;
		meanRadius = enable;
		nBPs = enable;
		nBranches = enable;
		nPrimaryBranches = enable;
		nTerminalBranches = enable;
		nTips = enable;
		primaryLength = enable;
		sNumber = enable;
		sRatio = enable;
		terminalLength = enable;
		width = enable;
		actionChoice = "Choose";
	}

	@Override
	public void run() {
		metrics = new ArrayList<>();
		if(cableLength) metrics.add(MultiTreeStatistics.LENGTH);
		if(terminalLength) metrics.add(MultiTreeStatistics.TERMINAL_LENGTH);
		if(primaryLength) metrics.add(MultiTreeStatistics.PRIMARY_LENGTH);
		if(nBPs) metrics.add(MultiTreeStatistics.N_BRANCH_POINTS);
		if(nTips) metrics.add(MultiTreeStatistics.N_TIPS);
		if(nBranches) metrics.add(MultiTreeStatistics.N_BRANCHES);
		if(nPrimaryBranches) metrics.add(MultiTreeStatistics.N_PRIMARY_BRANCHES);
		if(nTerminalBranches) metrics.add(MultiTreeStatistics.N_TERMINAL_BRANCHES);
		if(sNumber) metrics.add(MultiTreeStatistics.STRAHLER_NUMBER);
		if(sRatio) metrics.add(MultiTreeStatistics.STRAHLER_RATIO);
		if(width) metrics.add(MultiTreeStatistics.WIDTH);
		if(height) metrics.add(MultiTreeStatistics.HEIGHT);
		if(depth) metrics.add(MultiTreeStatistics.DEPTH);
		if(meanRadius) metrics.add(MultiTreeStatistics.MEAN_RADIUS);
	
		if (metrics.isEmpty()) {
			cancel("No metrics chosen.");
			return;
		}

		if (tree != null) {
			measure(tree);
			return;
		}

		if (file == null || !file.exists() || !file.canRead()) {
			cancel("Specified path is not valid.");
			return;
		}

		final List<Tree> trees;
		if (file.isDirectory()) {
			trees = Tree.listFromDir(file.getAbsolutePath());
		} else {
			trees = new ArrayList<>();
			final Tree tree = Tree.fromFile(file.getAbsolutePath());
			if (tree != null) trees.add(tree);
		}
		if (trees.isEmpty()) {
			cancel("No reconstruction files could be retrieved from specified path");
			return;
		}
		final int n = trees.size();
		for (int i = 0; i < n; i++) {
			final Tree tree = trees.get(i);
			statusService.showStatus(i, n, tree.getLabel());
			measure(tree);
		}
		resetUI();
	}

	private void measure(final Tree tree) {
		final TreeAnalyzer analyzer = new TreeAnalyzer(tree);
		analyzer.setContext(getContext());
		final DefaultGenericTable table = (sntService.isActive()) ? sntService.getTable() : new DefaultGenericTable();
		analyzer.setTable(table, TABLE_TITLE);
		analyzer.measure(metrics, splitByType);
	}


	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final Tree tree = sntService.demoTrees().get(0);
		final Map<String, Object> input = new HashMap<>();
		input.put("tree", tree);
		ij.command().run(DetailedMeasurementsCmd.class, true, input);
	}
}
