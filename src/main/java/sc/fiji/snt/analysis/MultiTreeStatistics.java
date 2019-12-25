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

import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.text.WordUtils;

import net.imagej.ImageJ;
import sc.fiji.snt.Path;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;

/**
 * Computes summary and descriptive statistics from univariate properties of
 * {@link Tree} groups. For analysis of individual Trees use {@link TreeStatistics}.
 *
 * @author Tiago Ferreira
 */
public class MultiTreeStatistics extends TreeStatistics {

	/** Flag for {@value #LENGTH} analysis. */
	public static final String LENGTH = "Cable length";

	/** Flag for {@value #TERMINAL_LENGTH} analysis. */
	public static final String TERMINAL_LENGTH = "Length of terminal branches (sum)";

	/** Flag for {@value #PRIMARY_LENGTH} analysis. */
	public static final String PRIMARY_LENGTH = "Length of primary branches (sum)";

	/** Flag specifying {@link Tree#assignValue(double) Tree value} statistics */
	public static final String ASSIGNED_VALUE = "Assigned value";

	/** Flag specifying {@link StrahlerAnalyzer#getRootNumber() Horton-Strahler number} statistics */
	public static final String STRAHLER_NUMBER = "Horton-Strahler number";

	/** Flag specifying {@link StrahlerAnalyzer#getAvgBifurcationRatio() Horton-Strahler bifurcation ratio} statistics */
	public static final String STRAHLER_RATIO = "Horton-Strahler bifurcation ratio";

	/** Flag specifying {@value #HIGHEST_PATH_ORDER} statistics */
	public static final String HIGHEST_PATH_ORDER = "Highest path order";

	/** Flag specifying {@value #N_BRANCHES} statistics */
	public static final String N_BRANCHES = "No. of branches";

	/** Flag specifying {@value #N_PRIMARY_BRANCHES} statistics */
	public static final String N_PRIMARY_BRANCHES = "No. of primary branches";

	/** Flag specifying {@value #N_TERMINAL_BRANCHES} statistics */
	public static final String N_TERMINAL_BRANCHES = "No. of terminal branches";

	/** Flag for {@value #N_BRANCH_POINTS} statistics */
	public static final String N_BRANCH_POINTS = "No. of branch points";

	/** Flag for {@value #N_PATHS} statistics */
	public static final String N_PATHS = "No. of paths";

	/** Flag for {@value #MEAN_RADIUS} statistics */
	public static final String MEAN_RADIUS = "Mean radius";

	/** Flag for {@value #N_NODES} statistics */
	public static final String N_NODES = "No. of nodes";

	/** Flag specifying {@value #N_TIPS} statistics */
	public static final String N_TIPS = "No. of tips";

	/** Flag for {@value #WIDTH} statistics */
	public static final String WIDTH = "Width";

	/** Flag for {@value #HEIGHT} statistics */
	public static final String HEIGHT = "Height";

	/** Flag for {@value #DEPTH} statistics */
	public static final String DEPTH = "Depth";

	/** Flag for {@value #N_FITTED_PATHS} statistics */
	public static final String N_FITTED_PATHS = "No. of fitted paths";

	protected static String[] ALL_FLAGS = { //
			LENGTH, TERMINAL_LENGTH, PRIMARY_LENGTH, //
			STRAHLER_NUMBER, STRAHLER_RATIO, HIGHEST_PATH_ORDER, //
			N_BRANCHES, N_PRIMARY_BRANCHES, N_TERMINAL_BRANCHES, N_BRANCH_POINTS, N_TIPS, //
			ASSIGNED_VALUE, MEAN_RADIUS, //
			WIDTH, HEIGHT, DEPTH, //
			N_NODES, N_PATHS, N_FITTED_PATHS//
	};

	protected static String[] COMMON_FLAGS = { //
			LENGTH, TERMINAL_LENGTH, PRIMARY_LENGTH, //
			N_BRANCH_POINTS, N_TIPS, //
			N_PRIMARY_BRANCHES, N_TERMINAL_BRANCHES, //
			N_PATHS, N_FITTED_PATHS, MEAN_RADIUS, WIDTH, HEIGHT, DEPTH //
	};

	private Collection<Tree> groupOfTrees;

	/**
	 * Instantiates a new instance from a collection of Trees.
	 *
	 * @param group the collection of Trees to be analyzed
	 */
	public MultiTreeStatistics(final Collection<Tree> group) {
		super(new Tree());
		this.groupOfTrees = group;
	}

	/**
	 * Sets an identifying label for the group of Trees being analyzed.
	 *
	 * @param groupLabel the identifying string for the group.
	 */
	public void setLabel(final String groupLabel) {
		tree.setLabel(groupLabel);
	}

