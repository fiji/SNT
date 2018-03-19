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

package tracing.measure;

import java.util.ArrayList;
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
import tracing.PathAndFillManager;
import tracing.util.PointInImage;


@Plugin(type = ContextCommand.class, visible = false)
public class PathAnalyzer extends ContextCommand {

	@Parameter
	protected StatusService statusService;

	@Parameter
	protected DisplayService displayService;

	public static final String BRANCH_ORDER = "Branch order";
	public static final String LENGTH = "Length";
	public static final String N_BRANCH_POINTS = "No. of branch points";
	public static final String N_NODES = "No. of nodes";
	public static final String NODE_RADIUS = "Node radius";
	public static final String MEAN_RADIUS = "Path mean radius";
	public static final String INTER_NODE_DISTANCE = "Inter-node distance";


	protected HashSet<Path> paths;
	private HashSet<Path> unfilteredPaths;
	private HashSet<Path> primaries;
	private HashSet<Path> terminals;
	private HashSet<PointInImage> joints;
	private HashSet<PointInImage> tips;
	protected DefaultGenericTable table;
	private String tableTitle;
	private int fittedPathsCounter = 0;
	private int unfilteredPathsFittedPathsCounter = 0;


	public PathAnalyzer(final ArrayList<Path> paths) {
		this(new HashSet<Path>(paths));
	}

	/**
	 * Instantiates a new Path analyzer.
	 *
	 * @param paths
	 *            list of paths to be analyzed. Note that some paths may be excluded
	 *            from analysis: I.e., null paths, and paths that may exist only as just
	 *            fitted versions of existing ones.
	 *
	 * @see #getParsedPaths()
	 */
	public PathAnalyzer(final HashSet<Path> paths) {
		this.paths = new HashSet<Path>();
		for (final Path p : paths) {
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
			this.paths.add(pathToAdd);
		}
		unfilteredPathsFittedPathsCounter = fittedPathsCounter;
	}

	public PathAnalyzer(final PathAndFillManager pafm) {
		this(pafm.getPaths());
	}


	public void restrictToSWCType(final int... types) {
		if (unfilteredPaths == null) {
			unfilteredPaths = new HashSet <Path>(paths);
		}
		final Iterator<Path> it = paths.iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final boolean valid = Arrays.stream(types).anyMatch(t -> t == p.getSWCType());
			if (!valid) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	public void restrictToOrder(final int... orders) {
		if (unfilteredPaths == null) {
			unfilteredPaths = new HashSet<Path>(paths);
		}
		final Iterator<Path> it = paths.iterator();
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
		if (unfilteredPaths == null) {
			unfilteredPaths = new HashSet <Path>(paths);
		}
		final Iterator<Path> it = paths.iterator();
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
		if (unfilteredPaths == null) {
			unfilteredPaths = new HashSet <Path>(paths);
		}
		final Iterator<Path> it = paths.iterator();
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
		if (unfilteredPaths == null) {
			unfilteredPaths = new HashSet <Path>(paths);
		}
		final Iterator<Path> it = paths.iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			if (p.getName().contains(pattern)) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	public void resetRestrictions() {
		if (unfilteredPaths == null) return; // no filtering has occurred
		paths = new HashSet <Path>(unfilteredPaths);
		joints = null;
		primaries = null;
		terminals = null;
		tips = null;
		fittedPathsCounter = unfilteredPathsFittedPathsCounter;
	}

	private void updateFittedPathsCounter(final Path filteredPath) {
		if (fittedPathsCounter > 0 && filteredPath.isFittedVersionOfAnotherPath()) fittedPathsCounter--;
	}

	/**
	 * Returns the set of parsed paths.
	 *
	 * @return the parsed paths, which may be a subset of the paths specified in the
	 *         constructor, since, e.g., paths that may exist only as just fitted
	 *         versions of existing ones are ignored by the class.
	 */
	public HashSet<Path> getParsedPaths() {
		return paths;
	}

	public void summarize(final String rowHeader) {
		if (table == null) table = new DefaultGenericTable();
		table.appendRow(rowHeader);
		final int row = Math.max(0, table.getRowCount() - 1);
		restrictToSWCType(Path.SWC_SOMA);
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
		if (paths == null || paths.isEmpty()) {
			cancel("No Paths to Measure");
			return;
		}
		statusService.showStatus("Measuring Paths...");
		summarize("All Paths");
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
		return paths.size();
	}

	public HashSet<Path> getPrimaryPaths() {
		primaries = new HashSet<Path>();
		for (final Path p : paths) {
			if (p.isPrimary())
				primaries.add(p);
		}
		return primaries;
	}

	/**
	 * Convenience method to retrieve paths containing terminal points.
	 *
	 * @return the set containing paths associated with terminal points
	 * @see #restrictToOrder(int...)
	 */
	public HashSet<Path> getTerminalPaths() {
		if (tips == null) getTips();
		terminals = new HashSet<Path>();
		for (final PointInImage tip : tips) {
			if (tip.onPath != null) {
				terminals.add(tip.onPath);
			} else {
				for (final Path p : paths) {
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
		for (final Path p : paths) {
			IntStream.of(0, p.size() - 1).forEach(i -> {
				tips.add(p.getPointInImage(i));
			});
		}

		// now remove any joint-associated point
		if (joints == null) getBranchPoints();
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
		for (final Path p : paths) {
			joints.addAll(p.findJoinedPoints());
		}
		return joints;
	}

	public double getCableLength() {
		return sumLength(paths);
	}

	public double getPrimaryLength() {
		if (primaries == null) getPrimaryPaths();
		return sumLength(primaries);
	}

	public double getTerminalLength() {
		if (terminals == null) getTerminalPaths();
		return sumLength(terminals);
	}

	public int getStrahlerRootNumber() {
		int root = -1;
		for (final Path p : paths) {
			int order = p.getOrder();
			if (order > root) root = order;
		}
		return root;
	}

	private double sumLength(final HashSet<Path>paths) {
		double sum = 0;
		for (final Path p : paths) {
			sum += p.getRealLength();
		}
		return sum;
	}

}
