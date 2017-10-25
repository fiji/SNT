/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2017 Fiji developers.
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

package tracing.plugin;

import java.awt.Checkbox;
import java.util.ArrayList;
import java.util.Vector;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import tracing.NeuriteTracerResultsDialog;
import tracing.Path;
import tracing.PathAndFillManager;
import tracing.SimpleNeuriteTracer;
import tracing.gui.GuiUtils;
import tracing.hyperpanes.MultiDThreePanes;

/**
 * Convenience class for converting SNT paths into ROIs.
 *
 * @author Tiago Ferreira
 */
public class RoiExporter {

	/** Flag describing SNT's XY view */
	public static final int XY_PLANE = MultiDThreePanes.XY_PLANE;
	/** Flag describing SNT's ZY view */
	public static final int ZY_PLANE = MultiDThreePanes.ZY_PLANE;
	/** Flag describing SNT's XZ view */
	public static final int XZ_PLANE = MultiDThreePanes.XZ_PLANE;

	private boolean useSWCcolors;
	private int exportPlane = MultiDThreePanes.XY_PLANE;

	/**
	 * Runs SNT's ROI exporter dialog.
	 *
	 * @param plugin the UI running instance of SNT
	 */
	public void runGui(final SimpleNeuriteTracer plugin) {

		final NeuriteTracerResultsDialog pluginUI = plugin.getUI();
		if (pluginUI == null) {
			throw new IllegalArgumentException("UI method called but SNTs UI null");
		}
		final PathAndFillManager pathAndFillManager = plugin
			.getPathAndFillManager();
		final GuiUtils gUtils = new GuiUtils(plugin.getUI());
		if (!pathAndFillManager.anySelected()) {
			gUtils.error("No paths selected.");
			return;
		}

		final GenericDialog gd = new GenericDialog("Selected Paths to ROIs");

		final int[] PLANES_ID = { XY_PLANE, XZ_PLANE, ZY_PLANE };
		final String[] PLANES_STRING = { "XY_View", "XZ_View", "ZY_View" };

		final boolean[] destinationPlanes = new boolean[PLANES_ID.length];
		for (int i = 0; i < PLANES_ID.length; i++)
			destinationPlanes[i] = pluginUI.getImagePlus(PLANES_ID[i]) != null;

		gd.setInsets(0, 10, 0);
		gd.addMessage("Create 2D Path-ROIs from:");
		gd.setInsets(0, 20, 0);
		for (int i = 0; i < PLANES_ID.length; i++)
			gd.addCheckbox(PLANES_STRING[i], destinationPlanes[i]);

		// 2D traces?
		final Vector<?> cbxs = gd.getCheckboxes();
		for (int i = 1; i < PLANES_ID.length; i++)
			((Checkbox) cbxs.get(i)).setEnabled(!plugin.is2D());

		final String[] scopes = { "ROI Manager", "Image overlay" };
		gd.addRadioButtonGroup("Store Path-ROIs in:", scopes, 2, 1, scopes[0]);

		gd.addMessage("");
		gd.addCheckbox("Color code ROIs by SWC type", false);
		gd.addCheckbox("Discard pre-existing ROIs in Overlay/Manager", true);

		gd.showDialog();
		if (gd.wasCanceled()) return;

		for (int i = 0; i < PLANES_ID.length; i++)
			destinationPlanes[i] = gd.getNextBoolean();

		final String scope = gd.getNextRadioButton();
		useSWCcolors(gd.getNextBoolean());
		final boolean reset = gd.getNextBoolean();

		if (scopes[0].equals(scope)) { // ROI Manager

			final Overlay overlay = new Overlay();
			for (int i = 0; i < destinationPlanes.length; i++) {
				if (destinationPlanes[i]) {
					final int lastPlaneIdx = overlay.size() - 1;
					export(pathAndFillManager, overlay, PLANES_ID[i]);
					if (plugin.is2D()) continue;
					for (int j = lastPlaneIdx + 1; j < overlay.size(); j++) {
						final Roi roi = overlay.get(j);
						roi.setName(roi.getName() + " [" + PLANES_STRING[i] + "]");
					}
				}
			}
			RoiManager rm = RoiManager.getInstance2();
			if (rm == null) rm = new RoiManager();
			else if (reset) rm.reset();
			Prefs.showAllSliceOnly = !plugin.is2D();
			rm.setEditMode(plugin.getImagePlus(), false);
			for (final Roi path : overlay.toArray())
				rm.addRoi(path);
			rm.runCommand("sort");
			rm.setEditMode(plugin.getImagePlus(), true);
			rm.runCommand("show all without labels");

		}
		else { // Overlay

			String error = "";
			for (int i = 0; i < destinationPlanes.length; i++) {
				if (destinationPlanes[i]) {
					final ImagePlus imp = pluginUI.getImagePlus(PLANES_ID[i]);
					if (imp == null) {
						error += PLANES_STRING[i] + ", ";
						continue;
					}
					Overlay overlay = imp.getOverlay();
					if (overlay == null) {
						overlay = new Overlay();
						imp.setOverlay(overlay);
					}
					else if (reset) overlay.clear();
					export(pathAndFillManager, overlay, PLANES_ID[i]);
				}
			}
			if (!error.isEmpty()) {
				gUtils.error("Some ROIs were skipped because some images (" //
					+ error.substring(0, error.length() - 2)//
					+ ") are not being displayed. Please generate them and " +
					"re-run the export (orf export to the ROI Manager instead).");
			}
		}
	}

