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

package sc.fiji.snt.plugin;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.IntStream;

import org.scijava.plugin.Parameter;
import org.scijava.table.DefaultGenericTable;
import org.scijava.ui.UIService;
import org.scijava.util.Colors;

import net.imagej.ImageJ;
import net.imagej.plot.CategoryChart;
import net.imagej.plot.LineSeries;
import net.imagej.plot.LineStyle;
import net.imagej.plot.MarkerStyle;
import net.imagej.plot.PlotService;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.StrahlerAnalyzer;
import sc.fiji.snt.analysis.TreeAnalyzer;

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

	private final StrahlerAnalyzer sAnalyzer;
	private Map<Integer, Double> tLengthMap;
	private Map<Integer, Double> bPointsMap;
	private Map<Integer, Double> nBranchesMap;
	private Map<Integer, Double> bRatioMap;
	private int maxOrder;


	public StrahlerCmd(final Tree tree) {
		super(tree);
		sAnalyzer = new StrahlerAnalyzer(tree);
	}

	@Override
	public void run() {
		if (tree == null || tree.isEmpty()) {
			cancel("No Paths to Measure");
			return;
		}
		statusService.showStatus("Classifying branches...");
		maxOrder = sAnalyzer.getRootNumber();
		tLengthMap = sAnalyzer.getLengths();
		nBranchesMap = sAnalyzer.getBranchCounts();
		bPointsMap = sAnalyzer.getBranchPointCounts();
		bRatioMap = sAnalyzer.getBifurcationRatios();
		updateAndDisplayTable();
		displayPlot();
		statusService.clearStatus();
	}

	@Override
	public void updateAndDisplayTable() {
		if (table == null)
			setTable(new DefaultGenericTable(), "Horton-Strahler Table");
		IntStream.rangeClosed(1, maxOrder).forEach(order -> {
			table.appendRow();
			final int row = Math.max(0, table.getRowCount() - 1);
			table.set(getCol("Horton-Strahler #"), row, order);
			table.set(getCol("Rev. Horton-Strahler #"), row, maxOrder - order + 1);
			table.set(getCol("Length (Sum)"), row, tLengthMap.get(order));
			table.set(getCol("# Branches"), row, nBranchesMap.get(order));
			table.set(getCol("Bifurcation ratio"), row, bRatioMap.get(order));
			table.set(getCol("# Branch Points"), row, bPointsMap.get(order));
		});
		super.updateAndDisplayTable();
	}

	public void displayPlot() {

		final CategoryChart<Integer> chart = plotService.newCategoryChart(Integer.class);
		chart.categoryAxis().setLabel("Horton-Strahler order");
		chart.categoryAxis().setOrder(Comparator.reverseOrder());

		final LineSeries<Integer> series1 = chart.addLineSeries();
		series1.setLabel("No. of branches");
		series1.setValues(nBranchesMap);
		series1.setStyle(chart.newSeriesStyle(Colors.BLUE, LineStyle.SOLID, MarkerStyle.CIRCLE));

		final LineSeries<Integer> series2 = chart.addLineSeries();
		series2.setLabel("Length");
		series2.setValues(tLengthMap);
		series2.setStyle(chart.newSeriesStyle(Colors.RED, LineStyle.SOLID, MarkerStyle.CIRCLE));
		
		final LineSeries<Integer> series3 = chart.addLineSeries();
		series3.setLabel("Bifurcation ratio");
		series3.setValues(bRatioMap);
		series3.setStyle(chart.newSeriesStyle(Colors.BLACK, LineStyle.SOLID, MarkerStyle.CIRCLE));

		uiService.show("SNT: Strahler Plot", chart);
	}

	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final Tree tree = sntService.demoTree();
		final StrahlerCmd cmd = new StrahlerCmd(tree);
		cmd.setContext(ij.context());
		cmd.run();
	}
}
