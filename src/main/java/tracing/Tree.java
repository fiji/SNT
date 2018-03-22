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

package tracing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

import tracing.util.PointInImage;

/**
 * Utility class to access a Collection of Paths. Note a "Tree" here is
 * literally a collection of Paths and it does not reflect graph theory
 * terminology
 *
 * @author Tiago Ferreira
 */
public class Tree {

	public static final int X_AXIS = 1;
	public static final int Y_AXIS = 2;
	public static final int Z_AXIS = 4;
	private final HashSet<Path> tree;

	/**
	 * Instantiates a new empty tree.
	 */
	public Tree() {
		tree = new HashSet<Path>();
	}

	/**
	 * Instantiates a new tree from a set of paths.
	 *
	 * @param paths
	 *            the Collection of paths forming this tree. Null not allowed.
	 */
	public Tree(final HashSet<Path> paths) {
		if (paths == null)
			throw new IllegalArgumentException("Cannot instantiate a new tree from a null collection");
		tree = paths;
	}

	/**
	 * Instantiates a new tree from a SWC or traces file
	 *
	 * @param filename
	 *            the absolute file path of the file
	 */
	public Tree(final String filename) {
		final PathAndFillManager pafm = PathAndFillManager.createFromTracesFile(filename);
		if (pafm == null)
			throw new IllegalArgumentException("No paths extracted from " + filename + " Invalid file?");
		tree = new HashSet<Path>(pafm.getPaths());
	}

	/**
	 * Instantiates a new tree from a filtered SWC or traces file.
	 *
	 * @param filename
	 *            the filename
	 * @param swcTypes
	 *            only paths matching the specified SWC type(s) (e.g.,
	 *            {@link Path#SWC_AXON}, {@link Path#SWC_DENDRITE}, etc.) will be
	 *            imported
	 */
	public Tree(final String filename, final int... swcTypes) {
		this(filename);
		final Iterator<Path> it = tree.iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final boolean filteredType = Arrays.stream(swcTypes).anyMatch(t -> t == p.getSWCType());
			if (!filteredType)
				it.remove();
		}
	}

	public Tree(ArrayList<Path> paths) {
		this(new HashSet<Path>(paths));
	}

	/**
	 * Adds a new Path to this tree.
	 *
	 * @param p
	 *            the Path to be added
	 */
	public void addPath(final Path p) {
		tree.add(p);
	}

	/**
	 * Removes a path from this tree.
	 *
	 * @param p
	 *            the Path to be removed
	 */
	public void removePath(final Path p) {
		tree.remove(p);
	}

	/**
	 * Gets paths from this tree
	 *
	 * @return the paths forming this tree
	 */
	public HashSet<Path> getPaths() {
		return tree;
	}

	/**
	 * Translates the entire tree by the specified offset.
	 *
	 * @param xOffset
	 *            the x offset
	 * @param yOffset
	 *            the y offset
	 * @param zOffset
	 *            the z offset
	 */
	public void translate(final double xOffset, final double yOffset, final double zOffset) {
		for (final Path p : tree) {
			for (double x : p.precise_x_positions)
				x = x + xOffset;
			for (double y : p.precise_y_positions)
				y = y + yOffset;
			for (double z : p.precise_z_positions)
				z = z + zOffset;
		}
	}

	/**
	 * Rotates the tree.
	 *
	 * @param axis
	 *            the rotation axis. Either {@link X_AXIS}, {@link Y_AXIS}, or
	 *            {@link Z_AXIS}
	 * @param angle
	 *            the rotation angle
	 */
	public void rotate(final int axis, final double angle) {
		final double sin = Math.sin(angle);
		final double cos = Math.cos(angle);
		switch (axis) {
		case Z_AXIS:
			for (final Path p : tree) {
				for (int node = 0; node < p.size(); node++) {
					final PointInImage pim = p.getPointInImage(node);
					final double x = pim.x * cos - pim.y * sin;
					final double y = pim.y * cos + pim.x * sin;
					p.moveNode(node, new PointInImage(x, y, pim.z));
				}
			}
			break;
		case Y_AXIS:
			for (final Path p : tree) {
				for (int node = 0; node < p.size(); node++) {
					final PointInImage pim = p.getPointInImage(node);
					final double x = pim.x * cos - pim.z * sin;
					final double z = pim.z * cos + pim.x * sin;
					p.moveNode(node, new PointInImage(x, pim.y, z));
				}
			}
			break;
		case X_AXIS:
			for (final Path p : tree) {
				for (int node = 0; node < p.size(); node++) {
					final PointInImage pim = p.getPointInImage(node);
					final double y = pim.y * cos - pim.z * sin;
					final double z = pim.z * cos + pim.y * sin;
					p.moveNode(node, new PointInImage(pim.x, y, z));
				}
			}
			break;
		default:
			throw new IllegalArgumentException("Unrecognized rotation axis" + axis);
		}
	}
}
