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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.stream.IntStream;

import org.scijava.app.StatusService;
import org.scijava.command.ContextCommand;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.table.DefaultGenericTable;
import tracing.Path;
import tracing.Tree;
import tracing.util.PointInImage;

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
	 *            Collection of Paths to be analyzed. Note that some Paths may be
	 *            excluded from analysis: I.e., null Paths, and Paths that may exist
	 *            only as just fitted versions of existing ones.
	 *
	 * @see #getParsedTree()
	 */
	public TreeAnalyzer(final Tree tree) {
		this.tree = new Tree();
		for (final Path p : tree.getPaths()) {
			if (p == null || p.isFittedVersionOfAnotherPath())
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

	public void restrictToSWCType(final int... types) {
		if (unfilteredTree == null) {
			unfilteredTree = new Tree(tree.getPaths());
		}
		tree = tree.subTree(types);
	}

	public void restrictToOrder(final int... orders) {
		if (unfilteredTree == null) {
			unfilteredTree = new Tree(tree.getPaths());
		}
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

	public void restrictToSize(final int minSize, final int maxSize) {
		if (unfilteredTree == null) {
			unfilteredTree = new Tree(tree.getPaths());
		}
		final Iterator<Path> it = tree.getPaths().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final int size = p.size();
			if (minSize >= size || maxSize >= size) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	public void restrictToLength(final double lowerBound, final double upperBound) {
		if (unfilteredTree == null) {
			unfilteredTree = new Tree(tree.getPaths());
		}
		final Iterator<Path> it = tree.getPaths().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final double length = p.getRealLength();
			if (lowerBound >= length || upperBound >= length) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	public void restrictToNamePattern(final String pattern) {
		if (unfilteredTree == null) {
			unfilteredTree = new Tree(tree.getPaths());
		}
		final Iterator<Path> it = tree.getPaths().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			if (p.getName().contains(pattern)) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

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
	 * @return the set of parsed paths, which may be a subset of the tree specified
	 *         in the constructor, since, e.g., Paths that may exist only as just
	 *         fitted versions of existing ones are ignored by the class.
	 */
	public Tree getParsedTree() {
		return tree;
	}

	public void summarize(final String rowHeader) {
		if (table == null)
			table = new DefaultGenericTable();
		table.appendRow(rowHeader);
		final int row = Math.max(0, table.getRowCount() - 1);
		restrictToSWCType(Path.SWC_SOMA);
		table.set(getCol("# tree.getPaths()"), row, getNPaths());
		table.set(getCol("# Branch Points"), row, getBranchPoints().size());
		table.set(getCol("# Tips"), row, getTips().size());
		table.set(getCol("Cable Length"), row, getCableLength());
		table.set(getCol("# Primary tree.getPaths() (PP)"), row, getPrimaryPaths().size());
		table.set(getCol("# Terminal tree.getPaths() (TP)"), row, getTerminalPaths().size());
		table.set(getCol("Sum length PP"), row, getPrimaryLength());
		table.set(getCol("Sum length TP"), row, getTerminalLength());
		table.set(getCol("Strahler Root No."), row, getStrahlerRootNumber());
		table.set(getCol("# Fitted tree.getPaths()"), row, fittedPathsCounter);
		table.set(getCol("Notes"), row, "Soma ignored");
	}

	public void setTable(final DefaultGenericTable table) {
		this.table = table;
	}

	public void setTable(final DefaultGenericTable table, final String title) {
		this.table = table;
		this.tableTitle = title;
	}

	public DefaultGenericTable getTable() {
		return table;
	}

	@Override
	public void run() {
		if (tree.getPaths() == null || tree.getPaths().isEmpty()) {
			cancel("No tree.getPaths() to Measure");
			return;
		}
		statusService.showStatus("Measuring tree.getPaths()...");
		summarize("All tree.getPaths()");
		updateAndDisplayTable();
		statusService.clearStatus();
	}

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

	public int getNPaths() {
		return tree.getPaths().size();
	}

	public HashSet<Path> getPrimaryPaths() {
		primaries = new HashSet<Path>();
		for (final Path p : tree.getPaths()) {
			if (p.isPrimary())
				primaries.add(p);
		}
		return primaries;
	}

	/**
	 * Convenience method to retrieve Paths containing terminal points.
	 *
	 * @return the set containing Paths associated with terminal points
	 * @see #restrictToOrder(int...)
	 */
	public HashSet<Path> getTerminalPaths() {
		if (tips == null)
			getTips();
		terminals = new HashSet<Path>();
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

	public HashSet<PointInImage> getBranchPoints() {
		joints = new HashSet<PointInImage>();
		for (final Path p : tree.getPaths()) {
			joints.addAll(p.findJoinedPoints());
		}
		return joints;
	}

	public double getCableLength() {
		return sumLength(tree.getPaths());
	}

	public double getPrimaryLength() {
		if (primaries == null)
			getPrimaryPaths();
		return sumLength(primaries);
	}

	public double getTerminalLength() {
		if (terminals == null)
			getTerminalPaths();
		return sumLength(terminals);
	}

	public int getStrahlerRootNumber() {
		int root = -1;
		for (final Path p : tree.getPaths()) {
			final int order = p.getOrder();
			if (order > root)
				root = order;
		}
		return root;
	}

	private double sumLength(final HashSet<Path> paths) {
		double sum = 0;
		for (final Path p : tree.getPaths()) {
			sum += p.getRealLength();
		}
		return sum;
	}

}
