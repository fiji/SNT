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

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import ij.IJ;
import ij.ImagePlus;
import tracing.hyperpanes.MultiDThreePanes;
import tracing.measure.TreeAnalyzer;
import tracing.measure.TreeStatistics;
import tracing.util.PointInImage;

/**
 * Utility class to access a Collection of Paths. Note a "Tree" here is
 * literally a set of Paths and it does not reflect graph theory terminology.
 *
 * @author Tiago Ferreira
 */
public class Tree {

	public static final int X_AXIS = 1;
	public static final int Y_AXIS = 2;
	public static final int Z_AXIS = 4;
	private HashSet<Path> tree;

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
	 *            the absolute file path of the imported file
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
	 *            the absolute file path of the imported file
	 * @param swcTypes
	 *            only paths matching the specified SWC type(s) (e.g.,
	 *            {@link Path#SWC_AXON}, {@link Path#SWC_DENDRITE}, etc.) will be
	 *            imported
	 */
	public Tree(final String filename, final int... swcTypes) {
		this(filename);
		tree = subTree(swcTypes).getPaths();
	}

	/**
	 * Instantiates a new tree from a list of paths.
	 *
	 * @param paths
	 *            the list of paths forming this tree. Null not allowed.
	 */
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
	 * Replaces all Paths in this tree.
	 *
	 * @param paths
	 *            the replacing Paths
	 */
	public void setPaths(final HashSet<Path> paths) {
		tree.clear();
		tree.addAll(paths);
	}

	/**
	 * Removes a path from this tree.
	 *
	 * @param p
	 *            the Path to be removed
	 * @return true if this tree contained p
	 */
	public boolean removePath(final Path p) {
		return tree.remove(p);
	}

	/**
	 * Gets all the paths from this tree
	 *
	 * @return the paths forming this tree
	 */
	public HashSet<Path> getPaths() {
		return tree;
	}

	/**
	 * Returns true if this tree contains no Paths.
	 *
	 * @return true if this tree contains no elements.
	 * 
	 */
	public boolean isEmpty() {
		return tree.isEmpty();
	}

	/**
	 * Downsamples the tree.
	 *
	 * @param maximumAllowedDeviation
	 *            the maximum allowed distance between path nodes. Note that
	 *            upsampling is not supported.
	 * @see PathDownsampler
	 */
	public void downSample(double maximumAllowedDeviation) {
		for (final Path p : tree) {
			p.downsample(maximumAllowedDeviation);
		}
	}

	/**
	 * Extracts a subset of paths matching the specified criteria.
	 *
	 * @param swcTypes
	 *            SWC type(s) (e.g., {@link Path#SWC_AXON},
	 *            {@link Path#SWC_DENDRITE}, etc.) allowed in the subtree
	 * @return the subset of paths matching the filtering criteria, or an empty Tree
	 *         if no hits were retrieved
	 */
	public Tree subTree(final int... swcTypes) {
		final Tree subtree = new Tree();
		final Iterator<Path> it = tree.iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final boolean filteredType = Arrays.stream(swcTypes).anyMatch(t -> t == p.getSWCType());
			if (filteredType)
				subtree.addPath(p);
		}
		return subtree;
	}

	/**
	 * Translates the tree by the specified offset.
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
	 *            the rotation angle in degrees
	 */
	public void rotate(final int axis, final double angle) {
		// See http://www.petercollingridge.appspot.com/3D-tutorial
		if (Double.isNaN(angle)) throw new IllegalArgumentException("Angle not valid");
		final double radAngle = Math.toRadians(angle);
		final double sin = Math.sin(radAngle);
		final double cos = Math.cos(radAngle);
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

	/**
	 * Gets the all the points (path nodes) forming this tree.
	 *
	 * @return the points
	 */
	public ArrayList<PointInImage> getPoints() {
		final ArrayList<PointInImage> list = new ArrayList<PointInImage>();
		for (final Path p : tree) {
			list.addAll(p.getPointInImageList());
		}
		return list;
	}

	/**
	 * Gets an empty image capable of holding the skeletonized version of this tree.
	 *
	 * @param multiDThreePaneView
	 *            the pane flag indicating the SNT view for this image e.g.,
	 *            {@link MultiDThreePanes#XY_PLANE}
	 * @return the empty 8-bit {@link ImagePlus} container
	 */
	public ImagePlus getImpContainer(int multiDThreePaneView) {
		if (tree.isEmpty())
			throw new IllegalArgumentException("tree contains no paths");
		final TreeStatistics tStats = new TreeStatistics(this);
		final SummaryStatistics xCoordStats = tStats.getSummaryStats(TreeAnalyzer.X_COORDINATES);
		final SummaryStatistics yCoordStats = tStats.getSummaryStats(TreeAnalyzer.Y_COORDINATES);
		final SummaryStatistics zCoordStats = tStats.getSummaryStats(TreeAnalyzer.Z_COORDINATES);
		final PointInImage p1 = new PointInImage(xCoordStats.getMin(), yCoordStats.getMin(), zCoordStats.getMin());
		final PointInImage p2 = new PointInImage(xCoordStats.getMax(), yCoordStats.getMax(), zCoordStats.getMax());
		final Path referencePath = tree.iterator().next();
		p1.onPath = referencePath;
		p2.onPath = referencePath;
		final double[] bound1 = PathNode.unScale(p1, multiDThreePaneView);
		final double[] bound2 = PathNode.unScale(p2, multiDThreePaneView);
		final int w = (int) (bound2[0] - bound1[0]) + 2;
		final int h = (int) (bound2[1] - bound1[1]) + 2;
		final int d = (int) (bound2[2] - bound1[2]) + 2;
		return IJ.createImage(null, w, h, d, 8);
	}

	/**
	 * Returns the number of Paths in this tree.
	 *
	 * @return the number of elements in this collection
	 */
	public int size() {
		return tree.size();
	}

}
