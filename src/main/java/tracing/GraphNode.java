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

class GraphNode implements Comparable {

	public int id;

	public int x;
	public int y;
	public int z;

	public String material_name;

	/* These few for the path finding... */
	public GraphNode previous;
	public double g; // cost of the path so far (up to and including this node)
	public double h; // heuristic esimate of the cost of going from here to the
						// goal
	/* ... end of path */

	void setFrom(final GraphNode other) {
		this.id = other.id;
		this.x = other.x;
		this.y = other.y;
		this.z = other.z;
		this.material_name = other.material_name;
		this.previous = other.previous;
		this.g = other.g;
		this.h = other.h;
	}

	// -----------------------------------------------------------------

	double f() {
		return g + h;
	}

	@Override
	public int compareTo(final Object other) {
		final GraphNode n = (GraphNode) other;
		return Double.compare(f(), n.f());
	}

	@Override
	public boolean equals(final Object other) {
		// System.out.println(" equals called "+id);
		return this.id == ((GraphNode) other).id;
	}

	@Override
	public int hashCode() {
		// System.out.println(" hashcode called "+id);
		return this.id;
	}

	// -----------------------------------------------------------------

	public boolean nearTo(final int within, final int other_x, final int other_y, final int other_z) {
		final int xdiff = other_x - x;
		final int ydiff = other_y - y;
		final int zdiff = other_z - z;
		final long distance_squared = xdiff * xdiff + ydiff * ydiff + zdiff * zdiff;
		final long within_squared = within * within;
		return distance_squared <= within_squared;
	}

	public String toDotName() {
		return material_name + " (" + id + ")";
	}

	public String toCollapsedDotName() {
		if (material_name.equals("Exterior"))
			return material_name + " (" + id + ")";
		else
			return material_name;
	}

}