	@Override
	protected String tryReallyHardToGuessMetric(final String guess) {
		if (Arrays.stream(ALL_FLAGS).anyMatch(guess::equalsIgnoreCase)) {
			return WordUtils.capitalize(guess, '-');
		}
		SNTUtils.log("\""+ guess + "\" was not immediately recognized as parameter");
		String normGuess = guess.toLowerCase();
		if (normGuess.indexOf("length") != -1 || normGuess.indexOf("cable") != -1) {
			if (normGuess.indexOf("term") != -1) {
				return TERMINAL_LENGTH;
			}
			else if (normGuess.indexOf("prim") != -1) {
				return PRIMARY_LENGTH;
			}
			else {
				return LENGTH;
			}
		}
		if (normGuess.indexOf("strahler") != -1 || normGuess.indexOf("horton") != -1 || normGuess.indexOf("h-s") != -1) {
			if (normGuess.indexOf("ratio") != -1) {
				return STRAHLER_RATIO;
			}
			else {
				return STRAHLER_NUMBER;
			}
		}
		if (normGuess.indexOf("path") != -1 && normGuess.indexOf("order") != -1) {
			return HIGHEST_PATH_ORDER;
		}
		if (normGuess.indexOf("assign") != -1 || normGuess.indexOf("value") != -1) {
			return ASSIGNED_VALUE;
		}
		if (normGuess.indexOf("branches") != -1) {
			if (normGuess.indexOf("prim") != -1) {
				return N_PRIMARY_BRANCHES;
			}
			else if (normGuess.indexOf("term") != -1) {
				return N_TERMINAL_BRANCHES;
			}
			else {
				return N_BRANCHES;
			}
		}
		if (normGuess.indexOf("bp") != -1 || normGuess.indexOf("branch") != -1 || normGuess.indexOf("junctions") != -1) {
			return N_BRANCH_POINTS;
		}
		if (normGuess.indexOf("radi") != -1 ) {
			return MEAN_RADIUS;
		}
		if (normGuess.indexOf("nodes") != -1 ) {
			return N_NODES;
		}
		if (normGuess.indexOf("tips") != -1 || normGuess.indexOf("termin") != -1 || normGuess.indexOf("end") != -1 ) {
			// n tips/termini/terminals/end points/endings
			return N_TIPS;
		}
		if (normGuess.indexOf("paths") != -1) {
			if (normGuess.indexOf("fit") != -1) {
				return N_FITTED_PATHS;
			}
			else {
				return N_PATHS;
			}
		}
		return super.tryReallyHardToGuessMetric(guess);
	}

	@Override
	protected void assembleStats(final StatisticsInstance stat,
		final String measurement)
	{
		final String normMeasurement = getNormalizedMeasurement(measurement);
		if (ASSIGNED_VALUE.equals(normMeasurement)) {
			for (final Tree t : groupOfTrees) stat.addValue(t.getAssignedValue());
			return;
		}
		for (final Tree t : groupOfTrees) {
			final TreeAnalyzer ta = new TreeAnalyzer(t);
			switch (normMeasurement) {
			case LENGTH:
				stat.addValue(ta.getCableLength());
				break;
			case TERMINAL_LENGTH:
				stat.addValue(ta.getTerminalLength());
				break;
			case PRIMARY_LENGTH:
				stat.addValue(ta.getPrimaryLength());
				break;
			case N_BRANCH_POINTS:
				stat.addValue(ta.getBranchPoints().size());
				break;
			case N_NODES:
				stat.addValue(t.getNodes().size());
				break;
			case N_BRANCHES:
				stat.addValue(ta.getStrahlerAnalyzer().getBranchCounts().values().stream().mapToDouble(f -> f).sum());
				break;
			case N_PRIMARY_BRANCHES:
				stat.addValue(ta.getPrimaryBranches().size());
				break;
			case N_TERMINAL_BRANCHES:
				stat.addValue(ta.getTerminalBranches().size());
				break;
			case N_TIPS:
				stat.addValue(ta.getTips().size());
				break;
			case N_PATHS:
				stat.addValue(ta.getNPaths());
				break;
			case N_FITTED_PATHS:
				stat.addValue(ta.getNFittedPaths());
				break;
			case STRAHLER_NUMBER:
				stat.addValue(ta.getStrahlerNumber());
				break;
			case STRAHLER_RATIO:
				stat.addValue(ta.getStrahlerBifurcationRatio());
				break;
			case HIGHEST_PATH_ORDER:
				stat.addValue(ta.getHighestPathOrder());
				break;
			case MEAN_RADIUS:
				double sum = 0;
				for (final Path p : t.list())
					sum += p.getMeanRadius();
				stat.addValue(sum / t.size());
				break;
			case WIDTH:
				stat.addValue(ta.getWidth());
				break;
			case HEIGHT:
				stat.addValue(ta.getHeight());
				break;
			case DEPTH:
				stat.addValue(ta.getDepth());
				break;
			default:
				SNTUtils.log("Unrecognized MultiTreeStatistics parameter... Defaulting to TreeStatistics analysis");
				dumpStats(stat, measurement);
				return;
			}
		}
	}

	private void dumpStats(final StatisticsInstance stat, final String measurement) {
		final String normMeasurement = super.getNormalizedMeasurement(measurement);
		for (final Tree tree : groupOfTrees)
			super.tree.list().addAll(tree.list());
		super.assembleStats(stat, normMeasurement);
	}

	/* IDE debug method */
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		final SNTService sntService = ij.context().getService(SNTService.class);
		SNTUtils.setDebugMode(true);
		final MultiTreeStatistics treeStats = new MultiTreeStatistics(sntService.demoTrees());
		treeStats.setLabel("Demo Dendrites");
		treeStats.getHistogram("junctions").setVisible(true);
	}
}
