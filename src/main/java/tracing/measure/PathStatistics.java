package tracing.measure;

import java.util.ArrayList;
import java.util.HashSet;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import tracing.Path;
import tracing.PathAndFillManager;

/**
 * The Class PathStatistics.
 */
public class PathStatistics extends PathAnalyzer {

	/** Flag encoding the length of a path */
	public static final int LENGTH = 1;

	/** Flag encoding number of nodes in a path */
	public static final int N_NODES = 2;

	/** Flag encoding distances between consecutive nodes*/
	public static final int INTER_NODE_DISTANCE = 4;

	/** Flag encoding radius of nodes */
	public static final int NODE_RADIUS = 8;


	public PathStatistics(final ArrayList<Path> paths) {
		super(paths);
	}

	public PathStatistics(final HashSet<Path> paths) {
		super(paths);
	}

	public PathStatistics(final PathAndFillManager pafm) {
		super(pafm);
	}

	/**
	 * Computes the {@link SummaryStatistics} for the specified measurement.
	 *
	 * @param measurement
	 *            the measurement ({@link N_NODES}, {@link NODE_RADIUS}, etc.
	 * @return the SummaryStatistics object.
	 */
	public SummaryStatistics getStatistics(final int measurement) {
		final SummaryStatistics stat = new SummaryStatistics();
		switch (measurement) {
		case LENGTH:
			for (final Path p : paths)
				stat.addValue(p.getRealLength());
			break;
		case N_NODES:
			for (final Path p : paths)
				stat.addValue(p.size());
			break;
		case INTER_NODE_DISTANCE:
			for (final Path p : paths) {
				if (p.size() < 2)
					continue;
				for (int i = 0; i < p.size(); i += 2) {
					stat.addValue(p.getPointInImage(i + 1).distanceTo(p.getPointInImage(i)));
				}
			}
			break;
		case NODE_RADIUS:
			for (final Path p : paths) {
				for (int i = 0; i < p.size(); i++) {
					stat.addValue(p.getNodeRadius(i));
				}
			}
			break;
		default:
			throw new IllegalArgumentException("Unrecognized parameter " + measurement);
		}
		return stat;
	}

}
