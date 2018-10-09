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

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;

import net.imagej.ImageJ;
import tracing.SNTService;
import tracing.Tree;
import tracing.gui.GuiUtils;
import tracing.plot.TreePlot3D;

/**
 * Command for opening two SWC/traces files in a dedicated Reconstruction Viewer.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Compare Two Reconstructions")
public class CompareFilesCmd extends ContextCommand {

	@Parameter
	private SNTService sntService;

	@Parameter(label = "File 1")
	private File file1;

	@Parameter(label = "Color")
	private ColorRGB color1;

	@Parameter(label = "File 2")
	private File file2;

	@Parameter(label = "Color")
	private ColorRGB color2;

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			final Tree tree1 = new Tree(file1.getAbsolutePath());
			tree1.setColor(color1);
			final Tree tree2 = new Tree(file2.getAbsolutePath());
			tree1.setColor(color2);
			final TreePlot3D plot3d = new TreePlot3D();
			plot3d.add(tree1);
			plot3d.add(tree2);
			plot3d.show().setTitle(file1.getName() + " vs " + file2.getName());
		} catch (final IllegalArgumentException ex) {
			cancel("An error occured: " + ex.getMessage());
		}
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(CompareFilesCmd.class, true);
	}

}