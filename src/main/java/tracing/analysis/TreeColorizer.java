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

package tracing.analysis;

import java.awt.Color;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.scijava.Context;
import org.scijava.plugin.Parameter;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import net.imglib2.display.ColorTable;
import sholl.ProfileEntry;
import sholl.UPoint;
import sholl.math.LinearProfileStats;
import tracing.Path;
import tracing.SNT;
import tracing.Tree;
import tracing.plugin.DistributionCmd;
import tracing.util.PointInImage;

/**
 * Class for color coding {@link Tree}s.
 *
 * @author Tiago Ferreira
 *
 */
public class TreeColorizer {

	/* For convenience keep references to TreeAnalyzer fields */
	public static final String BRANCH_ORDER = TreeAnalyzer.BRANCH_ORDER;
	public static final String LENGTH = TreeAnalyzer.LENGTH;
	public static final String N_BRANCH_POINTS = TreeAnalyzer.N_BRANCH_POINTS;
	public static final String N_NODES = TreeAnalyzer.N_NODES;
	public static final String MEAN_RADIUS = TreeAnalyzer.MEAN_RADIUS;
	public static final String NODE_RADIUS = TreeAnalyzer.NODE_RADIUS;
	public static final String X_COORDINATES = TreeAnalyzer.X_COORDINATES;
	public static final String Y_COORDINATES = TreeAnalyzer.Y_COORDINATES;
	public static final String Z_COORDINATES = TreeAnalyzer.Z_COORDINATES;
	private static final String INTERNAL_COUNTER = "";
	public static final String FIRST_TAG = "Tags";


	@Parameter
	private LUTService lutService;

	protected ArrayList<Path> paths;
	protected ColorTable colorTable;
	protected boolean integerScale;
	protected double min = Double.MAX_VALUE;
	protected double max = Double.MIN_VALUE;
	private Map<String, URL> luts;
	private int internalCounter = 1;

	/**
	 * Instantiates the Colorizer.
	 *
	 * @param context
	 *            the SciJava application context providing the services required by
	 *            the class
	 */
	public TreeColorizer(final Context context) {
		context.inject(this);
	}

	private void initLuts() {
		if (luts == null)
			luts = lutService.findLUTs();
	}

