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

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import tracing.Path;
import tracing.Tree;

/**
 * The Class TreeStatistics.
 */
public class TreeStatistics extends TreeAnalyzer {

	/** Flag encoding the cable length of a tree */
	public static final int LENGTH = 1;

	/** Flag encoding number of points in a tree */
	public static final int N_NODES = 2;

	/** Flag encoding distances between consecutive nodes */
	public static final int INTER_NODE_DISTANCE = 4;

	/** Flag encoding the node radius */
	public static final int NODE_RADIUS = 8;

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
	public SummaryStatistics getStatistics(final int measurement) {
		final SummaryStatistics stat = new SummaryStatistics();
		switch (measurement) {
		case LENGTH:
			for (final Path p : paths)
				stat.addValue(p.getRealLength());
			break;
		case N_NODES:
			for (final Path p : paths)
				stat.addValue(p.size());
			break;
		case INTER_NODE_DISTANCE:
			for (final Path p : paths) {
				if (p.size() < 2)
					continue;
				for (int i = 0; i < p.size(); i += 2) {
					stat.addValue(p.getPointInImage(i + 1).distanceTo(p.getPointInImage(i)));
				}
			}
			break;
		case NODE_RADIUS:
			for (final Path p : paths) {
				for (int i = 0; i < p.size(); i++) {
					stat.addValue(p.getNodeRadius(i));
				}
			}
			break;
		default:
			throw new IllegalArgumentException("Unrecognized parameter " + measurement);
		}
		return stat;
	}

}
