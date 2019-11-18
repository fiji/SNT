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

package sc.fiji.snt.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.scijava.Context;

import net.imagej.ImageJ;
import net.imagej.display.ColorTables;
import net.imglib2.display.ColorTable;

import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.annotation.AllenCompartment;
import sc.fiji.snt.annotation.AllenUtils;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SNTPoint;
import sc.fiji.snt.viewer.MultiViewer2D;
import sc.fiji.snt.viewer.OBJMesh;
import sc.fiji.snt.viewer.Viewer2D;
import sc.fiji.snt.viewer.Viewer3D;

/**
 * Class for color coding groups of {@link Tree}s.
 * <p>
 * After a mapping property and a color table (LUT) are specified, the mapping
 * proceeds as follows: 1) Each Tree in the group is measured for the mapping
 * property; 2) each measurement is mapped to a LUT entry that is used to color
 * each Tree. Mapping limits can be optionally specified
 * </p>
 *
 * @author Tiago Ferreira
 */
public class MultiTreeColorMapper extends ColorMapper {

	/** Mapping property: Cable length */
	public static final String LENGTH = TreeAnalyzer.LENGTH;

	/** Mapping property: Count of all branch points */
	public static final String N_BRANCH_POINTS = TreeAnalyzer.N_BRANCH_POINTS;

	/** Mapping property: Count of all tips (end points) */
	public static final String N_TIPS = MultiTreeStatistics.N_TIPS;

	/** Mapping property: Highest {@link Path#getOrder() path order} */
	public static final String HIGHEST_PATH_ORDER = MultiTreeStatistics.HIGHEST_PATH_ORDER;

	/** Mapping property: Assigned Tree value */
	public static final String ASSIGNED_VALUE = "Assigned value";

	/**
	 * Mapping property (dummy): Each Tree in the collection is assigned an
	 * incremental LUT entry
	 */
	public static final String ID = "Cell/id";

	public static final String[] PROPERTIES = { //
		ID, LENGTH, N_BRANCH_POINTS, N_TIPS, HIGHEST_PATH_ORDER//
	};

	private final List<MappedTree> mappedTrees;
	private int internalCounter = 1;

