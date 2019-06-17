/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
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

package sc.fiji.snt.viewer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.jzy3d.colors.Color;
import org.jzy3d.maths.Coord3d;
import org.jzy3d.plot3d.builder.Builder;
import org.jzy3d.plot3d.primitives.AbstractDrawable;
import org.jzy3d.plot3d.primitives.Scatter;
import org.jzy3d.plot3d.primitives.Shape;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;

import sc.fiji.snt.Path;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SNTPoint;
import sc.fiji.snt.viewer.Viewer3D.Utils;

/**
 * An Annotation3D is a triangulated surface or a cloud of points (scatter)
 * rendered in {@link Viewer3D} that can be used to highlight nodes in a
 * {@link Tree} or locations in a mesh.
 *
 * @author Tiago Ferreira
 */
public class Annotation3D {

	protected static final int SCATTER = 0;
	protected static final int SURFACE = 1;

	private final Viewer3D viewer;
	private final Collection<? extends SNTPoint> points;
	private final AbstractDrawable drawable;
	private float size;
	private String label;

	protected Annotation3D(final Viewer3D viewer, final Collection<? extends SNTPoint> points, final int type) {
		this.viewer = viewer;
		this.points = points;
		size = viewer.getDefaultThickness();
		drawable = (type == SCATTER) ? assembleScatter() : assembleSurface();
		setSize(-1);
	}

	protected Annotation3D(final Viewer3D viewer, final SNTPoint point) {
		this(viewer, Collections.singleton(point), SCATTER);
	}

	private AbstractDrawable assembleSurface() {
		final ArrayList<Coord3d> coordinates = new ArrayList<>();
		for (final SNTPoint point : points) {
			coordinates.add(new Coord3d(point.getX(), point.getY(), point.getZ()));
		}
		final Shape surface = Builder.buildDelaunay(coordinates);
		surface.setColor(Utils.contrastColor(viewer.getDefColor()).alphaSelf(0.4f));
		surface.setWireframeColor(viewer.getDefColor().alphaSelf(0.8f));
		surface.setFaceDisplayed(true);
		surface.setWireframeDisplayed(true);
		return surface;
	}

	private AbstractDrawable assembleScatter() {
		final Coord3d[] coords = new Coord3d[points.size()];
		final Color[] colors = new Color[points.size()];
		int idx = 0;
		for (final SNTPoint point : points) {
			coords[idx] = new Coord3d(point.getX(), point.getY(), point.getZ());
			if (point instanceof PointInImage && ((PointInImage) point).getPath() != null) {
				final Path path = ((PointInImage) point).getPath();
				final int nodeIndex = path.getNodeIndex(((PointInImage) point));
				if (nodeIndex < -1) {
					colors[idx] = viewer.getDefColor();
				} else {
					colors[idx] = viewer
							.fromAWTColor((path.hasNodeColors()) ? path.getNodeColor(nodeIndex) : path.getColor());
				}
			} else {
				colors[idx] = viewer.getDefColor();
			}
			idx++;
		}
		final Scatter scatter = new Scatter();
		scatter.setData(coords);
		scatter.setColors(colors);
		return scatter;
	}

	/**
	 * Sets the annotation width.
	 *
	 * @param size the new width.
	 */
	public void setSize(final float size) {
		this.size = (size < 0) ? viewer.getDefaultThickness() : size;
		if (drawable != null) {
			if (drawable instanceof Scatter)
				((Scatter) drawable).setWidth(this.size);
			else if (drawable instanceof Shape)
				((Shape) drawable).setWireframeWidth(this.size);
		}
	}

	/**
	 * Assigns a color to the annotation.
	 * 
	 * @param color               the color to render the annotation. (If the
	 *                            annotation contains a wireframe, the wireframe is
	 *                            rendered using a "contrast" color computed from
	 *                            this one.
	 * @param transparencyPercent the color transparency (in percentage)
	 */
	public void setColor(final ColorRGB color, final double transparencyPercent) {
		final ColorRGB inputColor = (color == null) ? Colors.WHITE : color;
		final Color c = new Color(inputColor.getRed(), inputColor.getGreen(), inputColor.getBlue(),
				(int) Math.round((100 - transparencyPercent) * 255 / 100));
		if (drawable instanceof Scatter) {
			((Scatter) drawable).setColors(null);
			((Scatter) drawable).setColor(c);
		} else if (drawable instanceof Shape) {
			((Shape) drawable).setColor(c);
			((Shape) drawable).setWireframeColor(Viewer3D.Utils.contrastColor(c));
		}
	}

	/**
	 * Script friendly method to assign a color to the annotation.
	 *
	 * @param color               the color to render the imported file, either a 1)
	 *                            HTML color codes starting with hash ({@code #}), a
	 *                            color preset ("red", "blue", etc.), or integer
	 *                            triples of the form {@code r,g,b} and range
	 *                            {@code [0, 255]}
	 * @param transparencyPercent the color transparency (in percentage)
	 */
	public void setColor(final String color, final double transparencyPercent) {
		setColor(new ColorRGB(color), transparencyPercent);
	}

	/**
	 * Determines whether the mesh bounding box should be displayed.
	 * 
	 * @param boundingBoxColor the color of the mesh bounding box. If null, no
	 *                         bounding box is displayed
	 */
	public void setBoundingBoxColor(final ColorRGB boundingBoxColor) {
		final Color c = (boundingBoxColor == null) ? null
				: new Color(boundingBoxColor.getRed(), boundingBoxColor.getGreen(), boundingBoxColor.getBlue(),
						boundingBoxColor.getAlpha());
		drawable.setBoundingBoxColor(c);
		drawable.setBoundingBoxDisplayed(c != null);
	}

	/**
	 * Gets the annotation label
	 *
	 * @return the label, as listed in Reconstruction Viewer's list.
	 */
	public String getLabel() {
		return label;
	}

	protected void setLabel(final String label) {
		this.label = label;
	}

	/**
	 * Returns the center of this annotation bounding box.
	 *
	 * @return the barycentre of this annotation. All coordinates are set to
	 *         Double.NaN if the bounding box is not available.
	 */
	public SNTPoint getBarycentre() {
		final Coord3d center = drawable.getBarycentre();
		return new PointInImage(center.x, center.y, center.z);
	}

	/**
	 * Returns the {@link AbstractDrawable} associated with this annotation.
	 *
	 * @return the AbstractDrawable
	 */
	public AbstractDrawable getDrawable() {
		return drawable;
	}

}