	private void export(final PathAndFillManager pathAndFillManager,
		final Overlay overlay, final int plane)
	{
		setView(plane);
		export(pathAndFillManager, true, overlay);
	}

	private boolean exportablePath(final Path path) {
		return path != null && !path.isFittedVersionOfAnotherPath();
	}

	/**
	 * Converts paths associated with a PathAndFillManager instance.
	 *
	 * @param pathAndFillManager a valid pathAndFillManager instance
	 * @param onlySelected if true only selected paths are converted into ROI
	 * @param overlay the target overlay holding the converted paths
	 */
	public void export(final PathAndFillManager pathAndFillManager,
		final boolean onlySelected, final Overlay overlay)
	{
		final ArrayList<Path> paths = new ArrayList<>();
		for (int i = 0; i < pathAndFillManager.size(); ++i) {
			final Path p = pathAndFillManager.getPath(i);
			if (!exportablePath(p)) continue;
			if (onlySelected && !pathAndFillManager.isSelected(p)) continue;
			paths.add(p);
		}
		export(paths, overlay);
	}

	/**
	 * Converts a list of paths.
	 *
	 * @param paths the paths being converted.
	 * @param overlay the target overlay holding the converted paths
	 */
	public void export(final ArrayList<Path> paths, Overlay overlay) {
		if (overlay == null) overlay = new Overlay();
		for (final Path p : paths) {
			if (!exportablePath(p)) continue;
			// If path suggests using fitted version, draw that instead
			final Path drawPath = (p.getUseFitted()) ? p.getFitted() : p;
			if (useSWCcolors) drawPath.setColorBySWCtype();
			drawPath.drawPathAsPoints(overlay, exportPlane);
		}
	}

	/**
	 * Sets the exporting view (XY by default).
	 *
	 * @param view either {@link XY_PLANE}, {@link XZ_PLANE} or {@link ZY_PLANE}.
	 */
	public void setView(final int view) {
		if (view != XY_PLANE && view != ZY_PLANE && view != XZ_PLANE)
			throw new IllegalArgumentException(
				"plane is not a valid MultiDThreePanes flag");
		this.exportPlane = view;
	}

	/**
	 * Specifies coloring of ROIs by SWC type.
	 *
	 * @param useSWCcolors if true exported paths are colored according to their
	 *          SWC type integer flag.
	 */
	public void useSWCcolors(final boolean useSWCcolors) {
		this.useSWCcolors = useSWCcolors;
	}

}