	protected ColorTable getColorTable(final String lut) {
		initLuts();
		for (final Map.Entry<String, URL> entry : luts.entrySet()) {
			if (entry.getKey().contains(lut)) {
				try {
					return lutService.loadLUT(entry.getValue());
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	protected void mapToProperty(final String measurement, final ColorTable colorTable) {
		if (colorTable == null)
			return;
		if (measurement == null)
			throw new IllegalArgumentException("Parameter cannot be null");
		final String cMeasurement = new StringBuilder().append(measurement.substring(0, 1).toUpperCase())
				.append(measurement.substring(1)).toString();
		this.colorTable = colorTable;
		switch (cMeasurement) {
		case BRANCH_ORDER:
		case LENGTH:
		case MEAN_RADIUS:
		case N_NODES:
		case N_BRANCH_POINTS:
		case INTERNAL_COUNTER:
		case FIRST_TAG:
			mapToPathProperty(cMeasurement, colorTable);
			break;
		case X_COORDINATES:
		case Y_COORDINATES:
		case Z_COORDINATES:
		case NODE_RADIUS:
			mapToNodeProperty(cMeasurement, colorTable);
			break;
		default:
			throw new IllegalArgumentException("Unknown parameter");
		}
	}

	private void mapToPathProperty(final String measurement, final ColorTable colorTable) {
		final List<MappedPath> mappedPaths = new ArrayList<>();
		switch (measurement) {
		case BRANCH_ORDER:
			integerScale = true;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.getOrder()));
			break;
		case LENGTH:
			integerScale = false;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, p.getLength()));
			break;
		case MEAN_RADIUS:
			integerScale = false;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, p.getMeanRadius()));
			break;
		case N_NODES:
			integerScale = true;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.size()));
			break;
		case N_BRANCH_POINTS:
			integerScale = true;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.findJoinedPoints().size()));
			break;
		case INTERNAL_COUNTER:
			integerScale = true;
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) internalCounter));
			break;
		case FIRST_TAG:
			final List<MappedTaggedPath> mappedTaggedPaths = new ArrayList<>();
			final TreeSet<String> tags = new TreeSet<>();
			for (final Path p : paths) {
				final MappedTaggedPath mp = new MappedTaggedPath(p);
				mappedTaggedPaths.add(mp);
				tags.add(mp.mappedTag);
			}
			final int nTags = tags.size();
			for (final MappedTaggedPath p : mappedTaggedPaths) {
				mappedPaths.add(new MappedPath(p.path, (double)tags.headSet(p.mappedTag).size()/nTags));
			}
			break;
		default:
			throw new IllegalArgumentException("Unknown parameter");
		}
		for (final MappedPath mp : mappedPaths) {
			mp.path.setColor(getColor(mp.mappedValue));
		}
	}

	private void mapToNodeProperty(final String measurement, final ColorTable colorTable) {
		if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
			final TreeStatistics tStats = new TreeStatistics(new Tree(paths));
			final SummaryStatistics sStats = tStats.getSummaryStats(measurement);
			setMinMax(sStats.getMin(), sStats.getMax());
		}
		for (final Path p : paths) {
			final Color[] colors = new Color[p.size()];
			for (int node = 0; node < p.size(); node++) {
				double value;
				switch (measurement) {
				case X_COORDINATES:
					value = p.getPointInImage(node).x;
					break;
				case Y_COORDINATES:
					value = p.getPointInImage(node).y;
					break;
				case Z_COORDINATES:
					value = p.getPointInImage(node).z;
					break;
				case NODE_RADIUS:
					value = p.getNodeRadius(node);
					break;
				default:
					throw new IllegalArgumentException("Unknow parameter");
				}
				colors[node] = getColor(value);
			}
			p.setNodeColors(colors);
		}
	}

	/**
	 * Colorizes a tree using Sholl data.
	 *
	 * @param tree       the tree to be colorized
	 * @param stats      the LinearProfileStats instance containing the mapping
	 *                   profile. if a polynomial fit has been successfully
	 *                   performed, mapping is done against the fitted data,
	 *                   otherwise sampled intersections are used.
	 * @param colorTable the color table specifying the color mapping. Null not
	 *                   allowed.
	 */
	public void colorize(final Tree tree, final LinearProfileStats stats, final ColorTable colorTable) {
		final UPoint ucenter = stats.getProfile().center();
		if (ucenter == null) {
			throw new IllegalArgumentException("Center unknown");
		}
		paths = tree.list();
		this.colorTable = colorTable;
		final boolean useFitted = stats.validFit();
		SNT.log("Mapping to fitted values: " + useFitted);
		setMinMax(stats.getMin(useFitted), stats.getMax(useFitted));
		final PointInImage center = new PointInImage(ucenter.x, ucenter.y, ucenter.z);
		final double stepSize = stats.getProfile().stepSize();
		final double stepSizeSq = stepSize * stepSize;
		for (final ProfileEntry entry : stats.getProfile().entries()) {
			final double entryRadiusSqed = entry.radiusSquared();
			for (final Path p : paths) {
				for (int node = 0; node < p.size(); node++) {
					final double dx = center.distanceSquaredTo(p.getPointInImage(node));
					if (dx >= entryRadiusSqed && dx < entryRadiusSqed + stepSizeSq) {
						p.setNodeColor(getColor(entry.count), node);
					}
				}
			}
		}
	}

	private Color getColor(final double mappedValue) {
		final int idx;
		if (mappedValue <= min)
			idx = 0;
		else if (mappedValue > max)
			idx = colorTable.getLength() - 1;
		else
			idx = (int) Math.round((colorTable.getLength() - 1) * (mappedValue - min) / (max - min));
		return new Color(colorTable.get(ColorTable.RED, idx), colorTable.get(ColorTable.GREEN, idx),
				colorTable.get(ColorTable.BLUE, idx));
	}

	/**
	 * Sets the LUT mapping bounds.
	 *
	 * @param min
	 *            the mapping lower bound (i.e., the highest measurement value for
	 *            the LUT scale). It is automatically calculated (the default) when
	 *            set to Double.NaN
	 * @param max
	 *            the mapping upper bound (i.e., the highest measurement value for
	 *            the LUT scale).It is automatically calculated (the default) when
	 *            set to Double.NaN.
	 */
	public void setMinMax(final double min, final double max) {
		if (!Double.isNaN(min) && !Double.isNaN(max) && min > max)
			throw new IllegalArgumentException("min > max");
		this.min = (Double.isNaN(min)) ? Double.MAX_VALUE : min;
		this.max = (Double.isNaN(max)) ? Double.MIN_VALUE : max;
	}

	/**
	 * Returns the mapping bounds
	 *
	 * @return a two-element array with current {minimum, maximum} mapping bounds
	 */
	public double[] getMinMax() {
		return new double[] {min, max};
	}

	/**
	 * Colorizes a tree after the specified measurement.
	 *
	 * @param tree
	 *            the tree to be colorized
	 * @param measurement
	 *            the measurement ({@link BRANCH_ORDER} }{@link LENGTH}, etc.)
	 * @param colorTable
	 *            the color table specifying the color mapping. Null not allowed.
	 */
	public void colorize(final Tree tree, final String measurement, final ColorTable colorTable) {
		this.paths = tree.list();
		mapToProperty(measurement, colorTable);
	}

	/**
	 * Colorizes a tree after the specified measurement. Mapping bounds are
	 * automatically determined.
	 *
	 * @param tree
	 *            the tree to be plotted
	 * @param measurement
	 *            the measurement ({@link BRANCH_ORDER} }{@link LENGTH}, etc.)
	 * @param lut
	 *            the lookup table specifying the color mapping
	 *
	 */
	public void colorize(final Tree tree, final String measurement, final String lut) {
		colorize(tree, measurement, getColorTable(lut));
	}

	/**
	 * Colorizes a list of trees, with each tree being assigned a LUT index.
	 *
	 * @param trees
	 *            the list of trees to be colorized
	 * @param lut
	 *            the lookup table specifying the color mapping
	 *
	 */
	public void colorizeTrees(final List<Tree> trees, final String lut) {
		setMinMax(1, trees.size());
		for (final ListIterator<Tree> it = trees.listIterator(); it.hasNext();) {
			colorize(it.next(), INTERNAL_COUNTER, lut);
			internalCounter = it.nextIndex();
		}
	}

	/**
	 * Gets the available LUTs.
	 *
	 * @return the set of keys, corresponding to the set of LUTs available
	 */
	public Set<String> getAvalailableLuts() {
		initLuts();
		return luts.keySet();
	}

	private class MappedPath {

		private final Path path;
		private final Double mappedValue;

		private MappedPath(final Path path, final Double mappedValue) {
			this.path = path;
			this.mappedValue = mappedValue;
			if (mappedValue > max)
				max = mappedValue;
			if (mappedValue < min)
				min = mappedValue;
		}
	}

	private class MappedTaggedPath {

		private final Pattern pattern = Pattern.compile("\\{(\\w+)\\b");
		private final Path path;
		private final String mappedTag;

		private MappedTaggedPath(final Path path) {
			this.path = path;
			final Matcher matcher = pattern.matcher(path.getName());
			mappedTag = (matcher.find()) ? matcher.group(1).toString() : "";
		}
	}

	/* IDE debug method */
	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final List<Tree> trees = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			final Tree tree = new Tree(DistributionCmd.randomPaths());
			tree.rotate(Tree.Z_AXIS, i * 20);
			trees.add(tree);
		}
		final TreePlot plot = new TreePlot(ij.context());
		plot.addTrees(trees, "Ice.lut");
		plot.addLookupLegend();
		plot.showPlot();
	}

}
