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

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import tracing.SimpleNeuriteTracer;
import tracing.gui.GuiUtils;

/**
 * Implements the "Show Hessian ('Tubeness') Image" command.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, initializer = "init")
public class TubenessCmd extends CommonDynamicCmd {

	@Parameter
	private OpService ops;

	private double sigma;

	@SuppressWarnings("unused")
	private void init() {
		if (!sntService.isActive()) {
			error("SNT is not running.");
			return;
		}
		final SimpleNeuriteTracer plugin = sntService.getPlugin();
		if (!plugin.accessToValidImageData()) {
			error("Valid image data is required for displaying \"Tubeness\" image.");
			return;
		}
		if (!plugin.isAstarEnabled() || !plugin.isHessianEnabled()) {
			error("Auto-tracing and Hessian analysis must be enabled for displaying \"Tubeness\" image.");
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
		final ImagePlus inputImp = sntService.getPlugin().getImagePlus();
		sigma = sntService.getUI().getSigma();
		final Img<FloatType> in = ImageJFunctions.convertFloat(inputImp);
		final Img<DoubleType> out = ops.create().img(in, new DoubleType());
		ops.filter().tubeness(out, in, sigma);
		ImageJFunctions.show(out, String.format("Tubeness: Sigma=%.1f%s", sigma, inputImp.getCalibration().getUnit()));
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(TubenessCmd.class, true);
	}
}
