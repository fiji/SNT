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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;

public class Load_Auto_Traces implements PlugIn, TraceLoaderListener {

	int width = -1, height = -1, depth = -1;
	float spacing_x = Float.MIN_VALUE;
	float spacing_y = Float.MIN_VALUE;
	float spacing_z = Float.MIN_VALUE;

	byte[][] values = null;

	@Override
	public void gotVertex(final int vertexIndex, final float x_scaled, final float y_scaled, final float z_scaled,
			final int x_image, final int y_image, final int z_image) {

		if (values == null) {
			if (width < 0 || height < 0 || depth < 0 || spacing_x == Float.MIN_VALUE || spacing_y == Float.MIN_VALUE
					|| spacing_z == Float.MIN_VALUE) {

				throw new RuntimeException("Some metadata was missing from the comments before the first vertex.");
			}
			values = new byte[depth][];
			for (int z = 0; z < depth; ++z)
				values[z] = new byte[width * height];
		}

		if (z_image >= depth) {
			System.out.println("z_image: " + z_image + " was too large for depth: " + depth);
			System.out.println("z_scaled was: " + z_scaled);
		}

		values[z_image][y_image * width + x_image] = (byte) 255;
	}

	@Override
	public void gotLine(final int fromVertexIndex, final int toVertexIndex) {
		// Do nothing...
	}

	@Override
	public void gotWidth(final int width) {
		this.width = width;
	}

	@Override
	public void gotHeight(final int height) {
		this.height = height;
	}

	@Override
	public void gotDepth(final int depth) {
		this.depth = depth;
	}

	@Override
	public void gotSpacingX(final float spacing_x) {
		this.spacing_x = spacing_x;
	}

	@Override
	public void gotSpacingY(final float spacing_y) {
		this.spacing_y = spacing_y;
	}

	@Override
	public void gotSpacingZ(final float spacing_z) {
		this.spacing_z = spacing_z;
	}

	@Override
	public void run(final String ignored) {

		OpenDialog od;

		od = new OpenDialog("Select traces.obj file...", null, null);

		final String fileName = od.getFileName();
		final String directory = od.getDirectory();

		if (fileName == null)
			return;

		System.out.println("Got " + fileName);

		final boolean success = SinglePathsGraph.loadWithListener(directory + fileName, this);

		if (!success) {
			IJ.error("Loading " + directory + fileName);
			return;
		}

		final ImageStack stack = new ImageStack(width, height);

		for (int z = 0; z < depth; ++z) {
			final ByteProcessor bp = new ByteProcessor(width, height);
			bp.setPixels(values[z]);
			stack.addSlice("", bp);
		}

		final ImagePlus imagePlus = new ImagePlus(fileName, stack);
		imagePlus.show();

	}

}