	/**
	 * Instantiates the MultiTreeColorMapper.
	 *
	 * @param trees the group of trees to be mapped,
	 */
	public MultiTreeColorMapper(final Collection<Tree> trees) {
		mappedTrees = new ArrayList<>();
		for (final Tree tree : trees) {
			if (tree.size() > 0) mappedTrees.add(new MappedTree(tree));
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ColorMapper#colorize(java.lang.String,
	 * net.imglib2.display.ColorTable)
	 */
	@Override
	public void map(final String measurement, final ColorTable colorTable) {
		try {
			final String cMeasurement = super.normalizedMeasurement(measurement);
			mapInternal(cMeasurement, colorTable);
		} catch (final IllegalArgumentException ignored) {
			final String educatedGuess = tryToGuessMetric(measurement);
			System.out.println("Mapping to \""+ measurement +"\" failed. Assuming \""+ educatedGuess+"\"... ");
			if ("unknown".equals(educatedGuess))
				throw new IllegalArgumentException("Unknown parameter: "+ measurement);
			else
				mapInternal(educatedGuess, colorTable);
		}
	}

	public void mapRootDistanceToCentroid(final AllenCompartment compartment, final ColorTable colorTable) {
		if (compartment == null || colorTable == null) throw new IllegalArgumentException("compartment/colorTable cannot be null");
		final OBJMesh mesh = compartment.getMesh();
		if (mesh == null) throw new IllegalArgumentException("Cannot proceed: compartment mesh is not available");
		integerScale = false;
		this.colorTable = colorTable;
		for (final MappedTree mt : mappedTrees) {
			final PointInImage root = mt.tree.getRoot();
			final SNTPoint centroid = mesh.getCentroid( (AllenUtils.isLeftHemisphere(root)) ? "left" : "right");
			final PointInImage pimCentroid = new PointInImage(centroid.getX(),centroid.getY(), centroid.getZ());
			mt.value = root.distanceTo(pimCentroid);
		}
		assignMinMax();
		for (final MappedTree mt : mappedTrees) {
			mt.tree.setColor(getColorRGB(mt.value));
		}
	}

	private void mapInternal(final String measurement, final ColorTable colorTable) {
		super.map(measurement, colorTable);
		for (final MappedTree mt : mappedTrees) {
			final TreeAnalyzer analyzer = new TreeAnalyzer(mt.tree);
			switch (measurement) {
				case ASSIGNED_VALUE:
					mt.value = mt.tree.getAssignedValue();
					break;
				case HIGHEST_PATH_ORDER:
					integerScale = true;
					mt.value = analyzer.getHighestPathOrder();
					break;
				case LENGTH:
					mt.value = analyzer.getCableLength();
					break;
				case N_BRANCH_POINTS:
					integerScale = true;
					mt.value = analyzer.getBranchPoints().size();
					break;
				case N_TIPS:
					integerScale = true;
					mt.value = analyzer.getTips().size();
					break;
				case ID:
					integerScale = true;
					mt.value = internalCounter++;
					break;
				default:
					throw new IllegalArgumentException("Unknown parameter: "+ measurement);
			}
		}
		assignMinMax();
		for (final MappedTree mt : mappedTrees) {
			mt.tree.setColor(getColorRGB(mt.value));
		}
	}

	private String tryToGuessMetric(final String guess) {
		String normGuess = guess.toLowerCase();
		if (normGuess.contains("length")) {
			normGuess = LENGTH;
		}
		else if (normGuess.indexOf("branch points") > -1 || normGuess.indexOf("bps") > -1) {
			normGuess = N_BRANCH_POINTS;
		}
		else if (normGuess.indexOf("tips") != -1 || normGuess.indexOf("endings") != -1 || normGuess.indexOf("terminals") != -1) {
			normGuess = N_TIPS;
		}
		else if (normGuess.indexOf("order") != -1) {
			normGuess = HIGHEST_PATH_ORDER;
		} else {
			normGuess = "unknown";
		}
		return normGuess;
	}

	public List<Tree> sortedMappedTrees() {
		Collections.sort(mappedTrees, new Comparator<MappedTree>() {
			@Override
			public int compare(final MappedTree t1, final MappedTree t2) {
				return Double.compare(t1.value, t2.value);
			}
		});
		final List<Tree> sortedTrees = new ArrayList<>(mappedTrees.size());
		mappedTrees.forEach(mp -> sortedTrees.add(mp.tree));
		return sortedTrees;
	}

	private void assignMinMax() {
		if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
			min = Double.MAX_VALUE;
			max = Double.MIN_VALUE;
			for (final MappedTree mt : mappedTrees) {
				if (mt.value < min) min = mt.value;
				if (mt.value > max) max = mt.value;
			}
		}
	}

	public MultiViewer2D getMultiViewer() {
		final List<Viewer2D> viewers = new ArrayList<>(mappedTrees.size());
		sortedMappedTrees().forEach(tree -> {
			final Viewer2D viewer = new Viewer2D(new Context());
			viewer.addTree(tree);
			viewers.add(viewer);
		});
		final MultiViewer2D multiViewer = new MultiViewer2D(viewers);
		if (colorTable != null && !mappedTrees.isEmpty()) {
			double groupMin = Double.MAX_VALUE;
			double groupMax = Double.MIN_VALUE;
			for (final MappedTree mt : mappedTrees) {
				if (mt.value < groupMin) groupMin = mt.value;
				if (mt.value > groupMax) groupMax = mt.value;
			}
			multiViewer.setColorBarLegend(colorTable, groupMin, groupMax);
		}
		return multiViewer;
	}

	private class MappedTree {

		public final Tree tree;
		public double value;

		public MappedTree(final Tree tree) {
			this.tree = tree;
		}
	}

	/* IDE debug method */
	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final List<Tree> trees = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			final Tree tree = new Tree(SNTUtils.randomPaths());
			tree.rotate(Tree.Z_AXIS, i * 20);
			trees.add(tree);
		}
		final MultiTreeColorMapper mapper = new MultiTreeColorMapper(trees);
		mapper.map("length", ColorTables.ICE);
		final Viewer3D viewer = new Viewer3D();
		for (final Tree tree : trees)
			viewer.addTree(tree);
		final double[] limits = mapper.getMinMax();
		viewer.addColorBarLegend(ColorTables.ICE, (float) limits[0],
			(float) limits[1]);
		viewer.show();
	}

}
