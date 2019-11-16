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

package sc.fiji.snt.gui.cmds;


import java.util.HashMap;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;
import org.scijava.util.ColorRGBA;
import org.scijava.widget.NumberWidget;

import net.imagej.ImageJ;
import sc.fiji.snt.gui.GuiUtils;

/**
 * Command for customizing a Tree in Reconstruction Viewer
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Customize Reconstruction(s)...")
public class CustomizeTreeCmd extends ContextCommand {

	@Parameter(label = "<HTML><b>Processes:", persist = false, visibility = ItemVisibility.MESSAGE)
	private String msg1 = "";

	@Parameter(label = "Color", required = false)
	private ColorRGB tColor;

	@Parameter(label = "Transparency (%)", min = "0.5", max = "100", style = NumberWidget.SCROLL_BAR_STYLE)
	private double tTransparency;

	@Parameter(label = "Thickness", min = "1", max = "8",  stepSize = "1",
			description = "Arbitrary units. 1: Thinnest; 8: Thickest")
	private double tSize;

	@Parameter(label = "<HTML><b>Soma:", persist = false, visibility = ItemVisibility.MESSAGE)
	private String msg2 = "";

	@Parameter(label = "Color", required = false)
	private ColorRGB sColor;

	@Parameter(label = "Transparency (%)", min = "0.5", max = "100", style = NumberWidget.SCROLL_BAR_STYLE)
	private double sTransparency;

	@Parameter(label = "Radius", min = "0",
			description = "Spatially calibrated units. Applies only if soma is being rendered as a sphere")
	private double sSize;

	// this should really be @Parameter(type = ItemIO.OUTPUT), but this
	// Suppresses the annoying "ignoring unsupported output message
	@Parameter(required = false)
	private HashMap<String, ColorRGBA> colorMap;
	@Parameter(required = false)
	private HashMap<String, Double> sizeMap;

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if (sTransparency >= 100 || tTransparency >= 100) {
			cancel("Surface color cannot be fully transparent.");
		}
		colorMap = new HashMap<>();
		colorMap.put("soma", (sColor == null) ? null : new ColorRGBA(sColor.getRed(), sColor.getGreen(), sColor.getBlue(), getAlpha(sTransparency)));
		colorMap.put("tree", (tColor == null) ? null : new ColorRGBA(tColor.getRed(), tColor.getGreen(), tColor.getBlue(), getAlpha(tTransparency)));
		sizeMap = new HashMap<>();
		sizeMap.put("soma", sSize);
		sizeMap.put("tree", tSize);
	}

	private int getAlpha( final double transparency) {
		int value = 255 - (int) Math.round(transparency * 255 / 100);
		if (value < 0) value = 0;
		if (value > 255) value = 255;
		return value;
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(CustomizeTreeCmd.class, true);
	}

}
