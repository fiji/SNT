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

package tracing;

import java.awt.Font;
import java.awt.Point;

import tracing.FillWindow;
import ij.Prefs;
import ij.gui.GenericDialog;

/**
 * Class handling SNT preferences.
 *
 * @author Tiago Ferreira
 */
public class SNTPrefs {

	private static final int DRAW_DIAMETERS_XY = 1;
	private static final int SNAP_CURSOR = 2;
	// private static final int SHOW_MIP_OVERLAY = 4;
	private static final int AUTO_CANVAS_ACTIVATION = 8;
	private static final int ENFORCE_LUT = 16;
	private static final int USE_THREE_PANE = 32;
	private static final int USE_3D_VIEWER = 64;
	private static final int LOOK_FOR_TUBES = 128;
	private static final int LOOK_FOR_OOF = 256;
	private static final int SHOW_ONLY_SELECTED = 512;
	private static final int STORE_WIN_LOCATIONS = 1024;
	// private static final int JUST_NEAR_SLICES = 1024;
	private static final int ENFORCE_DEFAULT_PATH_COLORS = 2048;
	private static final int DEBUG = 4096;
	private static final int LOOK_FOR_TRACES = 8192;
	private static final int COMPRESSED_XML = 16384;

	private static final String BOOLEANS = "tracing.snt.booleans";
	private static final String SNAP_XY = "tracing.snt.xysnap";
	private static final String SNAP_Z = "tracing.snt.zsnap";
	private static final String PATHWIN_LOC = "tracing.snt.pwloc";
	private static final String FILLWIN_LOC = "tracing.snt.fwloc";

	//private static final String SNAP_Z = "tracing.snt.zsnap";

	// private final static String NEARBY_VIEW = "tracing.snt.nearbyview";

	private final int UNSET_PREFS = -1;
	private int currentBooleans;
	private final Simple_Neurite_Tracer snt;

	public SNTPrefs(final Simple_Neurite_Tracer snt) {
		this.snt = snt;
		getBooleans();
	}

	public SNTPrefs(final SimpleNeuriteTracer snt) {
		this.snt = (Simple_Neurite_Tracer) snt;
		getBooleans();
	}

	private int getDefaultBooleans() {
		return DRAW_DIAMETERS_XY + SNAP_CURSOR + AUTO_CANVAS_ACTIVATION + USE_THREE_PANE + LOOK_FOR_TUBES + LOOK_FOR_OOF
				+ LOOK_FOR_TRACES + COMPRESSED_XML;
	}

	private void getBooleans() {
		// Somehow Prefs.getInt() fails. We'll cast from double instead
		currentBooleans = (int) Prefs.get(BOOLEANS, UNSET_PREFS);
		if (currentBooleans == UNSET_PREFS)
			currentBooleans = getDefaultBooleans();
	}

	protected void loadPluginPrefs() {
		getBooleans();
		snt.useCompressedXML = getPref(COMPRESSED_XML);
		snt.autoCanvasActivation = getPref(AUTO_CANVAS_ACTIVATION);
		snt.snapCursor = getPref(SNAP_CURSOR);
		snt.drawDiametersXY = getPref(DRAW_DIAMETERS_XY);
		snt.displayCustomPathColors = !getPref(ENFORCE_DEFAULT_PATH_COLORS);
		snt.setShowOnlySelectedPaths(getPref(SHOW_ONLY_SELECTED), false);
		SimpleNeuriteTracer.verbose = getPref(DEBUG);
		snt.cursorSnapWindowXY = (int) Prefs.get(SNAP_XY, 4);
		snt.cursorSnapWindowXY = whithinBoundaries(snt.cursorSnapWindowXY,
				SimpleNeuriteTracer.MIN_SNAP_CURSOR_WINDOW_XY, SimpleNeuriteTracer.MAX_SNAP_CURSOR_WINDOW_XY);
		snt.cursorSnapWindowZ = (int) Prefs.get(SNAP_Z, 0);
		snt.cursorSnapWindowZ = whithinBoundaries(snt.cursorSnapWindowZ, SimpleNeuriteTracer.MIN_SNAP_CURSOR_WINDOW_Z,
				SimpleNeuriteTracer.MAX_SNAP_CURSOR_WINDOW_Z);
		if (snt.cursorSnapWindowZ > snt.depth)
			snt.cursorSnapWindowZ = snt.depth;
	}

	private int whithinBoundaries(final int value, final int min, final int max) {
		if (value < min)
			return min;
		if (value > max)
			return max;
		return value;
	}

	protected void loadStartupPrefs() {
		snt.forceGrayscale = getPref(ENFORCE_LUT);
		snt.look4oofFile = getPref(LOOK_FOR_OOF);
		snt.look4tubesFile = getPref(LOOK_FOR_TUBES);
		snt.look4tracesFile = getPref(LOOK_FOR_TRACES);
		snt.setSinglePane(!getPref(USE_THREE_PANE));
		snt.use3DViewer = getPref(USE_3D_VIEWER);
	}

	private boolean getPref(final int key) {
		return (currentBooleans & key) != 0;
	}

	protected void saveStartupPrefs() {
		setPref(ENFORCE_LUT, snt.forceGrayscale);
		setPref(LOOK_FOR_OOF, snt.look4oofFile);
		setPref(LOOK_FOR_TUBES, snt.look4tubesFile);
		setPref(LOOK_FOR_TRACES, snt.look4tracesFile);
		setPref(USE_THREE_PANE, !snt.getSinglePane());
		setPref(USE_3D_VIEWER, snt.use3DViewer);
		Prefs.set(BOOLEANS, currentBooleans);
		Prefs.savePreferences();
	}

