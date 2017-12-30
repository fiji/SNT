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

/* The x, y and z here are in world coordinates, i.e. already scaled
   by the calibration values. */

public class PointInImage {

	public double x, y, z;
	public Path onPath = null; // You can optionally set this value:

	public PointInImage(final double x, final double y, final double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public double distanceSquaredTo(final double ox, final double oy, final double oz) {
		final double xdiff = x - ox;
		final double ydiff = y - oy;
		final double zdiff = z - oz;
		return xdiff * xdiff + ydiff * ydiff + zdiff * zdiff;
	}

	public double distanceSquaredTo(final PointInImage o) {
		return distanceSquaredTo(o.x, o.y, o.z);
	}

	public double distanceTo(final PointInImage o) {
		final double xdiff = x - o.x;
		final double ydiff = y - o.y;
		final double zdiff = z - o.z;
		return Math.sqrt(xdiff * xdiff + ydiff * ydiff + zdiff * zdiff);
	}

	@Override
	public String toString() {
		return "( " + x + ", " + y + ", " + z + " ) [onPath " + onPath + "]";
	}

	public PointInImage transform(final PathTransformer transformer) {
		final double[] result = new double[3];
		transformer.transformPoint(x, y, z, result);
		return new PointInImage(result[0], result[1], result[2]);
	}

	public boolean isReal() {
		return !(Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || Double.isInfinite(x) || Double.isInfinite(y)
				|| Double.isInfinite(z));
	}

}
