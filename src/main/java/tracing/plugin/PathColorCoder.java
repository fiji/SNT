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
import java.util.List;
import java.util.Map;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import net.imglib2.display.ColorTable;
import tracing.Path;
import tracing.PathWindow;
import tracing.SNT;

/**
 * Command for color coding paths according to their properties.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Path Color Coder", initializer = "init")
public class PathColorCoder extends DynamicCommand {

	@Parameter
	private PrefService prefService;

	@Parameter
	private LUTService lutService;

	@Parameter(required = true, label = "Color by", choices = { PathAnalyzer.BRANCH_ORDER, PathAnalyzer.LENGTH,
			PathAnalyzer.N_BRANCH_POINTS, PathAnalyzer.N_NODES, PathAnalyzer.MEAN_RADIUS})
	private String measurementChoice;

	@Parameter(label = "LUT", callback = "lutChoiceChanged")
	private String lutChoice;

	@Parameter(required = false, label = "<HTML>&nbsp;")
	private ColorTable colorTable;

	@Parameter(required = true)
	private HashSet<Path> paths;

	@Parameter(required = false)
	private PathWindow manager;

	private double min = Double.MAX_VALUE;
	private double max = Double.MIN_VALUE;
	private Map<String, URL> luts;


	@Override
	public void run() {

		final List<MappedPath> mappedPaths = new ArrayList<>();
		switch (measurementChoice) {
		case PathAnalyzer.BRANCH_ORDER:
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.getOrder()));
			break;
		case PathAnalyzer.LENGTH:
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, p.getRealLength()));
			break;
		case PathAnalyzer.MEAN_RADIUS:
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, p.getMeanRadius()));
			break;
		case PathAnalyzer.N_NODES:
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.size()));
			break;
		case PathAnalyzer.N_BRANCH_POINTS:
			for (final Path p : paths)
				mappedPaths.add(new MappedPath(p, (double) p.findJoinedPoints().size()));
			break;
		default:
			throw new IllegalArgumentException("Unknown parameter");
		}

		SNT.log("Color Coding Paths ("+ measurementChoice + ") using "+ lutChoice);
		SNT.log(measurementChoice + " min: "+ min);
		SNT.log(measurementChoice + " max: "+ max);

		for (final MappedPath mp : mappedPaths) {
			final int idx = (int) Math.round((colorTable.getLength() - 1) * (mp.mappedValue - min) / (max - min));
			final Color color = new Color(colorTable.get(ColorTable.RED, idx), colorTable.get(ColorTable.GREEN, idx),
					colorTable.get(ColorTable.BLUE, idx));
			mp.path.setColor(color);
		}
		SNT.log("Finished...");
		if (manager != null) manager.refresh();

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

	private void lutChoiceChanged() {
		try {
			colorTable = lutService.loadLUT(luts.get(lutChoice));
		} catch (final IOException e) {
			e.printStackTrace();
		}
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
		ij.command().run(PathColorCoder.class, true, input);
	}

}
