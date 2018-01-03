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

package tracing;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.PriorityQueue;

@Deprecated
@SuppressWarnings("all")
class PathWithLength {

	public double length;
	public ArrayList<GraphNode> path;

}

@Deprecated
@SuppressWarnings("all")
public class Connectivity {

	ArrayList<GraphNode> allNodes;
	public double[][] distances;

	public int[] redValues;
	public int[] greenValues;
	public int[] blueValues;

	public String[] materialNames;
	public Hashtable<String, Integer> materialNameToIndex;

	public String colorString(final String materialName) {

		if (materialName.equals("Exterior"))
			return "#DDDDDD";
		else {

			final Integer material_id_integer = materialNameToIndex.get(materialName);
			final int material_id = material_id_integer.intValue();

			final double scaling = 1.4;

			int r = (int) (redValues[material_id] * scaling);
			int g = (int) (greenValues[material_id] * scaling);
			int b = (int) (blueValues[material_id] * scaling);

			if (r > 255)
				r = 255;
			if (g > 255)
				g = 255;
			if (b > 255)
				b = 255;

			String redValueString = Integer.toHexString(r);
			if (redValueString.length() <= 1)
				redValueString = "0" + redValueString;

			String greenValueString = Integer.toHexString(g);
			if (greenValueString.length() <= 1)
				greenValueString = "0" + greenValueString;

			String blueValueString = Integer.toHexString(b);
			if (blueValueString.length() <= 1)
				blueValueString = "0" + blueValueString;

			return "#" + redValueString + greenValueString + blueValueString;
		}
	}

	/*
	 * public ArrayList< GraphNode > makePath( GraphNode lastNode ) { ArrayList<
	 * GraphNode > result = new ArrayList< GraphNode >(); makePathHidden(
	 * result, lastNode ); return result; }
	 *
	 * private void makePathHidden( ArrayList< GraphNode > result, GraphNode
	 * lastNode ) {
	 *
	 * if( lastNode.previous != null ) { makePathHidden( result,
	 * lastNode.previous ); }
	 *
	 * result.add( lastNode ); }
	 */

	ArrayList<GraphNode> trimPath(final ArrayList<GraphNode> originalPath, final String from_material,
			final String to_material) {

		int last_from_material = -1;
		for (int i = 0; i < originalPath.size(); ++i) {
			final GraphNode g = originalPath.get(i);
			if (from_material.equals(g.material_name)) {
				last_from_material = i;
			}
		}

		int first_to_material = -1;
		for (int i = originalPath.size() - 1; i >= 0; --i) {
			final GraphNode g = originalPath.get(i);
			if (to_material.equals(g.material_name)) {
				first_to_material = i;
			}
		}

		if (first_to_material < last_from_material) {
			if (true)
				return null;
			else {
				System.out
						.println("********* Very odd path ********** (" + last_from_material + "," + first_to_material);
				SNT.log("   from " + from_material + " to " + to_material);
				for (int i = 0; i < originalPath.size(); ++i) {
					final GraphNode g = originalPath.get(i);
					SNT.log("  - " + g.toDotName());
				}
			}
		}

		final ArrayList<GraphNode> result = new ArrayList<>();
		for (int i = last_from_material; i <= first_to_material; ++i) {
			result.add(originalPath.get(i));
		}

		return result;
	}

	ArrayList<GraphNode> makePath(final GraphNode lastNode) {

		// SNT.log( "Trying to return result" );

		final ArrayList<GraphNode> resultReversed = new ArrayList<>();
		GraphNode p = lastNode;
		do {
			resultReversed.add(p);
			// SNT.log( "adding "+p.toDotName());
		} while (null != (p = p.previous));

		final ArrayList<GraphNode> realResult = new ArrayList<>();

		for (int i = resultReversed.size() - 1; i >= 0; --i)
			realResult.add(resultReversed.get(i));

		return realResult;
	}

