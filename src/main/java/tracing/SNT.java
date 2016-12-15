package tracing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.JButton;

import org.scijava.util.VersionUtils;

import ij.IJ;
import ij.plugin.Colors;

/** Static utilities for SNT **/
public class SNT {
	public static final String VERSION = getVersion();

	private SNT() {
	}

	private static String getVersion() {
		return VersionUtils.getVersion(tracing.SimpleNeuriteTracer.class);
	}

	protected static void error(final String string) {
		IJ.error("Simple Neurite Tracer v" + getVersion(), string);
	}

	protected static void log(final String string) {
		System.out.println("[SNT] " + string);
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
