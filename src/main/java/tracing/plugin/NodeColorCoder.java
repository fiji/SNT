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

import java.awt.Color;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.Button;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import net.imglib2.display.ColorTable;
import tracing.Path;
import tracing.PathWindow;
import tracing.SNT;

/**
 * Command for color coding nodes according to their properties.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Node Color Coder", initializer = "init")
public class NodeColorCoder extends DynamicCommand {

	private static final String DEPTH = "Depth";
	private static final String RADIUS = "Radius";
	private static final String X_COORD = "X coordinate";
	private static final String Y_COORD = "Y coordinate";

	@Parameter
	private PrefService prefService;

	@Parameter
	private LUTService lutService;

	@Parameter(required = true, label = "Color by", choices = { DEPTH, RADIUS, X_COORD, Y_COORD })
	private String measurementChoice;

	@Parameter(label = "LUT", callback = "lutChoiceChanged")
	private String lutChoice;

	@Parameter(required = false, label = "<HTML>&nbsp;")
	private ColorTable colorTable;

	@Parameter(label = "Remove existing node coding", callback = "reset")
	private Button resetButton;

	@Parameter(required = true)
	private HashSet<Path> paths;

	@Parameter(required = false)
	private PathWindow manager;

	private double min = Double.MAX_VALUE;
	private double max = Double.MIN_VALUE;
	private Map<String, URL> luts;

	@Override
	public void run() {

		SNT.log("Color Coding Nodes (" + measurementChoice + ") using " + lutChoice);
		setMinMax();
		SNT.log(measurementChoice + " min: " + min);
		SNT.log(measurementChoice + " max: " + max);

		for (final Path p : paths) {
			Color[] colors = new Color[p.size()];
			for (int node = 0; node < p.size(); node++) {
				double value;
				switch (measurementChoice) {
				case DEPTH:
					value = p.getPointInImage(node).z;
					break;
				case X_COORD:
					value = p.getPointInImage(node).x;
					break;
				case Y_COORD:
					value = p.getPointInImage(node).y;
					break;
				case RADIUS:
					value = p.getNodeRadius(node);
					break;
				default:
					throw new IllegalArgumentException("Unknow parameter");
				}
				final int idx = (int) Math.round((colorTable.getLength() - 1) * (value - min) / (max - min));
				colors[node] = new Color(colorTable.get(ColorTable.RED, idx), colorTable.get(ColorTable.GREEN, idx),
						colorTable.get(ColorTable.BLUE, idx));
			}
			p.setNodeColors(colors);
		}

		SNT.log("Finished...");
		if (manager != null)
			manager.refresh();

	}

	protected void init() {
		if (lutChoice == null)
			lutChoice = prefService.get(getClass(), "lutChoice", "mpl-viridis.lut");
		setLUTs();
	}

	private void setLUTs() {
		luts = lutService.findLUTs();
		if (luts.isEmpty()) {
			cancel("This command requires at least one LUT to be installed.");
		}
		final ArrayList<String> choices = new ArrayList<>();
		for (final Map.Entry<String, URL> entry : luts.entrySet()) {
			choices.add(entry.getKey());
		}

		// define a valid LUT choice
		Collections.sort(choices);
		if (lutChoice == null || !choices.contains(lutChoice)) {
			lutChoice = choices.get(0);
		}

		final MutableModuleItem<String> input = getInfo().getMutableInput("lutChoice", String.class);
		input.setChoices(choices);
		input.setValue(this, lutChoice);
		lutChoiceChanged();
	}

	@SuppressWarnings("unused")
	private void reset() {
		for (final Path p : paths) p.setNodeColors(null);
		if (manager != null) manager.refresh();
	}

	private void lutChoiceChanged() {
		try {
			colorTable = lutService.loadLUT(luts.get(lutChoice));
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private void setMinMax() {
		switch (measurementChoice) {
		case DEPTH:
			for (final Path p : paths) {
				for (int node = 0; node < p.size(); node++) {
					setMinMax(p.getPointInImage(node).z);
				}
			}
			break;
		case X_COORD:
			for (final Path p : paths) {
				for (int node = 0; node < p.size(); node++) {
					setMinMax(p.getPointInImage(node).x);
				}
			}
			break;
		case Y_COORD:
			for (final Path p : paths) {
				for (int node = 0; node < p.size(); node++) {
					setMinMax(p.getPointInImage(node).y);
				}
			}
			break;
		case RADIUS:
			for (final Path p : paths) {
				if (!p.hasRadii()) continue;
				for (int node = 0; node < p.size(); node++) {
					setMinMax(p.getNodeRadius(node));
				}
			}
			break;
		default:
			throw new IllegalArgumentException("Unknow parameter");
		}
		if (max < min) setMinMax(0);
	}

	private void setMinMax(final double n) {
		if (n > max)
			max = n;
		else if (n < min)
			min = n;
	}

	/**
	 * IDE debug method
	 * 
	 * @throws IOException
	 **/
	public static void main(final String[] args) throws IOException {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input = new HashMap<>();
		input.put("paths", new HashSet<Path>());
		ij.command().run(NodeColorCoder.class, true, input);
	}

}
