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

import java.util.HashSet;
import org.scijava.Context;

import tracing.Path;
import tracing.util.PointInImage;

import net.imagej.table.DefaultResultsTable;
import net.imagej.table.ResultsTable;

import org.scijava.app.StatusService;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;


public class PathAnalyzer {

	@Parameter
	private StatusService statusService;

	@Parameter
	private DisplayService displayService;

	@Parameter
	private LogService logService;

	private ResultsTable table;
	private final HashSet<Path> paths;
	private HashSet<PointInImage> joints;

	public PathAnalyzer(final Context context, final HashSet<Path> paths) {
		context.inject(this);
		this.paths = paths;
	}

	public void summarize() {
		if (table == null) {
			table = new DefaultResultsTable();
			table.appendColumns(new String[] {"# Paths", "# Primary", "# Branch Points", "# Tips", "# Total Length"});
			table.appendRow();
		}
		final int row = Math.max(0, table.getRowCount() - 1);
		table.set("# Paths", row, (double) getNPaths());
		table.set("# Primary", row, (double) getPrimaryPaths().size());
		table.set("# Branch Points", row, (double) getBranchPoints().size());
		table.set("# Tips", row, (double) getTips().size());
		table.set("# Total Length", row, getLength());
	}

	public void setTable(ResultsTable table) {
		this.table = table;
	}

	public ResultsTable getTable() {
		return table;
	}

	public void run() {
		if (paths == null || paths.isEmpty()) {
			statusService.showStatus("No Paths to Measure");
			return;
		}
		statusService.showStatus("Measuring Paths...");
		summarize();
		displayService.createDisplay("Path Measurements", table);
		statusService.clearStatus();
	}

	private int getNPaths() {
		return paths.size();
	}

	private HashSet<Path> getPrimaryPaths() {
		final HashSet<Path> primaries = new HashSet<Path>();
		for (final Path p : paths) {
			if (p.isPrimary())
				primaries.add(p);
		}
		return primaries;
	}

	private HashSet<PointInImage> getTips() {

		// retrieve all start/end points
		final HashSet<PointInImage> tips = new HashSet<>();
		for (final Path p : paths) {
			IntStream.of(0, p.size() - 1).forEach(i -> {
				tips.add(p.getPointInImage(i));
			});
		}

		// now remove any joint-associated point
		if (joints == null) joints = getBranchPoints();
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

	private HashSet<PointInImage> getBranchPoints() {
		joints = new HashSet<PointInImage>();
		for (final Path p : paths) {
			joints.addAll(p.findJoinedPoints());
		}
		return joints;
	}

	private double getLength() {
		double sum = 0;
		for (final Path p : paths) {
			sum += p.getRealLength();
		}
		return sum;
	}

}
