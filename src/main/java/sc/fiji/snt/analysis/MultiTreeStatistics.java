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

import java.util.Collection;

import net.imagej.ImageJ;
import sc.fiji.snt.Path;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.Tree;

/**
 * Computes summary and descriptive statistics from univariate properties of
 * {@link Tree} groups. For analysis of individual Trees use {@link #TreeStatistics}.
 *
 * @author Tiago Ferreira
 */
public class MultiTreeStatistics extends TreeStatistics {

	/** Flag specifying a Tree's 'assigned value'. @see Tree#getAssignedValue() */
	public static final String ASSIGNED_VALUE = "Assigned value";

	/** Flag specifying a Tree's cable length. */
	public static final String LENGTH = "Total length";

	/** Flag specifying the total number of branch points in a Tree */
	public static final String N_BRANCH_POINTS = "No. of branch points";

	/** Flag specifying the total number of end points in a Tree */
	public static final String N_TIPS = "No. of tips";

	/** Flag specifying the highest {@link Path#getOrder() path order} of a Tree */
	public static final String HIGHEST_PATH_ORDER = "Highest path order";

	/** Flag specifying the number of nodes in a Tree */
	public static final String N_NODES = "No. of nodes";

	/** Flag specifying the number of primary branches in a Tree */
	public static final String N_PRIMARY_BRANCHES = "No. of primary branches";

	/** Flag specifying the number of terminal branches in a Tree */
	public static final String N_TERMINAL_BRANCHES = "No. of terminal branches";

	/** Flag specifying the cable length of primary branches in a Tree */
	public static final String PRIMARY_LENGTH = "Sum of primary branches length";

	/** Flag specifying the cable length of terminal branches in a Tree */
	public static final String TERMINAL_LENGTH = "Sum of terminal branches length";


	private Collection<Tree> groupOfTrees;

	/**
	 * Instantiates a new instance from a collection of Trees.
	 *
	 * @param tree the collection of Trees to be analyzed
	 */
	public MultiTreeStatistics(final Collection<Tree> group) {
		super(new Tree());
		this.groupOfTrees = group;
	}

	/**
	 * Sets an identifying label for the group of Trees being analyzed.
	 *
	 * @param label the identifying string for the group.
	 */
	public void setLabel(final String groupLabel) {
		tree.setLabel(groupLabel);
	}

	@Override
	protected void assembleStats(final StatisticsInstance stat,
		final String measurement)
	{
		if (ASSIGNED_VALUE.equals(measurement)) {
			stat.addValue(tree.getAssignedValue());
			return;
		}
		for (final Tree t : groupOfTrees) {
			final TreeAnalyzer ta = new TreeAnalyzer(t);
			switch (measurement) {
			case LENGTH:
				stat.addValue(ta.getCableLength());
				break;
			case N_BRANCH_POINTS:
				stat.addValue(ta.getBranchPoints().size());
				break;
			case N_NODES:
				stat.addValue(t.getNodes().size());
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
			case PRIMARY_LENGTH:
				stat.addValue(ta.getPrimaryLength());
				break;
			case HIGHEST_PATH_ORDER:
				stat.addValue(ta.getHighestPathOrder());
				break;
			case TERMINAL_LENGTH:
				stat.addValue(ta.getTerminalLength());
				break;
			case MEAN_RADIUS:
				double sum = 0;
				for (final Path p : t.list())
					sum += p.getMeanRadius();
				stat.addValue(sum / t.size());
				break;
			default:
				throw new IllegalArgumentException("Unrecognized MultiTreeStatistics parameter " + measurement);
			}
		}
	}

	/* IDE debug method */
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final MultiTreeStatistics treeStats = new MultiTreeStatistics(sntService.demoTrees());
		treeStats.setLabel("Demo Dendrites");
		treeStats.getHistogram(MultiTreeStatistics.MEAN_RADIUS).setVisible(true);
	}
}
