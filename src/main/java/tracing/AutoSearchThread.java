/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2017 Fiji developers.
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

package tracing;

import java.util.ArrayList;

import ij.ImagePlus;

public class AutoSearchThread extends SearchThread {

	float[][] tubeValues;
	float tubenessThreshold;

	SinglePathsGraph previousPathGraph;

	ArrayList<AutoPoint> destinations = new ArrayList<>(512);

	public ArrayList<AutoPoint> getDestinations() {
		return destinations;
	}

	int start_x, start_y, start_z;

	public AutoSearchThread(final ImagePlus image, final float[][] tubeValues, final AutoPoint startPoint,
			final float tubenessThreshold, final SinglePathsGraph previousPathGraph) {

		super(image, // Image to trace
				-1, // stackMin (which we don't use at all in the automatic
					// tracer)
				-1, // stackMax (which we don't use at all in the automatic
					// tracer)
				false, // bidirectional
				false, // definedGoal
				false, // startPaused
				0, // timeoutSeconds
				1000); // reportEveryMilliseconds

		this.tubeValues = tubeValues;
		this.tubenessThreshold = tubenessThreshold;

		this.previousPathGraph = previousPathGraph;

		this.start_x = startPoint.x;
		this.start_y = startPoint.y;
		this.start_z = startPoint.z;

		final SearchNode s = createNewNode(start_x, start_y, start_z, 0,
				estimateCostToGoal(start_x, start_y, start_z, 0), null, OPEN_FROM_START);
		addNode(s, true);
	}

	@Override
	protected double costMovingTo(final int new_x, final int new_y, final int new_z) {

		double cost;

		// Then this saves a lot of time:
		float measure = tubeValues[new_z][new_y * width + new_x];
		if (measure == 0)
			measure = 0.2f;
		cost = 1 / measure;

		return cost;
	}

	@Override
	protected void addingNode(final SearchNode n) {
		if (tubeValues[n.z][n.y * width + n.x] > tubenessThreshold) {
			final AutoPoint p = new AutoPoint(n.x, n.y, n.z);
			destinations.add(p);
		} else if (null != previousPathGraph.get(n.x, n.y, n.z)) {
			final AutoPoint p = new AutoPoint(n.x, n.y, n.z);
			destinations.add(p);
		}
	}

	/*
	 * This is the heuristic value for the A* search. There's no defined goal in
	 * this default superclass implementation, so always return 0 so we end up
	 * with Dijkstra's algorithm.
	 */

	float estimateCostToGoal(final int current_x, final int current_y, final int current_z,
			final int to_goal_or_start) {
		return 0;
	}

	Path getPathBack(final int from_x, final int from_y, final int from_z) {
		return nodes_as_image_from_start[from_z][from_y * width + from_x].asPath(x_spacing, y_spacing, z_spacing,
				spacing_units);
	}

	@Override
	public Path getResult() {
		throw new RuntimeException("BUG: not implemented");
	}

}
