/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2020 Fiji developers.
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

public class AutoPoint {
	public short x;
	public short y;
	public short z;

	public AutoPoint(final int x, final int y, final int z) {
		this.x = (short) x;
		this.y = (short) y;
		this.z = (short) z;
	}

	@Override
	public String toString() {
		return "(" + x + "," + y + "," + z + ")";
	}

	@Override
	public boolean equals(final Object o) {
		final AutoPoint op = (AutoPoint) o;
		// System.out.println("Testing equality between "+this+" and "+op);
		final boolean result = (this.x == op.x) && (this.y == op.y) && (this.z == op.z);
		return result;
	}

	@Override
	public int hashCode() {
		return x + y * (1 << 11) + z * (1 << 22);
	}
}
