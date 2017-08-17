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

import util.CMTKTransformation;

/* And now some helpful implementations: */

public class CMTKInversePathTransformer implements PathTransformer {

	private final CMTKTransformation.Inverse t;

	public CMTKInversePathTransformer(final CMTKTransformation.Inverse t) {
		this.t = t;
	}

	@Override
	public void transformPoint(final double modelX, final double modelY, final double modelZ,
			final double[] transformed) {
		t.transformPoint(modelX, modelY, modelZ, transformed);
	}

	@Override
	public void transformPoint(final double modelX, final double modelY, final double modelZ, final int[] transformed) {
		t.transformPoint(modelX, modelY, modelZ, transformed);
	}

	@Override
	public void transformPoint(final int modelX, final int modelY, final int modelZ, final int[] transformed) {
		t.transformPoint(modelX, modelY, modelZ, transformed);
	}

	@Override
	public void transformPoint(final int modelX, final int modelY, final int modelZ, final double[] transformed) {
		t.transformPoint(modelX, modelY, modelZ, transformed);
	}

}
