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

import java.io.IOException;

import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import net.imagej.ImageJ;
import sc.fiji.snt.SNTUtils;

/**
 * Command for Launching SNT when user selects 'Simple Neurite Tracer' from Ij's menu hierarchy.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = true, menuPath = "Plugins>NeuroAnatomy>Legacy>Simple Neurite Tracer...")
public class SNTLegacyLoaderCmd extends ContextCommand {

	@Parameter
	private CommandService cmdService;

	@Parameter
	private UIService uiService;

	@Override
	public void run() {
		uiService.showDialog("\"Plugins>NeuroAnatomy>SNT...\" is the preferred way to run SNT.");
		cmdService.run(SNTLoaderCmd.class, true);
	}

	/*
	 * IDE debug method
	 *
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		SNTUtils.setDebugMode(true);
		ij.command().run(SNTLegacyLoaderCmd.class, true);
	}

}
