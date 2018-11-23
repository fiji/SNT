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

import java.awt.Color;
import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.ui.UIService;
import org.scijava.ui.console.ConsolePane;
import org.scijava.util.VersionUtils;

import fiji.util.Levenshtein;
import ij.IJ;
import ij.measure.Calibration;
import ij.plugin.Colors;
import tracing.util.BoundingBox;

/** Static utilities for SNT **/
public class SNT {

	private static Context context;
	private static LogService logService;
	private static UIService uiService;

	public static final String VERSION = getVersion();

	private static boolean initialized;
	private static SimpleNeuriteTracer plugin;

	private SNT() {}

	private synchronized static void initialize() {
		if (initialized) return;
		if (context == null) context = (Context) IJ.runPlugIn("org.scijava.Context",
			"");
		if (logService == null) logService = context.getService(LogService.class);
		if (uiService == null) uiService = context.getService(UIService.class);
		initialized = true;
	}

	public static String getReadableVersion() {
		if (VERSION.length()< 21) return "SNT "+ VERSION;
		return "SNT "+ VERSION.substring(0, 21) + "...";
	}

	private static String getVersion() {
		return VersionUtils.getVersion(tracing.SimpleNeuriteTracer.class);
	}

	protected static synchronized void error(final String string) {
		if (SNT.isDebugMode()) nonDebugError(string);
	}

	protected static void setPlugin(final SimpleNeuriteTracer plugin) {
		SNT.plugin = plugin;
		if (context == null && plugin != null) context = plugin.getContext();
	}

	public static SimpleNeuriteTracer getPluginInstance() {
		return plugin;
	}

	protected static synchronized void nonDebugError(final String string) {
		if (!initialized) initialize();
		logService.error("[SNT] " + string);
	}

	public static synchronized void error(final String string,
		final Throwable t)
	{
		if (!SNT.isDebugMode()) return;
		if (!initialized) initialize();
		logService.error("[SNT] " + string, t);
	}

	public static synchronized void log(final String string) {
		if (!SNT.isDebugMode()) return;
		if (!initialized) initialize();
		logService.info("[SNT] " + string);
	}

	protected static synchronized void warn(final String string) {
		if (!SNT.isDebugMode()) return;
		if (!initialized) initialize();
		logService.warn("[SNT] " + string);
	}

	public static void csvQuoteAndPrint(final PrintWriter pw, final Object o) {
		pw.print(stringForCSV("" + o));
	}

	private static String stringForCSV(final String s) {
		boolean quote = false;
		String result = s;
		if (s.indexOf(',') >= 0) quote = true;
		if (s.indexOf('"') >= 0) {
			quote = true;
			result = s.replaceAll("\"", "\"\"");
		}
		if (quote) return "\"" + result + "\"";
		else return result;
	}

	protected static String getColorString(final Color color) {
		String name = "none";
		name = Colors.getColorName(color, name);
		if (!"none".equals(name)) name = Colors.colorToString(color);
		return name;
	}

	protected static Color getColor(String colorName) {
		if (colorName == null) colorName = "none";
		Color color = null;
		color = Colors.getColor(colorName, color);
		if (color == null) color = Colors.decode(colorName, color);
		return color;
	}

	public static String stripExtension(final String filename) {
		final int lastDot = filename.lastIndexOf(".");
		if (lastDot > 0) return filename.substring(0, lastDot);
		return null;
	}

	protected static boolean fileAvailable(final File file) {
		try {
			return file != null && file.exists();
		}
		catch (final SecurityException ignored) {
			return false;
		}
	}

	protected static boolean isValidURL(final String url) {
		try {
			new URL(url).toURI();
			return true;
		}
		catch (final Exception e) {
			return false;
		}
	}

	public static String formatDouble(final double value, final int digits) {
		String pattern = "0.";
		while (pattern.length() < digits + 2)
			pattern += "0";
		final double absValue = Math.abs(value);
		if (absValue < 0.01 || absValue >= 1000) pattern += "E0";
		return new DecimalFormat(pattern).format(value);
	}

