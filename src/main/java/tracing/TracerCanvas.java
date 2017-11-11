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
/*******************************************************************************
 * Copyright (C) 2017 Tiago Ferreira
 * Copyright (C) 2006-2011 Mark Longair
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.util.ArrayList;

import ij.ImagePlus;
import tracing.hyperpanes.PaneOwner;
import tracing.hyperpanes.MultiDThreePanesCanvas;

@SuppressWarnings("serial")
public class TracerCanvas extends MultiDThreePanesCanvas {

	protected PathAndFillManager pathAndFillManager;

	public TracerCanvas(final ImagePlus imagePlus, final PaneOwner owner, final int plane,
			final PathAndFillManager pathAndFillManager) {

		super(imagePlus, owner, plane);
		this.pathAndFillManager = pathAndFillManager;
	}

	ArrayList<SearchInterface> searchThreads = new ArrayList<>();

	public void addSearchThread(final SearchInterface s) {
		synchronized (searchThreads) {
			searchThreads.add(s);
		}
	}

	public void removeSearchThread(final SearchInterface s) {
		synchronized (searchThreads) {
			int index = -1;
			for (int i = 0; i < searchThreads.size(); ++i) {
				final SearchInterface inList = searchThreads.get(i);
				if (s == inList)
					index = i;
			}
			if (index >= 0)
				searchThreads.remove(index);
		}
	}

	boolean just_near_slices = false;
	int eitherSide;

	@Override
	protected void drawOverlay(final Graphics g) {

		/*
		 * int current_z = -1;
		 *
		 * if( plane == ThreePanes.XY_PLANE ) { current_z =
		 * imp.getZ() - 1; }
		 */

		final int current_z = imp.getZ() - 1;

		synchronized (searchThreads) {
			for (final SearchInterface st : searchThreads)
				st.drawProgressOnSlice(plane, current_z, this, g);
		}

		final SimpleNeuriteTracer plugin = pathAndFillManager.getPlugin();

		final boolean showOnlySelectedPaths = plugin.getShowOnlySelectedPaths();

		final Color selectedColor = plugin.selectedColor;
		final Color deselectedColor = plugin.deselectedColor;

		final boolean drawDiametersXY = plugin.getDrawDiametersXY();

		if (pathAndFillManager != null) {
			for (int i = 0; i < pathAndFillManager.size(); ++i) {

				final Path p = pathAndFillManager.getPath(i);
				if (p == null)
					continue;

				if (p.fittedVersionOf != null)
					continue;

				Path drawPath = p;

				// If the path suggests using the fitted version, draw that
				// instead:
				if (p.useFitted) {
					drawPath = p.fitted;
				}

				final boolean isSelected = pathAndFillManager.isSelected(p);
				if (!isSelected && showOnlySelectedPaths)
					continue;

				final boolean customColor = (drawPath.hasCustomColor && plugin.displayCustomPathColors);
				Color color = deselectedColor;
				if (isSelected && !customColor)
					color = selectedColor;
				else if (customColor)
					color = drawPath.getColor();

				if (just_near_slices) {
					drawPath.drawPathAsPoints(this, g, color, plane, (isSelected && customColor), drawDiametersXY,
							current_z, eitherSide);
				} else {
					drawPath.drawPathAsPoints(this, g, color, plane, (isSelected && customColor), drawDiametersXY);
				}
			}
		}

		super.drawOverlay(g);

	}

	/* Keep another Graphics for double-buffering... */

	private int backBufferWidth;
	private int backBufferHeight;

	private Graphics backBufferGraphics;
	private Image backBufferImage;

	private void resetBackBuffer() {

		if (backBufferGraphics != null) {
			backBufferGraphics.dispose();
			backBufferGraphics = null;
		}

		if (backBufferImage != null) {
			backBufferImage.flush();
			backBufferImage = null;
		}

		backBufferWidth = getSize().width;
		backBufferHeight = getSize().height;

		if (backBufferWidth > 0 && backBufferHeight > 0) {
			backBufferImage = createImage(backBufferWidth, backBufferHeight);
			backBufferGraphics = backBufferImage.getGraphics();
		}
	}

	@Override
	public void paint(final Graphics g) {

		if (backBufferWidth != getSize().width || backBufferHeight != getSize().height || backBufferImage == null
				|| backBufferGraphics == null)
			resetBackBuffer();

		super.paint(backBufferGraphics);
		drawOverlay(backBufferGraphics);
		g.drawImage(backBufferImage, 0, 0, this);
	}
}
