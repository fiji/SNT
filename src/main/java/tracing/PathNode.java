/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2017 Fiji developers.
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
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import tracing.hyperpanes.MultiDThreePanes;

/** Convenience class used to render Path nodes (vertices). */
public class PathNode {

	/** Flag describing a start point node */
	public static final int START = 1;
	/** Flag describing an end point node */
	public static final int END = 2;
	/** Flag describing a fork point node */
	public static final int JOINT = 3;
	/** Flag describing a slab node */
	public static final int SLAB = 4;

	private final Path path;
	private int type;
	private double size;
	private boolean editable;
	protected double x;
	protected double y;

	/**
	 * Creates a node from a Path position.
	 *
	 * @param path the path holding this node
	 * @param index the position of this node within path
	 * @param canvas the canvas to render this node
	 */
	public PathNode(final Path path, final int index, final TracerCanvas canvas) {
		this.path = path;

		// Retrieve x,y coordinates of node
		switch (canvas.getPlane()) {
			case MultiDThreePanes.XY_PLANE:
				x = canvas.myScreenXDprecise(path.getXUnscaledDouble(index));
				y = canvas.myScreenYDprecise(path.getYUnscaledDouble(index));
				break;
			case MultiDThreePanes.XZ_PLANE:
				x = canvas.myScreenXDprecise(path.getXUnscaledDouble(index));
				y = canvas.myScreenYDprecise(path.getZUnscaledDouble(index));
				break;
			case MultiDThreePanes.ZY_PLANE:
				x = canvas.myScreenXDprecise(path.getZUnscaledDouble(index));
				y = canvas.myScreenYDprecise(path.getYUnscaledDouble(index));
				break;
			default:
				throw new IllegalArgumentException("BUG: Unknown plane! (" + canvas
					.getPlane() + ")");
		}

		size = canvas.nodeDiameter();

		// Define which type of node we're dealing with
		if (index == 0 && path.startJoins == null) {
			type = START;
			size *= 2;
		}
		else if (index == path.points - 1 && path.endJoins == null) {
			type = END;
			size *= 1.5;
		}
		else if ((index == 0 && path.startJoins != null) || (index == path.points -
			1 && path.endJoins != null))
		{
			type = JOINT;
			size *= 3;
		}
		else {
			type = SLAB;
		}

	}

	/**
	 * Returns the type of node.
	 *
	 * @return the node type: PathNode.END, PathNode.JOINT, PathNode.SLAB or
	 *         PathNode.START
	 */
	public int type() {
		return type;
	}

	public void draw(final Graphics2D g, final Color c) {

		// TODO: set size according to path thickness?
		final Shape node = new Ellipse2D.Double(x - size / 2, y - size / 2, size,
			size);
		if (editable) {
			// editable node: opaque border with cross hairs
			g.setColor(c);
			g.draw(node);
			g.draw(new Line2D.Double(x - size / 2, y, x + size / 2, y)); // W/E
			g.draw(new Line2D.Double(x, y - size / 2, x, y + size / 2)); // N/S
			g.draw(new Line2D.Double(x - size / 2, y - size / 2, x + size / 2, y +
				size / 2)); // NW/SE
			g.draw(new Line2D.Double(x - size / 2, y + size / 2, x + size / 2, y -
				size / 2)); // SW/NE
		}
		else if (path.isSelected()) {
			// selected path node: opaque border and 75% transparency fill
			g.setColor(c);
			g.draw(node);
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 191));
			g.fill(node);
		}
		else {
			// 'regular' node: filled @ 50% transparency
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 128));
			g.fill(node);
		}
	}

	/**
	 * @return whether or not this node should be rendered as editable.
	 */
	public boolean isEditable() {
		return editable;
	}

	/**
	 * Enables the node as editable/non-editable.
	 *
	 * @param editable true to render the node as editable.
	 */
	public void setEditable(final boolean editable) {
		this.editable = editable;
	}

}
