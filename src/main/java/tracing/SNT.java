package tracing;

import org.scijava.util.VersionUtils;

import ij.IJ;

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

}
