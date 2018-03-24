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

import java.awt.Dimension;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;
import org.scijava.widget.Button;
import org.scijava.widget.NumberWidget;

import net.imagej.ImageJ;
import tracing.Path;
import tracing.Tree;
import tracing.analysis.TreePlot;

/**
 * Command for interactively plotting trees using {@link TreePlot}
 *
 * @author Tiago Ferreira
 */
@Plugin(type = DynamicCommand.class, visible = false, label = "Interactive Traces Plot")
public class PlotterCmd extends DynamicCommand implements Interactive {

	@Parameter(required = false, label = "Angle (degrees)", min = "0", max = "360", style = NumberWidget.SCROLL_BAR_STYLE, callback = "updatePlot")
	private int angle = 0;

	@Parameter(required = false, label = "Rotation axis", choices = { Z_AXIS, Y_AXIS, X_AXIS }, callback = "updatePlot")
	private String rotationAxis = Y_AXIS;

	@Parameter(label = "Snapshot color")
	private ColorRGB snapshotColor = DEF_COLOR;

	@Parameter(label = "Reset Rotation", callback = "resetRotation")
	private Button reset;

	@Parameter(label = "Take Snapshot", callback = "snapshot")
	private Button snapshot;

	@Parameter(required = true)
	private Tree tree;

	@Parameter(required = false)
	private String title;

	private static final String X_AXIS = "X";
	private static final String Y_AXIS = "Y";
	private static final String Z_AXIS = "Z";
	private static final ColorRGB DEF_COLOR = new ColorRGB("black");
	private TreePlot plot;
	private JFreeChart chart;
	private ChartFrame frame;
	private Tree plottingTree;

	@Override
	public void run() {
		// we don't want to be overriding input paths, so we'll just copy it
		plottingTree = tree.duplicate();
		resetRotation();
		buildPlot(DEF_COLOR);
		chart = plot.getChart();
		frame = new ChartFrame(title, chart);
		frame.setPreferredSize(new Dimension(600, 450));
		frame.pack();
		frame.setVisible(true);
	}

	void buildPlot(ColorRGB color) {
		plot = new TreePlot(context());
		plot.setDefaultColor(color);
		if (angle > 0)
			plottingTree.rotate(getRotationAxis(rotationAxis), angle);
		plot.addTree(plottingTree);
	}

	private void updatePlot() {
		buildPlot(DEF_COLOR);
		chart.getXYPlot().setDataset(plot.getChart().getXYPlot().getDataset());
		frame.toFront();
	}

	private void resetRotation() {
		angle = 0;
		rotationAxis = Y_AXIS;
		if (plot != null)
			updatePlot();
	}

	@SuppressWarnings("unused")
	private void snapshot() {
		buildPlot(snapshotColor);
		plot.setTitle("Snapshot: " + rotationAxis + angle + "deg");
		plot.showPlot();
	}

	private int getRotationAxis(final String string) {
		switch (string) {
		case Y_AXIS:
			return Tree.Y_AXIS;
		case Z_AXIS:
			return Tree.Z_AXIS;
		default:
			return Tree.X_AXIS;
		}
	}

	/** IDE debug method **/
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input = new HashMap<>();
		final Tree tree = new Tree(new HashSet<Path>(DistributionCmd.randomPaths()));
		input.put("tree", tree);
		ij.command().run(PlotterCmd.class, true, input);
	}

}
