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

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.JButton;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.UIService;
import org.scijava.util.VersionUtils;

import ij.IJ;
import ij.plugin.Colors;

/** Static utilities for SNT **/
public class SNT {

	private static Context context;
	private static LogService logService;
	private static UIService uiService;
	public static final String VERSION = getVersion();

	private SNT() {
		// prevent instantiation of utility class
	}

	private synchronized static void initialize() {
		if (context == null) setContext((Context) IJ.runPlugIn(
			"org.scijava.Context", ""));
	}

	public static void setContext(final Context cntxt) {
		if (context != null) return;
		context = cntxt;
		if (logService == null) logService = context.getService(LogService.class);
		if (uiService == null) uiService = context.getService(UIService.class);
	}

	private static String getVersion() {
		return VersionUtils.getVersion(tracing.SimpleNeuriteTracer.class);
	}

	@Deprecated
	public static void error(final String string) {
		initialize();
		uiService.showDialog(string, "Simple Neurite Tracer v" + VERSION,
			MessageType.ERROR_MESSAGE);
	}

	protected static void log(final String string) {
		initialize();
		logService.info("[SNT] " + string);
	}

	protected static void warn(final String string) {
		initialize();
		logService.warn("[SNT] " + string);
	}

	protected static void log(final String... strings) {
		if (strings != null) log(String.join(" ", strings));
	}

	protected static void debug(Object msg) {
		if (SimpleNeuriteTracer.verbose) {
			initialize();
			logService.debug("[SNT] " + msg);
		}
	}

	public static String stripExtension(final String filename) {
		final int lastDot = filename.lastIndexOf(".");
		if (lastDot > 0) return filename.substring(0, lastDot);
		return null;
	}

	//FIXME: Move to gui.GuiUtils
	protected static JButton smallButton(final String text) {
		final double SCALE = .85;
		final JButton button = new JButton(text);
		final Font font = button.getFont();
		button.setFont(font.deriveFont((float) (font.getSize() * SCALE)));
		final Insets insets = button.getMargin();
		button.setMargin(new Insets((int) (insets.top * SCALE), (int) (insets.left * SCALE),
				(int) (insets.bottom * SCALE), (int) (insets.right * SCALE)));
		return button;
	}

	protected static String getColorString(final Color color) {
		String name = "none";
		name = Colors.getColorName(color, name);
		if (!"none".equals(name))
			name = Colors.colorToString(color);
		return name;
	}

	protected static Color getColor(String colorName) {
		if (colorName == null)
			colorName = "none";
		Color color = null;
		color = Colors.getColor(colorName, color);
		if (color == null)
			color = Colors.decode(colorName, color);
		return color;
	}

}
