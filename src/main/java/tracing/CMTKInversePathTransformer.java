/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2006, 2007, 2008, 2009, 2010, 2011 Mark Longair */

/*
  This file is part of the ImageJ plugin "Simple Neurite Tracer".

  The ImageJ plugin "Simple Neurite Tracer" is free software; you
  can redistribute it and/or modify it under the terms of the GNU
  General Public License as published by the Free Software
  Foundation; either version 3 of the License, or (at your option)
  any later version.

  The ImageJ plugin "Simple Neurite Tracer" is distributed in the
  hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE.  See the GNU General Public License for more
  details.

  In addition, as a special exception, the copyright holders give
  you permission to combine this program with free software programs or
  libraries that are released under the Apache Public License.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
