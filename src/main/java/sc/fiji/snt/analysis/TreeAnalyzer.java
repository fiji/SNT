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

package sc.fiji.snt.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.scijava.table.DefaultGenericTable;

import net.imagej.ImageJ;

import org.scijava.app.StatusService;
import org.scijava.command.ContextCommand;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import sc.fiji.snt.Path;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.Tree;
import sc.fiji.snt.util.PointInImage;

/**
 * Class for analysis of {@link Tree}s
 *
 * @author Tiago Ferreira
 */
@Plugin(type = ContextCommand.class, visible = false)
public class TreeAnalyzer extends ContextCommand {

	@Parameter
	protected StatusService statusService;

	@Parameter
	protected DisplayService displayService;

	/** Flag for {@value #BRANCH_ORDER} analysis. */
	public static final String BRANCH_ORDER = "Branch order";

	/** Flag for {@value #INTER_NODE_DISTANCE} analysis. */
	public static final String INTER_NODE_DISTANCE = "Inter-node distance";

	/** Flag for {@value #TERMINAL_LENGTH} analysis. */
	public static final String TERMINAL_LENGTH = "Length of terminal branches";

	/** Flag for {@value #PRIMARY_LENGTH} analysis. */
	public static final String PRIMARY_LENGTH = "Length of primary branches";

	/** Flag for {@value #INTER_NODE_DISTANCE_SQUARED} analysis. */
	public static final String INTER_NODE_DISTANCE_SQUARED =
		"Inter-node distance (squared)";

	/** Flag for {@value #LENGTH} analysis. */
	public static final String LENGTH = "Length";

	/** Flag for {@value #N_BRANCH_POINTS} analysis. */
	public static final String N_BRANCH_POINTS = "No. of branch points";

	/** Flag for {@value #N_NODES} analysis. */
	public static final String N_NODES = "No. of nodes";

	/** Flag for {@value #NODE_RADIUS} analysis. */
	public static final String NODE_RADIUS = "Node radius";

	/** Flag for {@value #MEAN_RADIUS} analysis. */
	public static final String MEAN_RADIUS = "Path mean radius";

	/** Flag for {@value #X_COORDINATES} analysis. */
	public static final String X_COORDINATES = "X coordinates";

	/** Flag for {@value #Y_COORDINATES} analysis. */
	public static final String Y_COORDINATES = "Y coordinates";

	/** Flag for {@value #Z_COORDINATES} analysis. */
	public static final String Z_COORDINATES = "Z coordinates";

	/**
	 * Flag for analysis of {@value #VALUES}, an optional numeric property that
	 * can be assigned to Path nodes (typically voxel intensities, assigned via
	 * {@link PathProfiler}. Note that an {@link IllegalArgumentException} is
	 * triggered if no values have been assigned to the tree being analyzed.
	 * 
	 * @see Path#hasNodeValues()
	 * @see PathProfiler#assignValues()
	 */
	public static final String VALUES = "Node intensity values";

	public static final String[] COMMON_MEASUREMENTS = { //
		BRANCH_ORDER, //
		LENGTH, //
		PRIMARY_LENGTH, //
		TERMINAL_LENGTH, //
		N_BRANCH_POINTS, //
		N_NODES, //
		MEAN_RADIUS, //
		NODE_RADIUS, //
		X_COORDINATES, //
		Y_COORDINATES, //
		Z_COORDINATES };
	protected Tree tree;
	private Tree unfilteredTree;
	private HashSet<Path> primaryBranches;
	private HashSet<Path> terminalBranches;
	private HashSet<PointInImage> joints;
	private HashSet<PointInImage> tips;
	protected DefaultGenericTable table;
	private String tableTitle;

	private int fittedPathsCounter = 0;
	private int unfilteredPathsFittedPathsCounter = 0;

	/**
	 * Instantiates a new Tree analyzer.
	 *
	 * @param tree Collection of Paths to be analyzed. Note that null Paths are
	 *          discarded. Also, when a Path has been fitted and
	 *          {@link Path#getUseFitted()} is true, its fitted 'flavor' is used.
	 * @see #getParsedTree()
	 */
	public TreeAnalyzer(final Tree tree) {
		this.tree = new Tree();
		this.tree.setLabel(tree.getLabel());
		for (final Path p : tree.list()) {
			if (p == null) continue;
			Path pathToAdd;
			// If fitted flavor of path exists use it instead
			if (p.getUseFitted() && p.getFitted() != null) {
				pathToAdd = p.getFitted();
				fittedPathsCounter++;
			}
			else {
				pathToAdd = p;
			}
			this.tree.add(pathToAdd);
		}
		unfilteredPathsFittedPathsCounter = fittedPathsCounter;
	}

