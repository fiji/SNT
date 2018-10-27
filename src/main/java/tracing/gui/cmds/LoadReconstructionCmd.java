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

package tracing.gui.cmds;

import java.io.File;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;

import net.imagej.ImageJ;
import tracing.SNTService;
import tracing.Tree;
import tracing.plot.TreePlot3D;

/**
 * Command for loading Reconstruction files in Reconstruction Viewer. Loaded
 * paths are not listed in the Path Manager.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, initializer = "init", label = "Load Reconstruction...")
public class LoadReconstructionCmd extends DynamicCommand {

	@Parameter(label = "File", required = true, style = "extensions:swc/eswc/traces", description = "Supported extensions: .traces or .(e)SWC")
	private File file;

	@Parameter(label = "Color", required = false, description = "Rendering Color of imported file")
	private ColorRGB color;

	@Parameter(persist = false, visibility = ItemVisibility.MESSAGE)
	private String msg;

	@Parameter(required = false)
	private TreePlot3D recViewer;

	@Parameter(required = false)
	private SNTService snt;

	@SuppressWarnings("unused")
	private void init() {
		final MutableModuleItem<String> mItem = getInfo().getMutableInput("msg", String.class);
		if (recViewer != null && recViewer.isSNTInstance()) {
			msg = "NB: Loaded file will not be listed in Path Manager";
		} else {
			msg = " ";
		}
	}

	/*
	 * 
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if (recViewer == null) {
			cancel("Reconstruction Viewer not specified");
		}
		try {
			final Tree tree = new Tree(file.getAbsolutePath());
			tree.setColor(color);
			if (tree.isEmpty())
				cancel("No Paths could be extracted. Invalid file?");
			recViewer.add(tree);
			recViewer.validate();
		} catch (final IllegalArgumentException ex) {
			cancel("An error occured: " + ex.getMessage());
		}
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(LoadReconstructionCmd.class, true);
	}
}