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

package tracing.gui.cmds;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import net.imagej.ImageJ;
import tracing.SimpleNeuriteTracer;

/**
 * Implements the "Show Filtered Image" command.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, initializer = "init")
public class ShowFilteredImgCmd extends CommonDynamicCmd {

	@Parameter(type =ItemIO.OUTPUT)
	private ImagePlus imp;

	@SuppressWarnings("unused")
	private void init() {
		if (!sntService.isActive()) {
			error("SNT is not running.");
			return;
		}
		final SimpleNeuriteTracer plugin = sntService.getPlugin();
		if (!plugin.accessToValidImageData()) {
			error("Valid image data is required for displaying filtered image.");
			return;
		}
		if (!plugin.filteredImageLoaded()) {
			error("No filtered image has been loaded.");
			return;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		imp = sntService.getPlugin().getFilteredDataAsImp();
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(ShowFilteredImgCmd.class, true);
	}
}
