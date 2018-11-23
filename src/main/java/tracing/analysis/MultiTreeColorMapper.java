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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.imagej.ImageJ;
import net.imagej.display.ColorTables;
import net.imglib2.display.ColorTable;
import tracing.SNT;
import tracing.Tree;
import tracing.plot.TreePlot3D;

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
	public static final String TOTAL_LENGTH = "Total length";

	/** Mapping property: Count of all branch points */
	public static final String TOTAL_N_BRANCH_POINTS = "Total no. of branch points";

	/** Mapping property: Count of all tips (end points) */
	public static final String TOTAL_N_TIPS = "Total no. of tips";

	/** Mapping property: Strahler root number */
	public static final String ROOT_NUMBER = "Strahler root number";

	/**
	 * Mapping property (dummy): Each Tree in the collection is assigned an
	 * incremental LUT entry
	 */
	public static final String ID = "Cell/Id";

	public static final String[] PROPERTIES = { //
			ID, TOTAL_LENGTH, TOTAL_N_BRANCH_POINTS, TOTAL_N_TIPS, ROOT_NUMBER//
	};

	private final Collection<MappedTree> mappedTrees;
	private int internalCounter = 1;

	/**
	 * Instantiates the MultiTreeColorMapper.
	 * 
	 * @param trees the group of trees to be mapped,
	 */
	public MultiTreeColorMapper(final Collection<Tree> trees) {
		mappedTrees = new ArrayList<>();
		for (final Tree tree : trees) {
			mappedTrees.add(new MappedTree(tree));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tracing.analysis.ColorMapper#colorize(java.lang.String,
	 * net.imglib2.display.ColorTable)
	 */
	@Override
	public void colorize(final String measurement, final ColorTable colorTable) {
		super.colorize(measurement, colorTable);
		final String cMeasurement = super.normalizedMeasurement(measurement);
		for (final MappedTree mt : mappedTrees) {
			final TreeAnalyzer analyzer = new TreeAnalyzer(mt.tree);
			switch (cMeasurement) {
			case ROOT_NUMBER:
				integerScale = true;
				mt.value = (double) analyzer.getStrahlerRootNumber();
				break;
			case TOTAL_LENGTH:
				mt.value = analyzer.getCableLength();
				break;
			case TOTAL_N_BRANCH_POINTS:
				integerScale = true;
				mt.value = (double) analyzer.getBranchPoints().size();
				break;
			case TOTAL_N_TIPS:
				integerScale = true;
				mt.value = (double) analyzer.getTips().size();
				break;
			case ID:
				integerScale = true;
				mt.value = (double) internalCounter++;
				break;
			default:
				throw new IllegalArgumentException("Unknown parameter");
			}
		}
		assignMinMax();
		for (final MappedTree mt : mappedTrees) {
			mt.tree.setColor(getColorRGB(mt.value));
		}
	}

	private void assignMinMax() {
		if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
			min = Double.MAX_VALUE;
			max = Double.MIN_VALUE;
			for (final MappedTree mt : mappedTrees) {
				if (mt.value < min)
					min = mt.value;
				if (mt.value > max)
					max = mt.value;
			}
		}
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
			final Tree tree = new Tree(SNT.randomPaths());
			tree.rotate(Tree.Z_AXIS, i * 20);
			trees.add(tree);
		}
		final MultiTreeColorMapper mapper = new MultiTreeColorMapper(trees);
		mapper.colorize(MultiTreeColorMapper.TOTAL_LENGTH, ColorTables.ICE);
		final TreePlot3D viewer = new TreePlot3D();
		for (final Tree tree : trees) viewer.add(tree);
		final double[] limits = mapper.getMinMax();
		viewer.addColorBarLegend(ColorTables.ICE, (float) limits[0], (float) limits[1]);
		viewer.show();
	}

}
