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
import java.util.Collections;
import java.util.List;

import net.imagej.ImageJ;
import net.imagej.plot.LineStyle;
import net.imagej.plot.MarkerStyle;
import net.imagej.plot.PlotService;
import net.imagej.plot.XYPlot;
import net.imagej.plot.XYSeries;

import org.scijava.app.StatusService;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import tracing.Path;
import tracing.Tree;
import tracing.util.PointInImage;
import tracing.util.SWCColor;

/**
 * Command to retrieve Profile plots from Paths.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = ContextCommand.class, visible = false)
public class PathProfiler extends ContextCommand {

	@Parameter
	private PlotService plotService;

	@Parameter
	private UIService uiService;

	@Parameter
	private StatusService statusService;

	private final Tree tree;
	private final ImageStack stack;
	private boolean valuesAssignedToTree;
	private boolean setLegend;

	public PathProfiler(final Tree tree, final ImagePlus imp) {
		if (imp == null || tree == null) throw new IllegalArgumentException("Null image");
		this.tree = tree;
		this.stack = imp.getImageStack();
	}

	public PathProfiler(final Path path, final ImagePlus imp) {
		this(new Tree(Collections.singleton(path)), imp);
	}

	@Override
	public void run() {
		if (tree.size() == 0) {
			cancel("<HTML>No path(s) to profile");
			return;
		}
		statusService.showStatus("Measuring Paths...");
		String title = 	"Path Profile";
		if (tree.getLabel() != null) title += " " + tree.getLabel();
		uiService.show(title, getPlot());
		statusService.clearStatus();
	}

	public void assignValues() {
		for (final Path p : tree.list())
			assignValues(p);
		valuesAssignedToTree = true;
	}

	public void assignValues(final Path p) {
		final double[] values = new double[p.size()];
		for (int i = 0; i < p.size(); i++) {
			try {
				values[i] = stack.getVoxel(p.getXUnscaled(i), p.getYUnscaled(i), p
					.getZUnscaled(i) + 1);
			}
			catch (final IndexOutOfBoundsException exc) {
				values[i] = Double.NaN;
			}
			p.setValues(values);
		}
	}

	private XYSeries addSeries(final XYPlot plot, final Path p,
		final ColorRGB color)
	{
		final XYSeries series = plot.addXYSeries();
		final List<Double> xList = new ArrayList<>(p.size());
		final List<Double> yList = new ArrayList<>(p.size());
		final PointInImage previous = p.getPointInImage(0);
		double dx = 0;
		for (int i = 0; i < p.size(); i++) {
			final PointInImage node = p.getPointInImage(i);
			dx += node.distanceTo(previous);
			xList.add(dx);
			yList.add(p.getValue(i));
		}
		series.setLabel(p.getName());
		series.setStyle(plot.newSeriesStyle(color, LineStyle.SOLID,
			MarkerStyle.CIRCLE));
		series.setValues(xList, yList);
		series.setLegendVisible(setLegend);
		return series;
	}

	public XYPlot getPlot() {
		if (!valuesAssignedToTree) assignValues();
		final XYPlot plot = plotService.newXYPlot();
		setLegend = tree.size() > 1;
		final ColorRGB[] colors;
		if (treeIsColorMapped()) {
			colors = new ColorRGB[tree.size()];
			for (int i = 0; i < tree.size(); i++) {
				colors[i] = tree.get(i).getColorRGB();
				if (colors[i] == null) colors[i] = Colors.BLACK;
			}
		}
		else {
			colors = SWCColor.getDistinctColors(tree.size());
		}
		int colorIdx = 0;
		for (final Path p : tree.list()) {
			addSeries(plot, p, colors[colorIdx++]);
		}
		plot.xAxis().setLabel("Distance");
		plot.yAxis().setLabel("Intensity (" + stack.getBitDepth() + "-bit)");
		return plot;
	}

	private boolean treeIsColorMapped() {
		final ColorRGB refColor = tree.get(0).getColorRGB();
		for (final Path path : tree.list()) {
			if (path.hasNodeColors()) return true;
			if (refColor != null && !refColor.equals(path.getColorRGB())) return true;
		}
		return false;
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Tree tree = new Tree("/home/tferr/code/OP_1/OP_1.traces");
		final ImagePlus imp = IJ.openImage("/home/tferr/code/OP_1/OP_1.tif");
		final PathProfiler profiler = new PathProfiler(tree, imp);
		profiler.setContext(ij.context());
		profiler.run();
	}
}
