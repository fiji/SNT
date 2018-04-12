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

package tracing.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.stream.IntStream;

import net.imagej.table.DefaultGenericTable;

import org.scijava.app.StatusService;
import org.scijava.command.ContextCommand;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import tracing.Path;
import tracing.Tree;
import tracing.util.PointInImage;

/**
 * Class for analysis of Trees
 *
 * @author Tiago Ferreira
 */
@Plugin(type = ContextCommand.class, visible = false)
public class TreeAnalyzer extends ContextCommand {

	@Parameter
	protected StatusService statusService;

	@Parameter
	protected DisplayService displayService;

	public static final String BRANCH_ORDER = "Branch order";
	public static final String INTER_NODE_DISTANCE = "Inter-node distance";
	public static final String LENGTH = "Length";
	public static final String N_BRANCH_POINTS = "No. of branch points";
	public static final String N_NODES = "No. of nodes";
	public static final String NODE_RADIUS = "Node radius";
	public static final String MEAN_RADIUS = "Path mean radius";
	public static final String X_COORDINATES = "X coordinates";
	public static final String Y_COORDINATES = "Y coordinates";
	public static final String Z_COORDINATES = "Z coordinates";

	protected Tree tree;
	private Tree unfilteredTree;
	private HashSet<Path> primaries;
	private HashSet<Path> terminals;
	private HashSet<PointInImage> joints;
	private HashSet<PointInImage> tips;
	protected DefaultGenericTable table;
	private String tableTitle;

	private int fittedPathsCounter = 0;
	private int unfilteredPathsFittedPathsCounter = 0;

	/**
	 * Instantiates a new Tree analyzer.
	 *
	 * @param tree
	 *            Collection of Paths to be analyzed. Note that null Paths are
	 *            discarded. Also, when a Path has been fitted and its
	 *            {@link Path#getUseFitted()} is true, its fitted flavor is used.
	 *
	 * @see #getParsedTree()
	 */
	public TreeAnalyzer(final Tree tree) {
		this.tree = new Tree();
		for (final Path p : tree.getPaths()) {
			if (p == null)
				continue;
			Path pathToAdd;
			// If fitted flavor of path exists use it instead
			if (p.getUseFitted() && p.getFitted() != null) {
				pathToAdd = p.getFitted();
				fittedPathsCounter++;
			} else {
				pathToAdd = p;
			}
			this.tree.addPath(pathToAdd);
		}
		unfilteredPathsFittedPathsCounter = fittedPathsCounter;
	}

	/**
	 * Restricts analysis to Paths sharing the specified SWC flag(s).
	 *
	 * @param types
	 *            the allowed SWC flags (e.g., {@link Path.SWC_AXON}, etc.)
	 */
	public void restrictToSWCType(final int... types) {
		initializeSnapshotTree();
		tree = tree.subTree(types);
	}

	/**
	 * Ignores Paths sharing the specified SWC flag(s).
	 *
	 * @param types
	 *            the SWC flags to be ignored (e.g., {@link Path.SWC_AXON}, etc.)
	 */
	public void ignoreSWCType(final int... types) {
		initializeSnapshotTree();
		final ArrayList<Integer> allowedTypes = Path.getSWCtypes();
		for (final int type : types) {
			if (allowedTypes.contains(type))
				allowedTypes.remove(Integer.valueOf(type));
		}
		tree = tree.subTree(allowedTypes.stream().mapToInt(i -> i).toArray());
	}

	/**
	 * Restricts analysis to Paths sharing the specified branching order(s).
	 *
	 * @param orders
	 *            the allowed branching orders
	 */
	public void restrictToOrder(final int... orders) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.getPaths().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final boolean valid = Arrays.stream(orders).anyMatch(t -> t == p.getOrder());
			if (!valid) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	/**
	 * Restricts analysis to paths having the specified number of nodes.
	 *
	 * @param minSize
	 *            the smallest number of nodes a path must have in order to be
	 *            analyzed. Set it to -1 to disable minSize filtering
	 * @param maxSize
	 *            the largest number of nodes a path must have in order to be
	 *            analyzed. Set it to -1 to disable maxSize filtering
	 */
	public void restrictToSize(final int minSize, final int maxSize) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.getPaths().iterator();
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
	 * @param lowerBound
	 *            the smallest length a path must have in order to be analyzed. Set
	 *            it to Double.NaN to disable lowerBound filtering
	 * @param upperBound
	 *            the largest length a path must have in order to be analyzed. Set
	 *            it to Double.NaN to disable upperBound filtering
	 */
	public void restrictToLength(final double lowerBound, final double upperBound) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.getPaths().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final double length = p.getRealLength();
			if (length < lowerBound || length > upperBound) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	/**
	 * Restricts analysis to Paths containing the specified string in their name.
	 *
	 * @param pattern
	 *            the string to search for
	 */
	public void restrictToNamePattern(final String pattern) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.getPaths().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			if (!p.getName().contains(pattern)) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	private void initializeSnapshotTree() {
		if (unfilteredTree == null)
			unfilteredTree = new Tree(tree.getPaths());
	}

