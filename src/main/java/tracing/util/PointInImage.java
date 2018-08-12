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

package tracing.util;

import java.util.Objects;

import tracing.Path;
import tracing.PathTransformer;

/**
 * Defines a Point in an image, a node of a traced {@link Path}. Coordinates are
 * always expressed in real-world coordinates.
 * 
 * @author Tiago Ferreira
 */
public class PointInImage implements SNTPoint {

	/** The cartesian coordinate of this node */
	public double x, y, z;

	/** The Path associated with this node, if any (optional field) */
	public Path onPath = null;

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
		return Math.sqrt(distanceSquaredTo(o));
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

	public boolean isSameLocation(final PointInImage pim) {
		return (this.x == pim.x) && (this.y == pim.y) && (this.z == pim.z);
	}

	@Override
	public boolean equals(final Object o) { // NB: onPath is optional: field not evaluated for equality
		if (o == this) return true;
		if (o == null) return false;
		if (getClass() != o.getClass()) return false;
		return isSameLocation((PointInImage) o);
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y, z);
	}

	@Override
	public double getX() {
		return x;
	}

	@Override
	public double getY() {
		return y;
	}

	@Override
	public double getZ() {
		return z;
	}
}
