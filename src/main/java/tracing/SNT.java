package tracing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.JButton;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.util.VersionUtils;

import ij.IJ;
import ij.plugin.Colors;

/** Static utilities for SNT **/
public class SNT {

	private static Context context;
	private static LogService logService;
	public static final String VERSION = getVersion();

	private static boolean initialized;

	private SNT() {
	}

	private synchronized static void initialize() {
		if (initialized)
			return;
		if (context == null)
			context = (Context) IJ.runPlugIn("org.scijava.Context", "");
		if (logService == null)
			logService = context.getService(LogService.class);
		initialized = true;
	}

	private static String getVersion() {
		return VersionUtils.getVersion(tracing.SimpleNeuriteTracer.class);
	}

	protected static void error(final String string) {
		IJ.error("Simple Neurite Tracer v" + VERSION, string);
	}

	protected static void log(final String string) {
		if (!initialized)
			initialize();
		logService.info("[SNT] " + string);
	}

	protected static void warn(final String string) {
		if (!initialized)
			initialize();
		logService.warn("[SNT] " + string);
	}

	protected static void log(final String... strings) {
		if (strings != null)
			log(String.join(" ", strings));
	}

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
