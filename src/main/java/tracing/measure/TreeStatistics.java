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

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import tracing.Path;
import tracing.Tree;

/**
 * Computes summary and descriptive statistics of tree properties.
 */
public class TreeStatistics extends TreeAnalyzer {


	public TreeStatistics(final Tree tree) {
		super(tree);
	}

	/**
	 * Computes the {@link SummaryStatistics} for the specified measurement.
	 *
	 * @param measurement
	 *            the measurement ({@link N_NODES}, {@link NODE_RADIUS}, etc.)
	 * @return the SummaryStatistics object.
	 */
	public SummaryStatistics getSummaryStats(final String measurement) {
		final SummaryStatistics sStats = new SummaryStatistics();
		assembleStats(new StatisticsInstance(sStats), measurement);
		return sStats;
	}

	/**
	 * Computes the {@link DescriptiveStatistics} for the specified measurement.
	 *
	 * @param measurement
	 *            the measurement ({@link N_NODES}, {@link NODE_RADIUS}, etc.)
	 * @return the DescriptiveStatistics object.
	 */
	public DescriptiveStatistics getDescriptiveStats(final String measurement) {
		final DescriptiveStatistics dStats = new DescriptiveStatistics();
		assembleStats(new StatisticsInstance(dStats), measurement);
		return dStats;
	}

	private void assembleStats(final StatisticsInstance stat, final String measurement) {
		switch (measurement) {
		case TreeAnalyzer.LENGTH:
			for (final Path p : paths)
				stat.addValue(p.getRealLength());
			break;
		case TreeAnalyzer.N_NODES:
			for (final Path p : paths)
				stat.addValue(p.size());
			break;
		case TreeAnalyzer.INTER_NODE_DISTANCE:
			for (final Path p : paths) {
				if (p.size() < 2)
					continue;
				for (int i = 1; i < p.size(); i += 1) {
					stat.addValue(p.getPointInImage(i).distanceTo(p.getPointInImage(i-1)));
				}
			}
			break;
		case TreeAnalyzer.NODE_RADIUS:
			for (final Path p : paths) {
				for (int i = 0; i < p.size(); i++) {
					stat.addValue(p.getNodeRadius(i));
				}
			}
			break;
		case TreeAnalyzer.MEAN_RADIUS:
			for (final Path p : paths) {
				stat.addValue(p.getMeanRadius());
			}
			break;
		case TreeAnalyzer.BRANCH_ORDER:
			for (final Path p : paths) {
				stat.addValue(p.getOrder());
			}
			break;
		case TreeAnalyzer.N_BRANCH_POINTS:
			for (final Path p : paths) {
				stat.addValue(p.findJoinedPoints().size());
			}
			break;
		default:
			throw new IllegalArgumentException("Unrecognized parameter " + measurement);
		}
	}

	private class StatisticsInstance {

		private SummaryStatistics sStatistics;
		private DescriptiveStatistics dStatistics;

		private StatisticsInstance(final SummaryStatistics sStatistics) {
			this.sStatistics = sStatistics;
		}

		private StatisticsInstance(final DescriptiveStatistics dStatistics) {
			this.dStatistics = dStatistics;
		}

		private void addValue(final double value) {
			if (sStatistics != null)
				sStatistics.addValue(value);
			else
				dStatistics.addValue(value);
		}
	}

}
