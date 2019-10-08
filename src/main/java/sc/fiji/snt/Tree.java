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

package sc.fiji.snt;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.scijava.util.ColorRGB;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.util.BoundingBox;
import sc.fiji.snt.util.PointInCanvas;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SNTPoint;
import sc.fiji.snt.util.SWCPoint;
import sholl.UPoint;
import sc.fiji.snt.analysis.TreeStatistics;
import sc.fiji.snt.analysis.graph.GraphUtils;
import sc.fiji.snt.analysis.sholl.TreeParser;
import sc.fiji.snt.hyperpanes.MultiDThreePanes;

/**
 * Utility class to access a Collection of Paths. A Tree is the preferred way to
 * group, access and manipulate {@link Path}s that share something in common,
 * specially when scripting SNT. Most methods are multithreaded. Note that a
 * "Tree" here is literally a collection of {@link Path}s and it does not
 * reflect graph theory terminology.
 *
 * @author Tiago Ferreira
 */
public class Tree {

	public static final int X_AXIS = 1;
	public static final int Y_AXIS = 2;
	public static final int Z_AXIS = 4;

	private ArrayList<Path> tree;
	private String label;
	private TreeBoundingBox box;
	private PathAndFillManager pafm;

	/**
	 * Instantiates a new empty Tree.
	 */
	public Tree() {
		tree = new ArrayList<>();
	}

	/**
	 * Instantiates a new Tree from a set of paths.
	 *
	 * @param paths the Collection of paths forming this tree. Null not allowed.
	 *          Note that when a Path has been fitted and
	 *          {@link Path#getUseFitted()} is true, its fitted 'flavor' is used.
	 */
	public Tree(final Collection<Path> paths) {
		if (paths == null) throw new IllegalArgumentException(
			"Cannot instantiate a new tree from a null collection");
		tree = new ArrayList<>(paths.size());
		for (final Path p : paths) {
			if (p == null) continue;
			Path pathToAdd;
			// If fitted flavor of path exists use it instead
			if (p.getUseFitted() && p.getFitted() != null) {
				pathToAdd = p.getFitted();
			}
			else {
				pathToAdd = p;
			}
			tree.add(pathToAdd);
		}
	}

	/**
	 * Instantiates a Tree from a collection of reconstruction nodes.
	 *
	 * @param nodes the collection of reconstruction nodes. Nodes will be sorted by
	 *              id and any duplicate entries pruned.
	 * @param label the identifying label for this Tree.
	 */
	public Tree(final Collection<SWCPoint> nodes, final String label) {
		pafm = PathAndFillManager.createFromNodes(nodes);
		tree = (pafm == null) ? new ArrayList<>() : pafm.getPaths();
		setLabel(label);
	}

	/**
	 * Instantiates a new tree from a SWC, TRACES or JSON file.
	 *
	 * @param filename the absolute file path of the imported file
	 * @throws IllegalArgumentException if file path is not valid
	 */
	public Tree(final String filename) throws IllegalArgumentException {
		final File f = new File(filename);
		if (!f.exists()) throw new IllegalArgumentException(
			"File does not exist: " + filename);
		pafm = PathAndFillManager.createFromFile(filename);
		if (pafm == null) throw new IllegalArgumentException(
			"No paths extracted from " + filename + " Invalid file?");
		tree = pafm.getPaths();
		setLabel(f.getName());
	}

	/**
	 * Instantiates a new tree from a filtered SWC, TRACES or JSON file.
	 *
	 * @param filename the absolute file path of the imported file
	 * @param swcTypes only paths matching the specified SWC type(s) (e.g.,
	 *          {@link Path#SWC_AXON}, {@link Path#SWC_DENDRITE}, etc.) will be
	 *          imported
	 */
	public Tree(final String filename, final int... swcTypes) {
		this(filename);
		tree = subTree(swcTypes).list();
	}

	/**
	 * Adds a new Path to this Tree.
	 *
	 * @param p the Path to be added
	 * @return true, if Path successful added
	 */
	public boolean add(final Path p) {
		return tree.add(p);
	}

