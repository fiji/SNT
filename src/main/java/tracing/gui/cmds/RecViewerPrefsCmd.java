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

import net.imagej.ImageJ;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.NumberWidget;

import tracing.gui.GuiUtils;

/**
 * Command implementing Reconstruction Viewer 'Preferences...' command.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Preferences",
	initializer = "init")
public class RecViewerPrefsCmd extends ContextCommand {

	public static String DEF_SNAPSHOT_DIR = System.getProperty("user.home") +
		File.separator + "Desktop" + File.separator + "SNT3";
	public static double DEF_ROTATION_ANGLE = 360d;
	public static double DEF_ROTATION_DURATION = 12;
	public static int DEF_ROTATION_FPS = 30;
	public static String DEF_CONTROLS_SENSITIVY = "High";

	@Parameter(label = "<HTML><b>I. Snapshot Recordings:", required = false,
		visibility = ItemVisibility.MESSAGE)
	private String HEADER1;

	@Parameter(label = "Save Directory", style = "directory",
		callback = "snapshotDirChanged",
		description = "Directory where static snapshots and animated sequences will be saved")
	private File snapshotDir;

	@Parameter(label = "Animated Rotations:", required = false,
		visibility = ItemVisibility.MESSAGE)
	private String SUB_HEADER1;

	@Parameter(required = false, persist = true, label = "Rotation degrees",
		style = NumberWidget.SCROLL_BAR_STYLE, stepSize = "10", min = "30",
		max = "360d")
	private double rotationAngle;

	@Parameter(label = "Duration (s)")
	private double rotationDuration;

	@Parameter(label = "Frames per second (FPS)")
	private int rotationFPS;

	@Parameter(label = "<HTML>&nbsp;", required = false,
		visibility = ItemVisibility.MESSAGE)
	private String msg1;

	@Parameter(label = "<HTML><b>II. Keyboard &amp; Mouse Controls:",
		required = false, visibility = ItemVisibility.MESSAGE)
	private String HEADER2;

	@Parameter(label = "Default sensitivity", required = false,
		callback = "sensitivityChanged",
		description = "The default (startup) sensivity for panning, zooming and rotating using hotkeys",
		choices = { "Low", "Medium", "High", "Highest" })
	private String sensitivity;
	private String storedSensitivity;

	@Parameter(label = "<HTML>&nbsp;", required = false,
		visibility = ItemVisibility.MESSAGE)
	private String msg2;

	@Parameter(label = "Defaults", callback = "reset")
	private Button defaults;

	private void init() {
		if (snapshotDir == null) snapshotDir = new File(DEF_SNAPSHOT_DIR);
		snapshotDirChanged();
		if (rotationAngle == 0) rotationAngle = DEF_ROTATION_ANGLE;
		if (rotationDuration == 0) rotationDuration = DEF_ROTATION_DURATION;
		if (rotationFPS == 0) rotationFPS = DEF_ROTATION_FPS;
		if (sensitivity == null) sensitivity =
			DEF_CONTROLS_SENSITIVY;
		storedSensitivity = sensitivity;
		sensitivityChanged();
	}

	@SuppressWarnings("unused")
	private void reset() {
		snapshotDir = null;
		rotationAngle = 0;
		rotationDuration = 0;
		rotationFPS = 0;
		sensitivity = null;
		init();
	}

	private void snapshotDirChanged() {
		if (snapshotDir == null || !snapshotDir.exists()) {
			msg1 = "<HTML>Path does not exist and will be created.";
		}
		else if (!snapshotDir.canWrite()) {
			msg1 = "<HTML><font color='red'>Directory is not writable!";
		}
		else {
			msg1 = "<HTML>&nbsp;";
		}
	}

	private void sensitivityChanged() {
		msg2 = (storedSensitivity.equals(sensitivity))
			? "<HTML>&nbsp;" : "<HTML>New settings active after restart...";
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		// The sole purpose of this run() is to store the parameters using the
		// PrefService mechanism. Thus, we don't need to do nothing here
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(RecViewerPrefsCmd.class, true);
	}

}