	protected void savePluginPrefs() {
		setPref(COMPRESSED_XML, snt.useCompressedXML);
		setPref(AUTO_CANVAS_ACTIVATION, snt.autoCanvasActivation);
		setPref(SNAP_CURSOR, snt.snapCursor);
		Prefs.set(SNAP_XY, snt.cursorSnapWindowXY);
		Prefs.set(SNAP_Z, snt.cursorSnapWindowZ);
		setPref(DRAW_DIAMETERS_XY, snt.drawDiametersXY);
		setPref(ENFORCE_DEFAULT_PATH_COLORS, !snt.displayCustomPathColors);
		setPref(SHOW_ONLY_SELECTED, snt.showOnlySelectedPaths);
		setPref(DEBUG, SimpleNeuriteTracer.verbose);
		Prefs.set(BOOLEANS, currentBooleans);
		clearLegacyPrefs();
		if (isSaveWinLocations()) {
			final NeuriteTracerResultsDialog rd = snt.resultsDialog;
			if (rd == null)
				return;
			final PathWindow pw = rd.getPathWindow();
			if (pw != null)
				Prefs.saveLocation(PATHWIN_LOC, pw.getLocation());
			final FillWindow fw = rd.getFillWindow();
			if (fw != null)
				Prefs.saveLocation(FILLWIN_LOC, fw.getLocation());
		}
	}

	protected boolean isSaveWinLocations() {
		return getPref(STORE_WIN_LOCATIONS);
	}

	private void setPref(final int key, final boolean value) {
		if (value)
			currentBooleans |= key;
		else
			currentBooleans &= ~key;
	}

	protected Point getPathWindowLocation() {
		return Prefs.getLocation(PATHWIN_LOC);
	}

	protected Point getFillWindowLocation() {
		return Prefs.getLocation(FILLWIN_LOC);
	}

	private void resetOptions() {
		clearLegacyPrefs();
		Prefs.set(BOOLEANS, null);
		Prefs.set(SNAP_XY, null);
		Prefs.set(SNAP_Z, null);
		Prefs.set(FILLWIN_LOC, null);
		Prefs.set(PATHWIN_LOC, null);
		currentBooleans = UNSET_PREFS;
	}

	private void clearLegacyPrefs() {
		Prefs.set("tracing.Simple_Neurite_Tracer.drawDiametersXY", null);
	}

	protected void promptForOptions() {

		final int startupOptions = 7;
		final int pluginOptions = 2;

		final String[] startupLabels = new String[startupOptions];
		final int[] startupItems = new int[startupOptions];
		final boolean[] startupStates = new boolean[startupOptions];
		int idx = 0;

		startupItems[idx] = ENFORCE_LUT;
		startupLabels[idx] = "Enforce non-inverted grayscale LUT";
		startupStates[idx++] = snt.forceGrayscale;

		startupItems[idx] = USE_THREE_PANE;
		startupLabels[idx] = "Use_three-pane view";
		startupStates[idx++] = !snt.getSinglePane();

		startupItems[idx] = USE_3D_VIEWER;
		startupLabels[idx] = "Use_3D Viewer";
		startupStates[idx++] = snt.use3DViewer;

		startupItems[idx] = LOOK_FOR_TUBES;
		startupLabels[idx] = "Load_Tubeness \".tubes.tif\" pre-processed file (if present)";
		startupStates[idx++] = snt.look4tubesFile;

		startupItems[idx] = LOOK_FOR_OOF;
		startupLabels[idx] = "Load_Tubular_Geodesics \".oof.ext\" pre-processed file (if present)";
		startupStates[idx++] = snt.look4oofFile;

		startupItems[idx] = LOOK_FOR_TRACES;
		startupLabels[idx] = "Load_default \".traces\" file (if present)";
		startupStates[idx++] = snt.look4tracesFile;

		startupItems[idx] = STORE_WIN_LOCATIONS;
		startupLabels[idx] = "Remember window locations across restarts";
		startupStates[idx++] = isSaveWinLocations();

		final String[] pluginLabels = new String[pluginOptions];
		final int[] pluginItems = new int[pluginOptions];
		final boolean[] pluginStates = new boolean[pluginOptions];
		idx = 0;

		pluginItems[idx] = COMPRESSED_XML;
		pluginLabels[idx] = "Use compression when saving traces";
		pluginStates[idx++] = snt.useCompressedXML;

		pluginItems[idx] = DEBUG;
		pluginLabels[idx] = "Enable_debug mode";
		pluginStates[idx++] = SimpleNeuriteTracer.verbose;

		final GenericDialog gd = new GenericDialog("SNT v" + SNT.VERSION + " Preferences");
		final Font font = new Font("SansSerif", Font.BOLD, 12);
		gd.setInsets(0, 0, 0);
		gd.addMessage("Startup Options:", font);
		gd.setInsets(0, 0, 0);
		gd.addCheckboxGroup(startupOptions, 1, startupLabels, startupStates);
		gd.setInsets(20, 0, 0);
		gd.addMessage("Advanced Options:", font);
		gd.setInsets(0, 0, 0);
		gd.addCheckboxGroup(pluginOptions, 1, pluginLabels, pluginStates);

		gd.enableYesNoCancel("OK", "Revert to Defaults");
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		} else if (gd.wasOKed()) {

			for (int i = 0; i < startupOptions; i++) {
				if (gd.getNextBoolean())
					currentBooleans |= startupItems[i];
				else
					currentBooleans &= ~startupItems[i];
			}
			for (int i = 0; i < pluginOptions; i++) {
				if (gd.getNextBoolean())
					currentBooleans |= pluginItems[i];
				else
					currentBooleans &= ~pluginItems[i];
			}
			Prefs.set(BOOLEANS, currentBooleans);

		} else {
			resetOptions();
		}

		Prefs.savePreferences();
		loadPluginPrefs();

	}

}