	/**
	 * Appends all paths of a specified {@link Tree} to this one.
	 *
	 * @param tree the Tree to be merged
	 * @return true if this Tree changed as a result of the merge
	 */
	public boolean merge(final Tree tree) {
		setLabel(((label == null) ? "" : label) + " " + tree.getLabel());
		return this.tree.addAll(tree.list());
	}

	// /**
	// * Returns a copy of this Tree.
	// *
	// * @return a copy of this Tree.
	// */
	// public Tree duplicate() {
	// return new Tree(new HashSet<Path>(tree));
	// }

	/**
	 * Replaces all Paths in this Tree.
	 *
	 * @param paths the replacing Paths
	 */
	public void replaceAll(final List<Path> paths) {
		tree = new ArrayList<>(paths);
	}

	/**
	 * Returns the Path at the specified position.
	 *
	 * @param index index of the element to return
	 * @return the element at the specified position
	 */
	public Path get(final int index) {
		return tree.get(index);
	}

	/**
	 * Returns the index of the specified Path in this Tree.
	 *
	 * @param path the Path to be searched for
	 * @return the path index, or -1 if it was not found
	 */
	public int indexOf(final Path path) {
		return tree.indexOf(path);
	}

	/**
	 * Removes a path from this tree.
	 *
	 * @param p the Path to be removed
	 * @return true if this tree contained p
	 */
	public boolean remove(final Path p) {
		return tree.remove(p);
	}

	/**
	 * Gets all the paths from this tree.
	 *
	 * @return the paths forming this tree
	 */
	public ArrayList<Path> list() {
		return tree;
	}

	/**
	 * Checks if is empty.
	 *
	 * @return true if this tree contains no Paths, false otherwise
	 */
	public boolean isEmpty() {
		return tree.isEmpty();
	}

	/**
	 * Downsamples the tree.
	 *
	 * @param maximumAllowedDeviation the maximum allowed distance between 'shaft'
	 *          path nodes. Note that 1) upsampling is not supported, and 2) the
	 *          position of nodes at branch points and tips remains unaltered
	 *          during downsampling
	 * @see PathDownsampler
	 */
	public void downSample(final double maximumAllowedDeviation) {
		tree.parallelStream().forEach(p -> {
			p.downsample(maximumAllowedDeviation);
		});
	}

	/**
	 * Extracts the subset of paths matching the specified criteria (script friendly
	 * method)
	 *
	 * @param swcTypes SWC type(s) a string with at least 2 characters describing
	 *                 the SWC type allowed in the subtree (e.g., 'soma', 'axn', or
	 *                 'dendrite')
	 * @return the subset of paths matching the filtering criteria, or an empty Tree
	 *         if no hits were retrieved
	 */
	public Tree subTree(final String... swcTypes) {
		final int[] types = new int[swcTypes.length];
		for (int i = 0; i < swcTypes.length; i++) {
			switch (swcTypes[i].toLowerCase().substring(0, 2)) {
			case "ax":
				types[i] = Path.SWC_AXON;
				break;
			case "so":
				types[i] = Path.SWC_SOMA;
				break;
			case "ap":
				types[i] = Path.SWC_APICAL_DENDRITE;
				break;
			case "ba":
			case "de":
				types[i] = Path.SWC_DENDRITE;
				break;
			default:
				types[i] = Path.SWC_UNDEFINED;
				break;
			}
		}
		return subTree(types);
	}