	/**
	 * Removes any filtering restrictions that may have been set. Once called all
	 * paths parsed by the constructor will be analyzed. Does nothing if no paths
	 * are currently being excluded from the analysis.
	 */
	public void resetRestrictions() {
		if (unfilteredTree == null)
			return; // no filtering has occurred
		tree.setPaths(unfilteredTree.getPaths());
		joints = null;
		primaries = null;
		terminals = null;
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
	 */
	public Tree getParsedTree() {
		return tree;
	}

	/**
	 * Outputs a summary of the current analysis to the Analyzer's tables.
	 *
	 * @param rowHeader
	 *            the String to be used as row header for the summary
	 */
	public void summarize(final String rowHeader) {
		if (table == null)
			table = new DefaultGenericTable();
		table.appendRow(rowHeader);
		final int row = Math.max(0, table.getRowCount() - 1);
		restrictToSize(2, -1); // include only paths with at least 2 points
		table.set(getCol("# Paths"), row, getNPaths());
		table.set(getCol("# Branch Points"), row, getBranchPoints().size());
		table.set(getCol("# Tips"), row, getTips().size());
		table.set(getCol("Cable Length"), row, getCableLength());
		table.set(getCol("# Primary Paths (PP)"), row, getPrimaryPaths().size());
		table.set(getCol("# Terminal Paths (TP)"), row, getTerminalPaths().size());
		table.set(getCol("Sum length PP"), row, getPrimaryLength());
		table.set(getCol("Sum length TP"), row, getTerminalLength());
		table.set(getCol("Strahler Root No."), row, getStrahlerRootNumber());
		table.set(getCol("# Fitted Paths"), row, fittedPathsCounter);
		table.set(getCol("Notes"), row, "Single point-paths ignored");
	}

	/**
	 * Sets the Analyzer table.
	 *
	 * @param table
	 *            the table to be used by the analyzer
	 * @see #summarize(String)
	 */
	public void setTable(final DefaultGenericTable table) {
		this.table = table;
	}

	/**
	 * Sets the table.
	 *
	 * @param table
	 *            the table to be used by the analyzer
	 * @param title
	 *            the title of the table display window
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

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if (tree.getPaths() == null || tree.getPaths().isEmpty()) {
			cancel("No Paths to Measure");
			return;
		}
		statusService.showStatus("Measuring Paths...");
		summarize("All Paths");
		updateAndDisplayTable();
		statusService.clearStatus();
	}

	/**
	 * Updates and displays the Analyzer table.
	 */
	public void updateAndDisplayTable() {
		final Display<?> display = displayService.getDisplay(tableTitle);
		if (display != null && display.isDisplaying(table)) {
			display.update();
		} else {
			displayService.createDisplay((tableTitle == null) ? "Path Measurements" : tableTitle, table);
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

	/**
	 * Gets the no. of paths parsed by the Analyzer.
	 *
	 * @return the number of paths
	 */
	public int getNPaths() {
		return tree.getPaths().size();
	}

	/**
	 * Retrieves Paths tagged as primary.
	 *
	 * @return the set of primary paths
	 */
	public HashSet<Path> getPrimaryPaths() {
		primaries = new HashSet<>();
		for (final Path p : tree.getPaths()) {
			if (p.isPrimary())
				primaries.add(p);
		}
		return primaries;
	}

	/**
	 * Retrieves Paths containing terminal points.
	 *
	 * @return the set containing Paths associated with terminal points
	 * @see #restrictToOrder(int...)
	 */
	public HashSet<Path> getTerminalPaths() {
		if (tips == null)
			getTips();
		terminals = new HashSet<>();
		for (final PointInImage tip : tips) {
			if (tip.onPath != null) {
				terminals.add(tip.onPath);
			} else {
				for (final Path p : tree.getPaths()) {
					if (p.contains(tip))
						terminals.add(p);
				}
			}
		}
		return terminals;
	}

	/**
	 * Gets the position of all the tips in the analyzed tree.
	 *
	 * @return the set of tip points
	 */
	public HashSet<PointInImage> getTips() {

		// retrieve all start/end points
		tips = new HashSet<>();
		for (final Path p : tree.getPaths()) {
			IntStream.of(0, p.size() - 1).forEach(i -> {
				tips.add(p.getPointInImage(i));
			});
		}

		// now remove any joint-associated point
		if (joints == null)
			getBranchPoints();
		final Iterator<PointInImage> tipIt = tips.iterator();
		while (tipIt.hasNext()) {
			final PointInImage tip = tipIt.next();
			joints.forEach(joint -> {
				if (joints.contains(tip) || tip.isSameLocation(joint))
					tipIt.remove();
			});
		}
		return tips;

	}

	/**
	 * Gets the position of all the branch points in the analyzed tree.
	 *
	 * @return the branch points positions
	 */
	public HashSet<PointInImage> getBranchPoints() {
		joints = new HashSet<>();
		for (final Path p : tree.getPaths()) {
			joints.addAll(p.findJoinedPoints());
		}
		return joints;
	}

	/**
	 * Gets the cable length.
	 *
	 * @return the cable length of the tree
	 */
	public double getCableLength() {
		return sumLength(tree.getPaths());
	}

	/**
	 * Gets the cable length of primary Paths
	 *
	 * @return the primary length
	 */
	public double getPrimaryLength() {
		if (primaries == null)
			getPrimaryPaths();
		return sumLength(primaries);
	}

	/**
	 * Gets the cable length of terminal Paths
	 *
	 * @return the terminal length
	 */
	public double getTerminalLength() {
		if (terminals == null)
			getTerminalPaths();
		return sumLength(terminals);
	}

	/**
	 * Gets the strahler root number of the analyzed tree
	 *
	 * @return the Strahler root number, or -1 if tree has no defined order
	 */
	public int getStrahlerRootNumber() {
		int root = -1;
		for (final Path p : tree.getPaths()) {
			final int order = p.getOrder();
			if (order > root)
				root = order;
		}
		return root;
	}

	private double sumLength(final Collection<Path> paths) {
		double sum = 0;
		for (final Path p : tree.getPaths()) {
			sum += p.getRealLength();
		}
		return sum;
	}

}
