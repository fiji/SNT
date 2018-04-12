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

import java.io.PrintWriter;
import java.util.ArrayList;

import tracing.util.PointInImage;

public class SWCPoint implements Comparable<SWCPoint> {

	protected final int id, type, previous;
	protected double x, y, z;
	protected double radius;
	protected final ArrayList<SWCPoint> nextPoints;
	protected Path fromPath = null;
	protected SWCPoint previousPoint;

	public SWCPoint(final int id, final int type, final double x, final double y, final double z, final double radius,
			final int previous) {
		nextPoints = new ArrayList<>();
		this.id = id;
		this.type = type;
		this.x = x;
		this.y = y;
		this.z = z;
		this.radius = radius;
		this.previous = previous;
	}

	public PointInImage getPointInImage() {
		return new PointInImage(x, y, z);
	}

	public void addNextPoint(final SWCPoint p) {
		if (!nextPoints.contains(p))
			nextPoints.add(p);
	}

	public void setPreviousPoint(final SWCPoint p) {
		previousPoint = p;
	}

	public double xSeparationFromPreviousPoint() {
		return (previousPoint == null) ? Double.NaN : Math.abs(this.x - previousPoint.x);
	}

	public double ySeparationFromPreviousPoint() {
		return (previousPoint == null) ? Double.NaN : Math.abs(this.y - previousPoint.y);
	}

	public double zSeparationFromPreviousPoint() {
		return (previousPoint == null) ? Double.NaN : (this.z - previousPoint.z);
	}

	@Override
	public String toString() {
		return "SWCPoint [" + id + "] " + Path.swcTypeNames[type] + " " + "(" + x + "," + y + "," + z + ") "
				+ "radius: " + radius + ", " + "[previous: " + previous + "]";
	}

	@Override
	public int compareTo(final SWCPoint o) {
		final int oid = o.id;
		return (id < oid) ? -1 : ((id > oid) ? 1 : 0);
	}

	public void println(final PrintWriter pw) {
		pw.println("" + id + " " + type + " " + x + " " + y + " " + z + " " + radius + " " + previous);
	}
}
