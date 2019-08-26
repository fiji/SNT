package sc.fiji.snt.gui.cmds;

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

import net.imagej.ImageJ;

import net.imagej.updater.UpdateService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.FileNotFoundException;

/**
 * Implements the 'EnableSciViewUpdateSite' command.
 *
 * @author Kyle Harrington
 */
@Plugin(type = Command.class, visible = false)
public class EnableSciViewUpdateSiteCmd implements Command {

    @Parameter
    private LogService logService;

	@Parameter
	private UpdateService updateService;

	@Override
	public void run() {
	    try {
	    	// Note that this call emits (but doesnt throw) a FileNotFoundException when run from an IDE
            updateService.getUpdateSite("SciView").setActive(true);
        } catch( Exception e ) {
	        logService.warn("UpdateService is unavailable. This should not occur if you run SNT via ImageJ, but may occur if you are using a development IDE.");
        }
	}

	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(EnableSciViewUpdateSiteCmd.class, true);
	}

}

