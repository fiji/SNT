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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Utility class to access a Collection of Paths.
 *
 * @author Tiago Ferreira
 */
public class Tree {

	private final HashSet<Path> tree;

	/**
	 * Instantiates a new tree.
	 */
	public Tree() {
		tree = new HashSet<Path>();
	}

	/**
	 * Instantiates a new tree from a SWC or traces file
	 *
	 * @param filename
	 *            the absolute filepathe of the imported file
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
	 *            the SWC type(s) of the paths to be imported (e.g.,
	 *            {@link Path#SWC_AXON}, {@link Path#SWC_DENDRITE}, etc.)
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
	public void applyOffset(final double xOffset, final double yOffset, final double zOffset) {
		for (final Path p : tree) {
			for (double x : p.precise_x_positions)
				x = x + xOffset;
			for (double y : p.precise_y_positions)
				y = y + yOffset;
			for (double z : p.precise_z_positions)
				z = z + zOffset;
		}
	}

}
