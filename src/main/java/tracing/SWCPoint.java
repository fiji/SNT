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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;

import tracing.util.PointInImage;

public class SWCPoint implements Comparable<SWCPoint> {

	protected final int id;
	protected final int type;
	protected final int previous;
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
		final PointInImage pim = new PointInImage(x, y, z);
		pim.onPath = fromPath;
		return pim;
	}

	public int getId() {
		return id;
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
		return "SWCPoint [" + id + "] " + Path.getSWCtypeName(type, false) + " " + "(" + x + "," + y + "," + z + ") "
				+ "radius: " + radius + ", " + "[previous: " + previous + "]";
	}

	@Override
	public int compareTo(final SWCPoint o) {
		final int oid = o.id;
		return (id < oid) ? -1 : ((id > oid) ? 1 : 0);
		//return id - o.id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;
		if (!(o instanceof SWCPoint)) return false;
		return this.id == ((SWCPoint) o).id;
	}

	/**
	 * Converts a collection of SWC points into a Reader.
	 *
	 * @param points the collection of SWC points to be converted into a space/ tab
	 *               separated String. Points should be sorted by sample number to
	 *               ensure valid connectivity.
	 * @return the Reader
	 */
	public static StringReader collectionAsReader(final Collection<SWCPoint> points) {
		final StringBuilder sb = new StringBuilder();
		for (final SWCPoint p : points) {
			sb.append(p.id).append("\t") //
					.append(p.type).append("\t") //
					.append(String.format("%.6f", p.x)).append(" ") //
					.append(String.format("%.6f", p.y)).append(" ") //
					.append(String.format("%.6f", p.z)).append(" ") //
					.append(String.format("%.6f", p.radius)).append("\t") //
					.append(p.previous).append(System.lineSeparator());
		}
		return new StringReader(sb.toString());
	}

	/**
	 * Prints a list of points as space-separated values.
	 *
	 * @param points the collections of SWC points to be printed.
	 * @param pw     the PrintWriter to write to.
	 * @see #listAsReader(Collection)
	 */
	public static void flush(final Collection<SWCPoint> points, final PrintWriter pw) {
		pw.print(collectionAsReader(points));
	}

}
