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

package tracing.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.imagej.plot.CategoryChart;
import net.imagej.plot.LineSeries;
import net.imagej.plot.PlotService;
import net.imagej.table.DefaultGenericTable;

import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import tracing.Path;
import tracing.Tree;
import tracing.analysis.TreeAnalyzer;

/**
 * Command to perform Horton-Strahler analysis on a {@link Tree}.
 *
 * @author Tiago Ferreira
 */
public class StrahlerCmd extends TreeAnalyzer {

	@Parameter
	private PlotService plotService;

	@Parameter
	private UIService uiService;

	private int maxBranchOrder;
	private int nPathsPreviousOrder;
	private final Map<Integer, Double> nPathsMap = new TreeMap<>();
	private final Map<Integer, Double> bPointsMap = new TreeMap<>();
	private final Map<Integer, Double> bRatioMap = new TreeMap<>();
	private final Map<Integer, Double> tLengthMap = new TreeMap<>();

	public StrahlerCmd(final Tree tree) {
		super(tree);
	}

	@Override
	public void run() {
		if (tree == null || tree.isEmpty()) {
			cancel("<HTML>No Paths to Measure");
			return;
		}
		statusService.showStatus("Measuring Paths...");
		compute();
		updateAndDisplayTable();
		displayPlot();
		statusService.clearStatus();
	}

	public void compute() {
		maxBranchOrder = 1;
		for (final Path p : tree.list()) {
			if (p.getOrder() > maxBranchOrder)
				maxBranchOrder = p.getOrder();
		}
		IntStream.rangeClosed(1, maxBranchOrder).forEach(order -> {

			final ArrayList<Path> groupedPaths = tree.list().stream() // convert set of paths to stream
					.filter(path -> path.getOrder() == order) // include only those of this order
					.collect(Collectors.toCollection(ArrayList::new)); // collect the output in a new list

			// now measure the group
			final TreeAnalyzer analyzer = new TreeAnalyzer(new Tree(groupedPaths));
			if (!analyzer.getParsedTree().isEmpty()) {
				tLengthMap.put(order, analyzer.getCableLength());
				final int nPaths = analyzer.getNPaths();
				nPathsMap.put(order, (double) nPaths);
				bPointsMap.put(order, (double) analyzer.getBranchPoints().size());
				bRatioMap.put(order, (order > 1) ? (double) nPaths / nPathsPreviousOrder : Double.NaN);
				nPathsPreviousOrder = nPaths;
			}
		});
	}

	@Override
	public void updateAndDisplayTable() {
		if (table == null)
			setTable(new DefaultGenericTable(), "Horton-Strahler Analysis");
		IntStream.rangeClosed(1, maxBranchOrder).forEach(order -> {
			table.appendRow();
			final int row = Math.max(0, table.getRowCount() - 1);
			table.set(getCol("Branching order"), row, order);
			table.set(getCol("Horton-Strahler #"), row, maxBranchOrder - order + 1);
			table.set(getCol("Length (Sum)"), row, tLengthMap.get(order));
			table.set(getCol("# Paths"), row, (nPathsMap.get(order).intValue()));
			table.set(getCol("# Branch Points"), row, bPointsMap.get(order).intValue());
			table.set(getCol("Bifurcation ratio"), row, bRatioMap.get(order));
		});
		super.updateAndDisplayTable();
	}

	public void displayPlot() {

		final CategoryChart<Integer> chart = plotService.newCategoryChart(Integer.class);
		final List<Integer> categories = IntStream.rangeClosed(1, maxBranchOrder).boxed().collect(Collectors.toList());
		Collections.sort(categories, Collections.reverseOrder());
		chart.categoryAxis().setManualCategories(categories);

		final LineSeries<Integer> series1 = chart.addLineSeries();
		series1.setLabel("N. Paths");
		series1.setValues(nPathsMap);

		final LineSeries<Integer> series2 = chart.addLineSeries();
		series2.setLabel("N. Branch points");
		series2.setValues(bPointsMap);

		final LineSeries<Integer> series3 = chart.addLineSeries();
		series3.setLabel("Length");
		series3.setValues(tLengthMap);

		chart.categoryAxis().setLabel("Horton-Strahler number");
		uiService.show("SNT: Strahler Plot", chart);
	}

	/**
	 * @return the map containing the number of paths on each order (reverse
	 *         Horton-Strahler number as key and counts as value). Single-point
	 *         paths are ignored. An empty map will be returned if
	 *         {{@link #compute()} has not been called.
	 */
	public Map<Integer, Double> getCountMap() {
		return nPathsMap;
	}

	/**
	 * @return the map containing the number of branch points on each order (reverse
	 *         Horton-Strahler number as key and branch point count as value).
	 *         Single-point paths are ignored. An empty map will be returned if
	 *         {{@link #compute()} has not been called.
	 */
	public Map<Integer, Double> getBranchPointMap() {
		return bPointsMap;
	}

	/**
	 *
	 * @return the highest Horton-Strahler number of the parsed tree.
	 */
	public int getRootNumber() {
		return this.maxBranchOrder;
	}

	/**
	 * @return the map containing the total path lengh on each order (reverse
	 *         Horton-Strahler number as key and sum length as value). Single-point
	 *         paths are ignored. An empty map will be returned if
	 *         {{@link #compute()} has not been called.
	 */
	public Map<Integer, Double> getLengthMap() {
		return tLengthMap;
	}

	/**
	 * @return the map containing th bifurcation ratios between orders (reverse
	 *         Horton-Strahler numbers as key and ratios as value). Single-point
	 *         paths are ignored. An empty map will be returned if
	 *         {{@link #compute()} has not been called.
	 */
	public Map<Integer, Double> getRatioMap() {
		return bRatioMap;
	}

}
