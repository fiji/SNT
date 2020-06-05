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

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GUI;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij3d.ContentConstants;
import ij3d.Image3DUniverse;

/* A test for the 3D viewer.  The results are odd at the moment - the
   crossing point of the lines should always appear to be at the
   centre of the voxel, since A Pixel Is Not A Little Square. */

public class Test_Single_Voxel implements PlugIn {
	@Override
	public void run(final String ignore) {
		final ImageStack stack = new ImageStack(3, 3);
		for (int i = 0; i < 3; ++i) {
			final byte[] pixels = new byte[9];
			if (i == 1)
				pixels[4] = (byte) 255;
			final ByteProcessor bp = new ByteProcessor(3, 3);
			bp.setPixels(pixels);
			stack.addSlice("", bp);
		}
		final ImagePlus i = new ImagePlus("test", stack);
		i.show();
		final Image3DUniverse univ = new Image3DUniverse(512, 512);
		univ.show();
		GUI.center(univ.getWindow());
		final boolean[] channels = { true, true, true };
		univ.addContent(i, new Color3f(Color.white), "Volume Rendering of a Single Voxel at (1,1,1)", 10, // threshold
				channels, 1, // resampling
				// factor
				ContentConstants.VOLUME);
		final List<Point3f> linePoints = new ArrayList<>();
		final boolean fudgeCoordinates = false;
		if (fudgeCoordinates) {
			// You shouldn't need to fudge the coordinates
			// like this to make the cross appear in the
			// centre of the voxel...
			linePoints.add(new Point3f(0.5f, 0.5f, 1.5f));
			linePoints.add(new Point3f(2.5f, 2.5f, 1.5f));
			linePoints.add(new Point3f(0.5f, 2.5f, 1.5f));
			linePoints.add(new Point3f(2.5f, 0.5f, 1.5f));
		} else {
			linePoints.add(new Point3f(0, 0, 1));
			linePoints.add(new Point3f(2, 2, 1));
			linePoints.add(new Point3f(0, 2, 1));
			linePoints.add(new Point3f(2, 0, 1));
		}
		univ.addLineMesh(linePoints, new Color3f(Color.red), "Line that cross at (1,1,1)", false);
		univ.resetView();
	}
}
