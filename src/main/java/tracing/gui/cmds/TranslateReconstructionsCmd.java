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

import java.util.List;

import net.imagej.ImageJ;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import tracing.SNT;
import tracing.Tree;
import tracing.gui.GuiUtils;
import tracing.plot.TreePlot3D;
import tracing.util.PointInImage;

/**
 * Implements Reconstruction Viewer's 'Translate...' command.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false,
	label = "Translate Reconstruction(s)", initializer = "init")
public class TranslateReconstructionsCmd extends CommonDynamicCmd {

	@Parameter(required = false, label = "X",
		description = "X offset in physical units")
	private double x;

	@Parameter(required = false, label = "Y",
		description = "Y offset in physical units")
	private double y;

	@Parameter(required = false, label = "Z",
		description = "Z offset in physical units")
	private double z;

	@Parameter(persist = false, visibility = ItemVisibility.MESSAGE)
	private String msg;

	@Parameter(required = false)
	private TreePlot3D recViewer;

	@Parameter(required = false)
	private List<String> treeLabels;

	@SuppressWarnings("unused")
	private void init() {
		if (recViewer != null && recViewer.isSNTInstance()) {
			msg = "NB: Path Manager paths will remain unchanged";
		}
		else {
			msg = " ";
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		try {
			if (recViewer == null) {
				recViewer = sntService.getReconstructionViewer();
			}
			if (treeLabels == null) {
				error("No reconstructions were specified");
				return;
			}
			recViewer.translate(treeLabels, new PointInImage(x, y, z));
		}
		catch (final UnsupportedOperationException | NullPointerException exc) {
			error("SNT's Reconstruction Viewer is not available");
		}

	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
		SNT.setDebugMode(true);
		final TreePlot3D jzy3D = new TreePlot3D(ij.context());
		jzy3D.add(tree);
		jzy3D.loadMouseRefBrain();
		jzy3D.show();
	}

}
