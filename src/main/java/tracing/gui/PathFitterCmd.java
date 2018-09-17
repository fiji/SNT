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

package tracing.gui;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.Button;

import net.imagej.ImageJ;
import tracing.PathFitter;

/**
 * Command with the sole purpose of providing a scijava-based GUI for
 * {@link tracing.PathFitter}
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Fit Paths")
public class PathFitterCmd extends ContextCommand {

	@Parameter
	private PrefService prefService;

	public static final String FITCHOICE_KEY= "choice";
	public static final String MAXRADIUS_KEY= "maxrad";

	private static final String EMPTY_LABEL = "<html>&nbsp;";
	private static final String HEADER = "<html><body><div style='width:500;'>";
	private static final String CHOICE_MIDPOINT = "Midpoint refinement";
	private static final String CHOICE_RADII = "Radii";
	private static final String CHOICE_BOTH = "Midpoint refinement and radii";

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE)
	private String msg1 = HEADER
			+ "<b>Type of fit:</b> Please choose the type of fit to be performed. The algorithm will fit "
			+ "circular cross-sections around the signal of existing nodes to "
			+ "compute radii (node thickness) and midpoint refinement of existing node coordinates:";

	@Parameter(required = true, label = EMPTY_LABEL, choices = { CHOICE_BOTH, CHOICE_MIDPOINT, CHOICE_RADII })
	private String fitChoice;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE)
	private String msg2 = HEADER
			+ "<b>Max. radius:</b> You can also specify (in pixels) the maximum radius to be considered:";

	@Parameter(required = false, label = EMPTY_LABEL)
	private int maxRadius = 40;

	@Parameter(required = false, label = "Reset Defaults", callback = "reset")
	private Button reset;

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		switch (fitChoice) {
		case CHOICE_MIDPOINT:
			prefService.put(PathFitterCmd.class, FITCHOICE_KEY, PathFitter.MIDPOINTS);
			break;
		case CHOICE_RADII:
			prefService.put(PathFitterCmd.class, FITCHOICE_KEY, PathFitter.RADII);
			break;
		default:
			prefService.put(PathFitterCmd.class, FITCHOICE_KEY, PathFitter.RADII_AND_MIDPOINTS);
			break;
		}
		prefService.put(PathFitterCmd.class, MAXRADIUS_KEY, maxRadius);
	}

	@SuppressWarnings("unused")
	private void reset() {
		fitChoice = PathFitterCmd.CHOICE_BOTH;
		maxRadius = PathFitter.DEFAULT_MAX_RADIUS;
		prefService.clear(PathFitterCmd.class); // useful if user dismisses dialog after pressing "Reset"
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(PathFitterCmd.class, true);
	}

}