	/**
	 * Restricts analysis to Paths sharing the specified SWC flag(s).
	 *
	 * @param types the allowed SWC flags (e.g., {@link Path#SWC_AXON}, etc.)
	 */
	public void restrictToSWCType(final int... types) {
		initializeSnapshotTree();
		tree = tree.subTree(types);
	}

	/**
	 * Ignores Paths sharing the specified SWC flag(s).
	 *
	 * @param types the SWC flags to be ignored (e.g., {@link Path#SWC_AXON},
	 *          etc.)
	 */
	public void ignoreSWCType(final int... types) {
		initializeSnapshotTree();
		final ArrayList<Integer> allowedTypes = Path.getSWCtypes();
		for (final int type : types) {
			allowedTypes.remove(Integer.valueOf(type));
		}
		tree = tree.subTree(allowedTypes.stream().mapToInt(i -> i).toArray());
	}

	/**
	 * Restricts analysis to Paths sharing the specified branching order(s).
	 *
	 * @param orders the allowed branching orders
	 */
	public void restrictToOrder(final int... orders) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.list().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final boolean valid = Arrays.stream(orders).anyMatch(t -> t == p
				.getOrder());
			if (!valid) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	/**
	 * Restricts analysis to paths having the specified number of nodes.
	 *
	 * @param minSize the smallest number of nodes a path must have in order to be
	 *          analyzed. Set it to -1 to disable minSize filtering
	 * @param maxSize the largest number of nodes a path must have in order to be
	 *          analyzed. Set it to -1 to disable maxSize filtering
	 */
	public void restrictToSize(final int minSize, final int maxSize) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.list().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final int size = p.size();
			if ((minSize > 0 && size < minSize) || (maxSize > 0 && size > maxSize)) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	/**
	 * Restricts analysis to paths sharing the specified length range.
	 *
	 * @param lowerBound the smallest length a path must have in order to be
	 *          analyzed. Set it to Double.NaN to disable lowerBound filtering
	 * @param upperBound the largest length a path must have in order to be
	 *          analyzed. Set it to Double.NaN to disable upperBound filtering
	 */
	public void restrictToLength(final double lowerBound,
		final double upperBound)
	{
		initializeSnapshotTree();
		final Iterator<Path> it = tree.list().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final double length = p.getLength();
			if (length < lowerBound || length > upperBound) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	/**
	 * Restricts analysis to Paths containing the specified string in their name.
	 *
	 * @param pattern the string to search for
	 */
	public void restrictToNamePattern(final String pattern) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.list().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			if (!p.getName().contains(pattern)) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	private void initializeSnapshotTree() {
		if (unfilteredTree == null) {
			unfilteredTree = new Tree(tree.list());
			unfilteredTree.setLabel(tree.getLabel());
		}
	}

	/**
	 * Removes any filtering restrictions that may have been set. Once called,
	 * subsequent analysis will use all paths initially parsed by the constructor.
	 * Does nothing if no paths are currently being excluded from the analysis.
	 */
	public void resetRestrictions() {
		if (unfilteredTree == null) return; // no filtering has occurred
		tree.replaceAll(unfilteredTree.list());
		joints = null;
		primaryBranches = null;
		terminalBranches = null;
		tips = null;
		fittedPathsCounter = unfilteredPathsFittedPathsCounter;
	}

	private void updateFittedPathsCounter(final Path filteredPath) {
		if (fittedPathsCounter > 0 && filteredPath.isFittedVersionOfAnotherPath())
			fittedPathsCounter--;
	}

	/**
	 * Returns the set of parsed Paths.
	 *
	 * @return the set of paths currently being considered for analysis.
	 * @see #resetRestrictions()
	 */
	public Tree getParsedTree() {
		return tree;
	}

	/**
	 * Outputs a summary of the current analysis to the Analyzer table using the
	 * default Tree label.
	 *
	 * @param groupByType if true measurements are grouped by SWC-type flag
	 * @see #run()
	 * @see #setTable(DefaultGenericTable)
	 */
	public void summarize(final boolean groupByType) {
		summarize(tree.getLabel(), groupByType);
	}

	/**
	 * Outputs a summary of the current analysis to the Analyzer table.
	 *
	 * @param rowHeader the String to be used as label for the summary
	 * @param groupByType if true measurements are grouped by SWC-type flag
	 * @see #run()
	 * @see #setTable(DefaultGenericTable)
	 */
	public void summarize(final String rowHeader, final boolean groupByType) {
		if (table == null) table = new DefaultGenericTable();
		if (groupByType) {
			final int[] types = tree.getSWCTypes().stream().mapToInt(v -> v)
				.toArray();
			for (final int type : types) {
				restrictToSWCType(type);
				final String label = Path.getSWCtypeName(type, true);
				final int row = getNextRow(rowHeader);
				measureTree(row, label);
				table.set(getCol("SWC Type"), row, label);
				resetRestrictions();
			}
		}
		else {
			measureTree(getNextRow(rowHeader), "All types");
		}
	}

	private int getNextRow(final String rowHeader) {
		table.appendRow(rowHeader);
		return table.getRowCount() - 1;
	}

	private void measureTree(final int row, final String type) {
		table.set(getCol("SWC Type"), row, type);
		table.set(getCol("# Paths"), row, getNPaths());
		table.set(getCol("# Branch Points"), row, getBranchPoints().size());
		table.set(getCol("# Tips"), row, getTips().size());
		table.set(getCol("Cable Length"), row, getCableLength());
		table.set(getCol("# Primary Branches (PB)"), row, getPrimaryBranches().size());
		table.set(getCol("# Terminal Branches (TB)"), row, getTerminalBranches().size());
		table.set(getCol("Sum length PB"), row, getPrimaryLength());
		table.set(getCol("Sum length TB"), row, getTerminalLength());
		table.set(getCol("Strahler Root No."), row, getStrahlerRootNumber());
		table.set(getCol("# Fitted Paths"), row, fittedPathsCounter);
		table.set(getCol("# Single-point Paths"), row, getSinglePointPaths());
	}

	/**
	 * Sets the Analyzer table.
	 *
	 * @param table the table to be used by the analyzer
	 * @see #summarize(boolean)
	 */
	public void setTable(final DefaultGenericTable table) {
		this.table = table;
	}

	/**
	 * Sets the table.
	 *
	 * @param table the table to be used by the analyzer
	 * @param title the title of the table display window
	 */
	public void setTable(final DefaultGenericTable table, final String title) {
		this.table = table;
		this.tableTitle = title;
	}

	/**
	 * Gets the table currently being used by the Analyzer
	 *
	 * @return the table
	 */
	public DefaultGenericTable getTable() {
		return table;
	}

	/**
	 * Generates detailed summaries in which measurements are grouped by SWC-type
	 * flags
	 *
	 * @see #summarize(String, boolean)
	 */
	@Override
	public void run() {
		if (tree.list() == null || tree.list().isEmpty()) {
			cancel("No Paths to Measure");
			return;
		}
		statusService.showStatus("Measuring Paths...");
		summarize(true);
		if (tree.getSWCTypes().size() > 1) summarize(false);
		updateAndDisplayTable();
		statusService.clearStatus();
	}

	/**
	 * Updates and displays the Analyzer table.
	 */
	public void updateAndDisplayTable() {
		final String displayName = (tableTitle == null) ? "Path Measurements"
			: tableTitle;
		final Display<?> display = displayService.getDisplay(displayName);
		if (display != null && display.isDisplaying(table)) {
			display.update();
		}
		else {
			displayService.createDisplay(displayName, table);
		}
	}

	protected int getCol(final String header) {
		int idx = table.getColumnIndex(header);
		if (idx == -1) {
			table.appendColumn(header);
			idx = table.getColumnCount() - 1;
		}
		return idx;
	}

	private int getSinglePointPaths() {
		return (int) tree.list().stream().filter(p -> p.size() == 1).count();
	}

	/**
	 * Gets the no. of paths parsed by the Analyzer.
	 *
	 * @return the number of paths
	 */
	public int getNPaths() {
		return tree.list().size();
	}

	/**
	 * Retrieves all the Paths in the analyzed Tree tagged as primary.
	 *
	 * @return the set of primary paths.
	 * @see #getPrimaryBranches()
	 */
	public Set<Path> getPrimaryPaths() {
		final HashSet<Path> primaryPaths = new HashSet<>();
		for (final Path p : tree.list()) {
			if (p.isPrimary()) primaryPaths.add(p);
		}
		return primaryPaths;
	}

	/**
	 * Retrieves the primary branches of the analyzed Tree. A primary branch
	 * corresponds to the section of a primary Path between its origin and its
	 * closest branch-point.
	 *
	 * @return the set containing the primary branches. Note that as per
	 *         {@link Path#getSection(int, int)}, these branches will not carry any
	 *         connectivity information.
	 * @see #getPrimaryPaths()
	 * @see #restrictToOrder(int...)
	 */
	public Set<Path> getPrimaryBranches() {
		primaryBranches = new HashSet<>();
		for (final Path p : tree.list()) {
			if (!p.isPrimary()) continue;
			final TreeSet<Integer> joinIndices = p.findJunctionIndices();
			final int firstJointIdx = (joinIndices.isEmpty()) ? p.size() - 1 : joinIndices.first();
			primaryBranches.add(p.getSection(0, firstJointIdx));
		}
		return primaryBranches;
	}

	/**
	 * Retrieves the terminal branches of the analyzed Tree. A terminal branch
	 * corresponds to the section of a terminal Path between its last branch-point
	 * and its terminal point (tip).
	 *
	 * @return the set containing terminal branches. Note that as per
	 *         {@link Path#getSection(int, int)}, these branches will not carry any
	 *         connectivity information.
	 * @see #getPrimaryBranches
	 * @see #restrictToOrder(int...)
	 */
	public Set<Path> getTerminalBranches() {
		terminalBranches = new HashSet<>();
		if (joints == null) getBranchPoints();
		for (final Path p : tree.list()) {
			final int lastNodeIdx = p.size() - 1;
			if (joints.contains(p.getNode(lastNodeIdx))) {
				continue; // not a terminal branch
			}
			final TreeSet<Integer> joinIndices = p.findJunctionIndices();
			final int lastJointIdx = (joinIndices.isEmpty()) ? 0 : joinIndices.last();
			terminalBranches.add(p.getSection(lastJointIdx, lastNodeIdx));
		}
		return terminalBranches;
	}

	/**
	 * Gets the position of all the tips in the analyzed tree.
	 *
	 * @return the set of terminal points
	 */
	public Set<PointInImage> getTips() {

		// retrieve all start/end points
		tips = new HashSet<>();
		for (final Path p : tree.list()) {
			tips.add(p.getNode(p.size() - 1));
		}
		// now remove any joint-associated point
		if (joints == null) getBranchPoints();
		tips.removeAll(joints);
		return tips;

	}

	/**
	 * Gets the position of all the branch points in the analyzed tree.
	 *
	 * @return the branch points positions
	 */
	public Set<PointInImage> getBranchPoints() {
		joints = new HashSet<>();
		for (final Path p : tree.list()) {
			joints.addAll(p.findJunctions());
		}
		return joints;
	}

	/**
	 * Gets the cable length.
	 *
	 * @return the cable length of the tree
	 */
	public double getCableLength() {
		return sumLength(tree.list());
	}

	/**
	 * Gets the cable length of primary branches.
	 *
	 * @return the length sum of all primary branches
	 * @see #getPrimaryBranches()
	 */
	public double getPrimaryLength() {
		if (primaryBranches == null) getPrimaryBranches();
		return sumLength(primaryBranches);
	}

	/**
	 * Gets the cable length of terminal branches
	 *
	 * @return the length sum of all terminal branches
	 * @see #getTerminalBranches()
	 */
	public double getTerminalLength() {		
		if (terminalBranches == null) getTerminalBranches();
		return sumLength(terminalBranches);
	}

	/**
	 * Gets the Strahler-Horton root number of the analyzed tree
	 *
	 * @return the Strahler root number, or -1 if tree has no defined order
	 */
	public int getStrahlerRootNumber() {
		int root = -1;
		for (final Path p : tree.list()) {
			final int order = p.getOrder();
			if (order > root) root = order;
		}
		return root;
	}

	private double sumLength(final Collection<Path> paths) {
		return paths.stream().mapToDouble(p -> p.getLength()).sum();
	}


	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final SNTService sntService = ij.context().getService(SNTService.class);
		sntService.initialize(sntService.demoTreeImage(), true);
		final Tree tree = sntService.demoTree();
		sntService.loadTree(tree);
		final TreeAnalyzer analyzer = new TreeAnalyzer(tree);
		for (Path p : analyzer.getPrimaryBranches()) {
			sntService.getPlugin().getPathAndFillManager().addPath(p);
		}
		sntService.getPlugin().updateAllViewers();
	}
}
