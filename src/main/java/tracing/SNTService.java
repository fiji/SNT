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

package tracing;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Set;

import org.scijava.Priority;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

import ij.ImagePlus;
import ij.plugin.ZProjector;
import ij.process.ColorProcessor;
import net.imagej.ImageJService;
import net.imagej.table.DefaultGenericTable;
import tracing.analysis.TreeAnalyzer;
import tracing.analysis.TreeStatistics;
import tracing.hyperpanes.MultiDThreePanes;

/**
 * Service for accessing and scripting the active instance of
 * {@link SimpleNeuriteTracer}.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Service.class, priority = Priority.NORMAL)
public class SNTService extends AbstractService implements ImageJService {

	@Parameter
	private ScriptService scriptService;

	@Parameter
	private LogService logService;

	private static SimpleNeuriteTracer plugin;

	private void accessActiveInstance() {
		plugin = SNT.getPluginInstance();
		if (plugin == null)
			throw new UnsupportedOperationException("SNT does not seem to be running");
	}

	// @Override
	// public void initialize() {
	// scriptService.addAlias(this.getClass());
	// }

	/**
	 * Gets whether SimpleNeuriteTracer is running.
	 *
	 * @return true if this {@code SNTService} is active, tied to the active
	 *         instance of SimpleNeuriteTracer
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public boolean isActive() {
		plugin = SNT.getPluginInstance();
		return plugin != null;
	}

	/**
	 * Returns a reference to the active {@link SimpleNeuriteTracer} plugin.
	 *
	 * @return the {@link SimpleNeuriteTracer} instance
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public SimpleNeuriteTracer getPlugin() {
		accessActiveInstance();
		return plugin;
	}

	/**
	 * Gets the paths currently selected in the Path Manager list.
	 *
	 * @return the paths currently selected, or null if no selection exists
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public Set<Path> getSelectedPaths() {
		accessActiveInstance();
		return plugin.getSelectedPaths();
	}

	/**
	 * Gets the paths currently listed in the Path Manager
	 *
	 * @return all the listed paths, or null if the Path Manager is empty
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public ArrayList<Path> getPaths() {
		accessActiveInstance();
		return plugin.getPathAndFillManager().getPathsFiltered();
	}

	/**
	 * Gets the collection of paths listed in the Path Manager as a {@link Tree}
	 * object.
	 *
	 * @param selectedPathsOnly
	 *            If true, only selected paths are retrieved
	 * @return the Tree holding the Path collection
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public Tree getTree(final boolean selectedPathsOnly) {
		if (selectedPathsOnly)
			return new Tree(getSelectedPaths());
		return new Tree(getPaths());
	}

	/**
	 * Returns a {@link TreeAnalyzer} instance constructed from current Paths.
	 *
	 * @param selectedPathsOnly
	 *            If true only selected paths will be considered
	 * @return the TreeAnalyzer instance
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public TreeAnalyzer getAnalyzer(final boolean selectedPathsOnly) {
		return new TreeAnalyzer(getTree(selectedPathsOnly));
	}

	/**
	 * Returns a {@link TreeStatistics} instance constructed from current Paths.
	 *
	 * @param selectedPathsOnly
	 *            If true only selected paths will be considered
	 * @return the TreeStatistics instance
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public TreeStatistics getStatistics(final boolean selectedPathsOnly) {
		return new TreeStatistics(getTree(selectedPathsOnly));
	}

	/**
	 * Returns the {@link PathAndFillManager} associated with the current plugin
	 * instance
	 *
	 * @return the PathAndFillManager instance
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public PathAndFillManager getPathAndFillManager() {
		accessActiveInstance();
		return plugin.getPathAndFillManager();
	}

	/**
	 * Updates (refreshes) all viewers currently in use by the plugin
	 */
	public void updateViewers() {
		accessActiveInstance();
		plugin.updateAllViewers();
	}

	/**
	 * Returns a reference to SNT's UI.
	 *
	 * @return the {@link SNTUI} window
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public SNTUI getUI() {
		accessActiveInstance();
		return plugin.getUI();
	}

	/**
	 * Assesses whether the UI is blocked.
	 *
	 * @return true if the UI is currently unblocked, i.e., ready for
	 *         tracing/editing/analysis
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 */
	public boolean isUIReady() {
		final SNTUI gui = getUI();
		final int state = gui.getState();
		return plugin.isUIready() && (state == SNTUI.WAITING_TO_START_PATH
				|| state == SNTUI.EDITING_MODE
				|| state == SNTUI.ANALYSIS_MODE);
	}

	/**
	 * Returns a reference to SNT's main table of measurements.
	 * 
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 * @return the table
	 */
	public DefaultGenericTable getTable() {
		return getUI().getPathManager().getTable();
	}

	/**
	 * Retrieves a WYSIWYG 'snapshot' of a tracing canvas.
	 *
	 * @param view
	 *            A case-insensitive string specifying the canvas to be captured.
	 *            Either "xy" (or "main"), "xz", "zy" or "3d" (for legacy's 3D
	 *            Viewer).
	 * @throws UnsupportedOperationException
	 *             if SimpleNeuriteTracer is not running
	 * @throws IllegalArgumentException
	 *             if view is not a recognized option
	 * @return the snapshot capture of the canvas as an RGB image
	 */
	public ImagePlus captureView(final String view) {
		accessActiveInstance();
		if (view == null || view.trim().isEmpty())
			throw new IllegalArgumentException("Invalid view");

		if (view.toLowerCase().contains("3d")) {
			if (plugin.get3DUniverse() == null)
				throw new IllegalArgumentException("view is not available");
			return plugin.get3DUniverse().takeSnapshot();
		}

		final int viewPlane = getView(view);
		final ImagePlus imp = plugin.getUI().getImagePlus(viewPlane);
		if (imp == null)
			throw new IllegalArgumentException("view is not available");

		final ImagePlus proj = ZProjector.run(imp, "max").flatten(); // overlay will be flatten but not active ROI
		final TracerCanvas canvas = new TracerCanvas(proj, plugin, viewPlane, plugin.getPathAndFillManager());
		final BufferedImage bi = new BufferedImage(proj.getWidth(), proj.getHeight(), BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = canvas.getGraphics2D(bi.getGraphics());
		g.drawImage(proj.getImage(), 0, 0, null);
		for (final Path p : getPaths()) {
			p.drawPathAsPoints(g, canvas, plugin);
		}
		// this is taken from ImagePlus.flatten()
		final ImagePlus result = new ImagePlus(view + " view snapshot", new ColorProcessor(bi));
		result.copyScale(proj);
		result.setProperty("Info", proj.getProperty("Info"));
		return result;
	}

	private int getView(final String view) {
		switch (view.toLowerCase()) {
		case "xy":
		case "main":
			return MultiDThreePanes.XY_PLANE;
		case "xz":
			return MultiDThreePanes.XZ_PLANE;
		case "zy":
			return MultiDThreePanes.ZY_PLANE;
		default:
			throw new IllegalArgumentException("Unrecognized view");
		}
	}

}
