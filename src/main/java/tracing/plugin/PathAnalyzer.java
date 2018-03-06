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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.stream.IntStream;

import org.scijava.app.StatusService;
import org.scijava.command.ContextCommand;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.table.DefaultGenericTable;
import tracing.Path;
import tracing.util.PointInImage;


@Plugin(type = ContextCommand.class, visible = false)
public class PathAnalyzer extends ContextCommand {

	@Parameter
	protected StatusService statusService;

	@Parameter
	protected DisplayService displayService;

	@Parameter
	private LogService logService;
	
	@Parameter IOService ioservice;

	protected final HashSet<Path> paths;
	private HashSet<Path> primaries;
	private HashSet<Path> terminals;
	private HashSet<PointInImage> joints;
	private HashSet<PointInImage> tips;
	protected DefaultGenericTable table;
	private String tableTitle;
	private int fittedPathsCounter = 0;

	public PathAnalyzer(final ArrayList<Path> paths) {
		this(new HashSet<Path>(paths));
	}

	/**
	 * Instantiates a new path analyzer.
	 *
	 * @param paths
	 *            list of paths to be analyzed. Note that some paths may be excluded
	 *            from analysis: I.e., those with less than 2 points (e.g.,used to
	 *            mark the soma), and (duplicated) paths that may exist only as just
	 *            fitted versions of existing ones.
	 *
	 * @see #getParsedPaths()
	 */
	public PathAnalyzer(final HashSet<Path> paths) {
		this.paths = new HashSet<Path>();
		for (final Path p : paths) {
			if (p == null || p.size() < 2 || p.isFittedVersionOfAnotherPath())
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
		table.set(getCol("# Paths"), row, getNPaths());
		table.set(getCol("# Branch Points"), row, getBranchPoints().size());
		table.set(getCol("# Tips"), row, getTips().size());
		table.set(getCol("Total Length"), row, getTotalLength());
		table.set(getCol("# Primary Paths (PP)"), row, getPrimaryPaths().size());
		table.set(getCol("# Terminal Paths (TP)"), row, getTerminalPaths().size());
		table.set(getCol("Sum length PP"), row, getPrimaryLength());
		table.set(getCol("Sum length TP"), row, getTerminalLength());
		table.set(getCol("# Fitted Paths"), row, fittedPathsCounter);
		table.set(getCol("Notes"), row, "Single point paths ignored");
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

	public double getTotalLength() {
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

	private double sumLength(final HashSet<Path>paths) {
		double sum = 0;
		for (final Path p : paths) {
			sum += p.getRealLength();
		}
		return sum;
	}

}