	/**
	 * Assesses if SNT is running in debug mode
	 *
	 * @return the debug flag
	 */
	public static boolean isDebugMode() {
		return SimpleNeuriteTracer.verbose;
	}

	/**
	 * Enables/disables debug mode
	 *
	 * @param b verbose flag
	 */
	public static void setDebugMode(final boolean b) {
		if (isDebugMode() && !b) {
			log("Exiting debug mode...");
		}
		SimpleNeuriteTracer.verbose = b;
		if (isDebugMode()) {
			log("Entering debug mode..."); // will initialize uiService
			final ConsolePane<?> console = uiService.getDefaultUI().getConsolePane();
			if (console != null) console.show();
		}
	}

	public static File findClosestPair(final File file, final String pairExt) {
		try {
			SNT.log("Finding closest pair for " + file);
			final File dir = file.getParentFile();
			final String[] list = dir.list(new FilenameFilter() {

				@Override
				public boolean accept(final File f, final String s) {
					return s.endsWith(pairExt);
				}
			});
			SNT.log("Found " + list.length + " " + pairExt + " files");
			if (list.length == 0) return null;
			Arrays.sort(list);
			String dirPath = dir.getAbsolutePath();
			if (!dirPath.endsWith(File.separator)) dirPath += File.separator;
			int cost = Integer.MAX_VALUE;
			final String seed = stripExtension(file.getName().toLowerCase());
			String closest = null;
			final Levenshtein levenshtein = new Levenshtein(5, 10, 1, 5, 5, 0);
			for (final String item : list) {
				final String filename = stripExtension(Paths.get(item).getFileName()
					.toString()).toLowerCase();
				final int currentCost = levenshtein.cost(seed, filename);
				SNT.log("Levenshtein cost for '" + item + "': " + currentCost);
				if (currentCost <= cost) {
					cost = currentCost;
					closest = item;
				}
			}
			SNT.log("Identified pair '" + closest + "'");
			return new File(dirPath + closest);
		}
		catch (final SecurityException | NullPointerException ignored) {
			return null;
		}
	}

	public static boolean similarCalibrations(final Calibration a,
		final Calibration b)
	{
		double ax = 1, ay = 1, az = 1;
		double bx = 1, by = 1, bz = 1;
		String aunit = "", bunit = "";
		if (a != null) {
			ax = a.pixelWidth;
			ay = a.pixelHeight;
			az = a.pixelDepth;
			aunit = a.getUnit();
		}
		if (b != null) {
			bx = b.pixelWidth;
			by = b.pixelHeight;
			bz = b.pixelDepth;
			bunit = a.getUnit();
		}
		if (!aunit.equals(bunit)) return false;
		final double epsilon = 0.000001;
		final double pixelWidthDifference = Math.abs(ax - bx);
		if (pixelWidthDifference > epsilon) return false;
		final double pixelHeightDifference = Math.abs(ay - by);
		if (pixelHeightDifference > epsilon) return false;
		final double pixelDepthDifference = Math.abs(az - bz);
		if (pixelDepthDifference > epsilon) return false;
		return true;
	}

	protected static String getSanitizedUnit(final String unit) {
		final BoundingBox bd = new BoundingBox();
		bd.setUnit(unit);
		return bd.getUnit();
	}

	/**
	 * Generates a list of random paths. Only useful for debugging purposes
	 *
	 * @return the list of random Paths
	 */
	public static List<Path> randomPaths() {
		final List<Path> data = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			final Path p = new Path(1, 1, 1, "unit");
			final double v1 = new Random().nextGaussian();
			final double v2 = new Random().nextGaussian();
			p.addPointDouble(v1, v2, v1);
			p.addPointDouble(v2, v1, v2);
			data.add(p);
		}
		return data;
	}

}