	/**
	 * Extracts the subset of paths matching the specified criteria.
	 *
	 * @param swcTypes SWC type(s) (e.g., {@link Path#SWC_AXON},
	 *          {@link Path#SWC_DENDRITE}, etc.) allowed in the subtree
	 * @return the subset of paths matching the filtering criteria, or an empty
	 *         Tree if no hits were retrieved
	 */
	public Tree subTree(final int... swcTypes) {
		final Tree subtree = new Tree();
		final Iterator<Path> it = tree.iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final boolean filteredType = Arrays.stream(swcTypes).anyMatch(t -> t == p
				.getSWCType());
			if (filteredType) subtree.add(p);
		}
		return subtree;
	}

	/**
	 * Assigns an SWC type label to all the Paths in this Tree.
	 *
	 * @param type the SWC type (e.g., {@link Path#SWC_AXON},
	 *          {@link Path#SWC_DENDRITE}, etc.)
	 */
	public void setType(final int type) {
		tree.stream().forEach(p -> p.setSWCType(type));
	}

	/**
	 * Assigns an SWC type label to all the Paths in this Tree.
	 *
	 * @param type the SWC type (e.g., "soma", "axon", "(basal) dendrite", "apical
	 *          dendrite", etc.)
	 */
	public void setSWCType(final String type) {
		String inputType = (type == null) ? Path.SWC_UNDEFINED_LABEL : type.trim()
			.toLowerCase();
		switch (inputType) {
			case "dendrite":
			case "dend":
				inputType = Path.SWC_DENDRITE_LABEL;
				break;
			case "":
			case "none":
			case "unknown":
			case "undef":
				inputType = Path.SWC_UNDEFINED_LABEL;
				break;
			default:
				break; // keep input
		}
		final int labelIdx = Path.getSWCtypeNames().indexOf(type);
		if (labelIdx == -1) throw new IllegalArgumentException(
			"Unrecognized SWC-type label:" + type);
		final int intType = Path.getSWCtypes().get(labelIdx);
		tree.stream().forEach(p -> p.setSWCType(intType));
	}

	/**
	 * Extracts the SWC-type flags present in this Tree.
	 *
	 * @return the set of SWC type(s) (e.g., {@link Path#SWC_AXON},
	 *         {@link Path#SWC_DENDRITE}, etc.) present in the tree
	 */
	public Set<Integer> getSWCtypes() {
		final HashSet<Integer> types = new HashSet<>();
		final Iterator<Path> it = tree.iterator();
		while (it.hasNext()) {
			types.add(it.next().getSWCType());
		}
		return types;
	}

	/**
	 * Gets the centroid position of all nodes tagged as {@link Path#SWC_SOMA}
	 *
	 * @return the centroid of soma position or null if no Paths are tagged as
	 *         soma
	 */
	public PointInImage getSomaPosition() {
		final TreeParser parser = new TreeParser(this);
		try {
			parser.setCenter(TreeParser.PRIMARY_NODES_SOMA);
		}
		catch (final IllegalArgumentException ignored) {
			SNTUtils.log("No soma attribute found...");
			return null;
		}
		final UPoint center = parser.getCenter();
		return (center == null) ? null : new PointInImage(center.x, center.y,
			center.z);
	}

	/**
	 * Gets the first node of the main primary path of this tree
	 *
	 * @return the root node, or null if the main primary path is undefined for this
	 *         tree.
	 */
	public PointInImage getRoot() {
		if (isEmpty()) return null;
		Path rootPath = get(0);
		if (!rootPath.isPrimary()) { // how would this ever happen?
			rootPath = null;
			for (final Path p : list()) {
				if (p.isPrimary()) {
					rootPath = p;
					break;
				}
			}
		}
		return (rootPath == null) ? null : rootPath.getNode(0);
	}

	public SNTPoint getCentroid() {
		return SNTPoint.average(getNodes());
	}

	/**
	 * Specifies the offset to be used when rendering this Tree in a
	 * {@link TracerCanvas}. Path coordinates remain unaltered.
	 *
	 * @param xOffset the x offset (in pixels)
	 * @param yOffset the y offset (in pixels)
	 * @param zOffset the z offset (in pixels)
	 */
	public void applyCanvasOffset(final double xOffset, final double yOffset,
		final double zOffset)
	{
		final PointInCanvas offset = new PointInCanvas(xOffset, yOffset, zOffset);
		tree.stream().forEach(p -> p.setCanvasOffset(offset));
	}

	/**
	 * Translates the tree by the specified offset.
	 *
	 * @param xOffset the x offset
	 * @param yOffset the y offset
	 * @param zOffset the z offset
	 */
	public void translate(final double xOffset, final double yOffset,
		final double zOffset)
	{
		tree.parallelStream().forEach(p -> {
			for (int node = 0; node < p.size(); node++) {
				p.precise_x_positions[node] += xOffset;
				p.precise_y_positions[node] += yOffset;
				p.precise_z_positions[node] += zOffset;
			}
		});
		if (box != null) {
			box.origin().x += xOffset;
			box.origin().y += yOffset;
			box.origin().z += zOffset;
			box.originOpposite().x += xOffset;
			box.originOpposite().y += yOffset;
			box.originOpposite().z += zOffset;
		}
	}

	/**
	 * Scales the tree by the specified factors.
	 *
	 * @param xScale the scaling factor for x coordinates
	 * @param yScale the scaling factor for y coordinates
	 * @param zScale the scaling factor for z coordinates
	 */
	public void scale(final double xScale, final double yScale,
		final double zScale)
	{
		tree.parallelStream().forEach(p -> {
			for (int node = 0; node < p.size(); node++) {
				p.precise_x_positions[node] *= xScale;
				p.precise_y_positions[node] *= yScale;
				p.precise_z_positions[node] *= zScale;
			}
		});
		if (box != null) {
			box.origin().x *= xScale;
			box.origin().y *= yScale;
			box.origin().z *= zScale;
			box.originOpposite().x *= xScale;
			box.originOpposite().y *= yScale;
			box.originOpposite().z *= zScale;
		}
	}

	/**
	 * Rotates the tree.
	 *
	 * @param axis the rotation axis. Either {@link #X_AXIS}, {@link #Y_AXIS}, or
	 *          {@link #Z_AXIS}
	 * @param angle the rotation angle in degrees
	 */
	public void rotate(final int axis, final double angle) {
		// See http://www.petercollingridge.appspot.com/3D-tutorial
		if (Double.isNaN(angle)) throw new IllegalArgumentException(
			"Angle not valid");
		final double radAngle = Math.toRadians(angle);
		final double sin = Math.sin(radAngle);
		final double cos = Math.cos(radAngle);
		switch (axis) {
			case Z_AXIS:
				tree.parallelStream().forEach(p -> {
					for (int node = 0; node < p.size(); node++) {
						final PointInImage pim = p.getNode(node);
						final double x = pim.x * cos - pim.y * sin;
						final double y = pim.y * cos + pim.x * sin;
						p.moveNode(node, new PointInImage(x, y, pim.z));
					}
				});
				break;
			case Y_AXIS:
				tree.parallelStream().forEach(p -> {
					for (int node = 0; node < p.size(); node++) {
						final PointInImage pim = p.getNode(node);
						final double x = pim.x * cos - pim.z * sin;
						final double z = pim.z * cos + pim.x * sin;
						p.moveNode(node, new PointInImage(x, pim.y, z));
					}
				});
				break;
			case X_AXIS:
				tree.parallelStream().forEach(p -> {
					for (int node = 0; node < p.size(); node++) {
						final PointInImage pim = p.getNode(node);
						final double y = pim.y * cos - pim.z * sin;
						final double z = pim.z * cos + pim.y * sin;
						p.moveNode(node, new PointInImage(pim.x, y, z));
					}
				});
				break;
			default:
				throw new IllegalArgumentException("Unrecognized rotation axis" + axis);
		}
		if (box != null) box.setComputationNeeded(true);
	}

	/**
	 * Gets the all the points (path nodes) forming this tree.
	 *
	 * @return the points
	 */
	public List<PointInImage> getPoints() {
		final List<PointInImage> list = new ArrayList<>();
		for (final Path p : tree) {
			list.addAll(p.getPointInImageList());
		}
		return list;
	}

	/**
	 * Assesses whether this Tree has depth.
	 *
	 * @return true, if is 3D
	 */
	public boolean is3D() {
		final List<PointInImage> points = getPoints();
		final double zRef = points.get(0).z;
		for (int i = 1; i < points.size(); i++) {
			if (points.get(i).z != zRef) return true;
		}
		return false;
	}

	/**
	 * Associates a bounding box to this tree.
	 *
	 * @param box the BoundingBox, typically referring to the image associated
	 *          with this tree
	 */
	public void setBoundingBox(final BoundingBox box) {
		this.box = (box == null) ? null : new TreeBoundingBox(box);
	}

	/**
	 * Gets the bounding box associated with this tree.
	 *
	 * @return the BoundingBox. It will be computed if no boundaries have been set.
	 */
	public BoundingBox getBoundingBox() {
		return getBoundingBox(box == null);
	}

	/**
	 * Gets the bounding box associated with this tree.
	 *
	 * @param computeIfUnset if {@code true} no BoundingBox has been explicitly
	 *          set, and, a BoundingBox will be compute from all the nodes of this
	 *          Tree
	 * @return the BoundingBox
	 */
	public BoundingBox getBoundingBox(final boolean computeIfUnset) {
		final boolean compute = computeIfUnset || (box != null && box.isComputationNeeded());
		if (compute && box == null) box = new TreeBoundingBox();
		if (compute) box.compute(getPoints().iterator());
		return box;
	}

	/**
	 * Gets an empty image capable of holding the skeletonized version of this
	 * tree.
	 *
	 * @param multiDThreePaneView the pane flag indicating the SNT view for this
	 *          image e.g., {@link MultiDThreePanes#XY_PLANE}
	 * @return the empty 8-bit {@link ImagePlus} container
	 */
	public ImagePlus getImpContainer(final int multiDThreePaneView) {
		if (tree.isEmpty()) throw new IllegalArgumentException(
			"tree contains no paths");
		// TODO: this should be handled by BoundingBox
		final TreeStatistics tStats = new TreeStatistics(this);
		final SummaryStatistics xCoordStats = tStats.getSummaryStats(
			TreeAnalyzer.X_COORDINATES);
		final SummaryStatistics yCoordStats = tStats.getSummaryStats(
			TreeAnalyzer.Y_COORDINATES);
		final SummaryStatistics zCoordStats = tStats.getSummaryStats(
			TreeAnalyzer.Z_COORDINATES);
		final PointInImage p1 = new PointInImage(xCoordStats.getMin(), yCoordStats
			.getMin(), zCoordStats.getMin());
		final PointInImage p2 = new PointInImage(xCoordStats.getMax(), yCoordStats
			.getMax(), zCoordStats.getMax());
		final Path referencePath = tree.iterator().next();
		p1.onPath = referencePath;
		p2.onPath = referencePath;
		final PointInCanvas bound1 = p1.getUnscaledPoint(multiDThreePaneView);
		final PointInCanvas bound2 = p2.getUnscaledPoint(multiDThreePaneView);

		// Padding is required to accommodate "rounding errors"
		// in PathAndFillManager.setPathPointsInVolume()
		final int xyPadding = 5; // 5 extra pixels on each margin
		final int zPadding = 2; // 1 slice above / below last point
		final int w = (int) (bound2.x - bound1.x) + xyPadding;
		final int h = (int) (bound2.y - bound1.y) + xyPadding;
		int d = (int) (bound2.z - bound1.z);
		if (d < 1) d = 1;
		if (d > 1) d += zPadding;
		return IJ.createImage(null, w, h, d, 8);
	}

	public void skeletonize(final ImagePlus destinationImp, final int value) {
		if (destinationImp.getType() != ImagePlus.GRAY16) {
			throw new IllegalArgumentException("Only 16-bit images supported");
		}
		initPathAndFillManager();
		final int width = destinationImp.getWidth();
		final int height = destinationImp.getHeight();
		final int depth = destinationImp.getNSlices();
		final int channel = destinationImp.getC();
		final int frame = destinationImp.getT();
		final ImageStack s = destinationImp.getStack();
		final short[][] slices_data = new short[depth][];
		for (int z = 0; z < depth; ++z) {
			slices_data[z] = (short[]) s.getPixels(destinationImp.getStackIndex(channel, z +
				1, frame));
		}
		pafm.assignSpatialSettings(destinationImp);
		pafm.setPathPointsInVolume(list(), slices_data, value, width, height, depth);
		pafm.resetSpatialSettings(); // do not tether this tree to destinationImp
	}

	/**
	 * Retrieves the number of paths in this tree.
	 *
	 * @return Returns the number of paths in this tree.
	 */
	public int size() {
		return tree.size();
	}

	/**
	 * Assigns a color to all the paths in this tree. Note that assigning a
	 * non-null color will remove node colors from Paths.
	 *
	 * @param color the color to be applied.
	 * @see Path#hasNodeColors()
	 */
	public void setColor(final ColorRGB color) {
		tree.stream().forEach(p -> {
			p.setColorRGB(color);
			if (color != null) p.setNodeColors(null);
		});
	}

	/**
	 * Assigns a color to all the paths in this tree. Note that assigning a non-null
	 * color will remove node colors from Paths.
	 * 
	 * @param color the color to be applied, either a 1) HTML color codes starting
	 *              with hash ({@code #}), a color preset ("red", "blue", etc.), or
	 *              integer triples of the form {@code r,g,b} and range
	 *              {@code [0, 255]}
	 */
	public void setColor(final String color) {
		setColor(new ColorRGB(color));
	}

	/**
	 * Assigns a fixed radius to all the nodes in this tree.
	 *
	 * @param r the radius to be assigned. Setting it to 0 or Double.NaN removes
	 *          the radius attribute from the Tree
	 */
	public void setRadii(final double r) {
		tree.parallelStream().forEach(p -> p.setRadius(r));
	}

	/**
	 * Sets an identifying label for this Tree.
	 *
	 * @param label the identifying string
	 */
	public void setLabel(final String label) {
		this.label = label;
	}

	/**
	 * Returns the identifying label of this tree. When importing files, the label
	 * typically defaults to the imported filename,
	 *
	 * @return the Tree label (or null) if none has been set.
	 */
	public String getLabel() {
		return label;
	}

	public Collection<SWCPoint> getNodes() throws IllegalArgumentException {
		initPathAndFillManager();
		final Path[] primaryPaths = pafm.getPathsStructured();
		if (primaryPaths.length != 1) throw new IllegalArgumentException("Tree contains multiple roots!");
		final HashSet<Path> connectedPaths = new HashSet<>();
		final LinkedList<Path> nextPathsToConsider = new LinkedList<>();
		nextPathsToConsider.add(primaryPaths[0]);
		while (nextPathsToConsider.size() > 0) {
			final Path currentPath = nextPathsToConsider.removeFirst();
			connectedPaths.add(currentPath);
			for (final Path joinedPath : currentPath.somehowJoins) {
				if (!connectedPaths.contains(joinedPath)) nextPathsToConsider.add(
					joinedPath);
			}
		}
		try {
			return pafm.getSWCFor(connectedPaths);
		} catch (final SWCExportException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}

	/**
	 * Assembles a DirectedGraph from this Tree.
	 *
	 * @return the Tree's graph with edge weights corresponding to inter-node
	 *         Euclidean distances
	 */
	public DefaultDirectedGraph<SWCPoint, DefaultWeightedEdge> getGraph() {
		return GraphUtils.createGraph(this);
	}

	/**
	 * Script-friendly method for creating a Tree from a reconstruction file.
	 *
	 * @param filePath the absolute path to the file (.Traces, (e)SWC or JSON) to be
	 *                 imported
	 * @return the Tree instance, or null if file could not be imported
	 */
	public static Tree fromFile(final String filePath) {
		try {
			return new Tree(filePath);
		} catch (final IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Retrieves a list of {@link Tree}s from reconstruction files stored in a
	 * common directory.
	 *
	 * @param dir the directory containg the reconstruction files (.(e)swc, .traces,
	 *            .json extension)
	 * @return the list of imported {@link Tree}s. An empty list is retrieved if
	 *         {@code dir} is not a valid, readable directory.
	 */
	public static List<Tree> listFromDir(final String dir) {
		return listFromDir(dir, "");
	}

	/**
	 * Retrieves a list of {@link Tree}s from reconstruction files stored in a
	 * common directory matching the specified criteria.
	 *
	 * @param dir     the directory containing the reconstruction files (.(e)swc,
	 *                .traces, .json extension)
	 * @param pattern the filename substring (case sensitive) to be matched. Only
	 *                filenames containing {@code pattern} will be imported from the
	 *                directory
	 * @return the list of imported {@link Tree}s. An empty list is retrieved if
	 *         {@code dir} is not a valid, readable directory.
	 */
	public static List<Tree> listFromDir(final String dir, final String pattern) {
		final FileFilter filter = (file) -> {
			final String name = file.getName();
			if (!name.contains(pattern))
				return false;
			final String lName = name.toLowerCase();
			return file.canRead() && (lName.endsWith("swc") || lName.endsWith(".traces") || lName.endsWith(".json"));
		};
		final List<Tree> trees = new ArrayList<>();
		if (dir == null) return trees;
		final File dirFile = new File(dir);
		if (!dirFile.isDirectory() || !dirFile.exists() || !dirFile.canRead()) {
			return trees;
		}
		final File treeFiles[] = dirFile.listFiles(filter);
		if (treeFiles == null || treeFiles.length == 0) {
			return trees;
		}
		for (final File treeFile : treeFiles) {
			final Tree tree = Tree.fromFile(treeFile.getAbsolutePath());
			if (tree != null)
				trees.add(tree);
		}
		return trees;
	}

	/**
	 * Saves this Tree to an SWC file.
	 *
	 * @param filePath the absolute path of the output file. {@code .swc} is
	 *                 automatically appended if {@code filePath} does not include
	 *                 an extension. Also, if a label has been assigned,
	 *                 {@code filePath} can also be a directory.
	 * @return true, if file successfully saved.
	 * @see #setLabel(String)
	 */
	public boolean saveAsSWC(final String filePath) {
		if (list() == null || list().isEmpty() || filePath == null || filePath.isEmpty())
			return false;
		initPathAndFillManager();
		File file = new File(filePath);
		if (file.isDirectory() && getLabel() != null)
			file = new File(file.getAbsolutePath(), getLabel());
		return pafm.exportAllPathsAsSWC(file.getAbsolutePath());
	}

	private void initPathAndFillManager() {
		if (pafm == null) pafm = new PathAndFillManager();
		if (pafm.size() == 0) for (final Path p : list()) pafm.addPath(p);
	}

	@Override
	public Tree clone() {
		final Tree clone = new Tree();
		clone.setLabel(getLabel());
		clone.setBoundingBox(box);
		for (final Path path : list()) clone.add(path.clone());
		return clone;
	}
	
	private class TreeBoundingBox extends BoundingBox {

		private boolean dimensionsNeedToBeComputed;

		private TreeBoundingBox(final BoundingBox box) {
			this.origin = box.origin();
			this.originOpposite = box.originOpposite();
			this.xSpacing = box.xSpacing;
			this.ySpacing = box.ySpacing;
			this.zSpacing = box.zSpacing;
			this.info = box.info;
			this.spacingUnit = box.getUnit();
		}

		private TreeBoundingBox() {
			super();
		}

		private void setComputationNeeded(final boolean bool) {
			dimensionsNeedToBeComputed = bool;
			if (bool) reset();
		}

		private boolean isComputationNeeded() {
			return dimensionsNeedToBeComputed;
		}
	}
}