	PathWithLength pathBetween(final GraphNode start, final GraphNode goal) {

		for (int i = 0; i < allNodes.size(); i++) {
			final GraphNode g = allNodes.get(i);
			g.g = 0;
			g.h = 0;
			g.previous = null;
		}

		final PriorityQueue<GraphNode> closed_from_start = new PriorityQueue<>();
		final PriorityQueue<GraphNode> open_from_start = new PriorityQueue<>();

		final Hashtable<GraphNode, GraphNode> open_from_start_hash = new Hashtable<>();
		final Hashtable<GraphNode, GraphNode> closed_from_start_hash = new Hashtable<>();

		start.g = 0;
		start.h = 0;
		start.previous = null;

		// add_node( open_from_start, open_from_start_hash, start );
		open_from_start.add(start);
		open_from_start_hash.put(start, start);

		while (open_from_start.size() > 0) {

			// GraphNode p = get_highest_priority( open_from_start,
			// open_from_start_hash );

			// SNT.log("Before poll:
			// "+open_from_start_hash.size()+"/"+open_from_start.size());
			final GraphNode p = open_from_start.poll();
			open_from_start_hash.remove(p);
			// SNT.log("After poll:
			// "+open_from_start_hash.size()+"/"+open_from_start.size());

			// SNT.log( " Got node "+p.toDotName()+" from the queue"
			// );

			// Has the route from the start found the goal?

			if (p.id == goal.id) {
				// SNT.log( "Found the goal! (from start to end)" );
				final ArrayList<GraphNode> path = trimPath(makePath(p), start.material_name, goal.material_name);
				if (path == null)
					return null;
				else {
					final PathWithLength result = new PathWithLength();
					result.path = path;
					result.length = p.g;
					return result;
				}
			}

			// add_node( closed_from_start, closed_from_start_hash, p );
			closed_from_start.add(p);
			closed_from_start_hash.put(p, p);

			// Now look at all the neighbours...

			for (int i = 0; i < distances.length; ++i) {

				final double d = distances[p.id][i];

				if (d >= 0) {

					final GraphNode neighbour = allNodes.get(i);
					if (neighbour.material_name.equals("Exterior")
							|| neighbour.material_name.equals(start.material_name)
							|| neighbour.material_name.equals(goal.material_name)) {

						// SNT.log( " /Considering neighbour: " +
						// neighbour.toDotName() );

						final GraphNode newNode = new GraphNode();
						newNode.setFrom(neighbour);
						newNode.g = p.g + d;
						newNode.h = 0;
						newNode.previous = p;

						final GraphNode foundInClosed = closed_from_start_hash.get(neighbour);

						final GraphNode foundInOpen = open_from_start_hash.get(neighbour);

						// Is there an exisiting route which is
						// better? If so, discard this new candidate...

						if ((foundInClosed != null) && (foundInClosed.f() <= newNode.f())) {
							// SNT.log( " Found in closed, but no
							// better.");
							continue;
						}

						if ((foundInOpen != null) && (foundInOpen.f() <= newNode.f())) {
							// SNT.log( " Found in open, but no
							// better.");
							continue;
						}

						if (foundInClosed != null) {

							// SNT.log("Found in closed and better");

							// remove( closed_from_start,
							// closed_from_start_hash, foundInClosed );
							closed_from_start.remove(foundInClosed);
							closed_from_start_hash.remove(foundInClosed);

							foundInClosed.setFrom(newNode);

							// add_node( open_from_start, open_from_start_hash,
							// foundInClosed );
							open_from_start.add(foundInClosed);
							open_from_start_hash.put(foundInClosed, foundInClosed);

							continue;
						}

						if (foundInOpen != null) {

							// SNT.log("Found in open and better");

							// remove( open_from_start, open_from_start_hash,
							// foundInOpen );
							open_from_start.remove(foundInOpen);
							open_from_start_hash.remove(foundInOpen);

							foundInOpen.setFrom(newNode);

							// add_node( open_from_start, open_from_start_hash,
							// foundInOpen );
							open_from_start.add(foundInOpen);
							open_from_start_hash.put(foundInOpen, foundInOpen);

							continue;
						}

						// Otherwise we add a new node:

						// SNT.log(" Adding new node to open " +
						// newNode.toDotName() );

						// add_node( open_from_start, open_from_start_hash,
						// newNode );
						open_from_start.add(newNode);
						open_from_start_hash.put(newNode, newNode);

					}
				}
			}
		}

		// If we get to here then we haven't found a route to the
		// point. (With the current impmlementation this shouldn't
		// happen, so print a warning.) However, in this case let's
		// return the best option:

		return null;

	}

}
