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

package tracing.analysis;

import java.awt.Color;
import java.util.HashSet;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;
import tracing.Path;
import tracing.PathNode;
import tracing.SNT;
import tracing.Tree;
import tracing.hyperpanes.MultiDThreePanes;
import tracing.util.PointInImage;

/**
 * Converts SNT {@link Path}s into (IJ1) ROIs.
 *
 * @see tracing.plugin.ROIExporterCmd
 *
 * @author Tiago Ferreira
 */
public class RoiConverter extends TreeAnalyzer {

	/** SNT's XY view (the default export plane) */
	public static final int XY_PLANE = MultiDThreePanes.XY_PLANE;
	/** SNT's ZY view */
	public static final int ZY_PLANE = MultiDThreePanes.ZY_PLANE;
	/** SNT's XZ view */
	public static final int XZ_PLANE = MultiDThreePanes.XZ_PLANE;

	private int width = -1; // flag to use mean path diameter
	private int exportPlane = XY_PLANE;
	private boolean useSWCcolors;

	public RoiConverter(final Tree tree) {
		super(tree);
	}

	/**
	 * Converts paths into 2D polyline ROIs (segment paths)
	 *
	 * @param overlay
	 *            the target overlay to hold converted paths
	 */
	public void convertPaths(Overlay overlay) {
		if (overlay == null)
			overlay = new Overlay();
		for (final Path p : tree.list()) {
			if (p.size() > 1) {
				drawPathSegments(p, overlay);
			} else { // Single Point Path
				final HashSet<PointInImage> pim = new HashSet<>();
				pim.add(p.getPointInImage(0));
				convertPoints(pim, overlay, getColor(p), "SPP");
			}
		}
	}

	/**
	 * Converts all the tips associated with the parsed paths into {@link ij.gui.PointRoi}s
	 *
	 * @see TreeAnalyzer#getTips()
	 * @param overlay
	 *            the target overlay to hold converted point
	 */
	@SuppressWarnings("deprecation")
	public void convertTips(Overlay overlay) {
		if (overlay == null)
			overlay = new Overlay();
		convertPoints(getTips(), overlay, Path.getSWCcolor(Path.SWC_END_POINT), Path.SWC_END_POINT_LABEL);
	}

	/**
	 * Converts all the branch points associated with the parsed paths into
	 * {@link ij.gui.PointRoi}s
	 *
	 * @see TreeAnalyzer#getBranchPoints()
	 * @param overlay
	 *            the target overlay to hold converted point
	 */
	@SuppressWarnings("deprecation")
	public void convertBranchPoints(Overlay overlay) {
		if (overlay == null)
			overlay = new Overlay();
		convertPoints(getBranchPoints(), overlay, Path.getSWCcolor(Path.SWC_FORK_POINT), Path.SWC_FORK_POINT_LABEL);
	}

	/**
	 * Sets the exporting view for segment paths (XY by default).
	 *
	 * @param view
	 *            either {@link XY_PLANE}, {@link XZ_PLANE} or {@link ZY_PLANE}.
	 */
	public void setView(final int view) {
		if (view != XY_PLANE && view != ZY_PLANE && view != XZ_PLANE)
			throw new IllegalArgumentException("plane is not a valid MultiDThreePanes flag");
		this.exportPlane = view;
	}

	/**
	 * Specifies coloring of ROIs by SWC type.
	 *
	 * @param useSWCcolors
	 *            if true converted ROIs are colored according to their SWC type
	 *            integer flag
	 */
	public void useSWCcolors(final boolean useSWCcolors) {
		this.useSWCcolors = useSWCcolors;
	}

	/**
	 * Sets the line width of converted segment paths. Set it to -1 to have ROIs
	 * plotted using the average diameter of the path
	 *
	 * @param width
	 *            the new stroke width
	 * @see tracing.Path#getMeanRadius
	 * @see ij.gui.Roi#getStrokeWidth
	 */
	public void setStrokeWidth(final int width) {
		this.width = width;
	}

	private Color getColor(final Path p) {
		return (useSWCcolors) ? Path.getSWCcolor(p.getSWCType()) : p.getColor();
	}

	private void drawPathSegments(final Path path, final Overlay overlay) {

		final String basename = path.getName();
		final Color color = getColor(path);
		final double stroke = (width == -1) ? path.getMeanRadius() * 2 : width;

		FloatPolygon polygon = new FloatPolygon();
		int current_roi_slice = Integer.MIN_VALUE;
		int roi_identifier = 1;

		for (int i = 0; i < path.size(); ++i) {

			double x = Integer.MIN_VALUE;
			double y = Integer.MIN_VALUE;
			int slice_of_point = Integer.MIN_VALUE;

			switch (exportPlane) {
			case XY_PLANE:
				x = path.getXUnscaledDouble(i);
				y = path.getYUnscaledDouble(i);
				slice_of_point = path.getZUnscaled(i);
				break;
			case XZ_PLANE:
				x = path.getXUnscaledDouble(i);
				y = path.getZUnscaledDouble(i);
				slice_of_point = path.getYUnscaled(i);
				break;
			case ZY_PLANE:
				x = path.getZUnscaledDouble(i);
				y = path.getYUnscaledDouble(i);
				slice_of_point = path.getXUnscaled(i);
				break;
			default:
				throw new IllegalArgumentException("exportPlane is not valid");
			}

			if (current_roi_slice == slice_of_point || i == 0) {
				polygon.addPoint(x, y);
			} else {
				addPolyLineToOverlay(polygon, current_roi_slice, basename, roi_identifier++, color, stroke, overlay);
				polygon = new FloatPolygon(); // reset ROI
				polygon.addPoint(x, y);
			}
			current_roi_slice = slice_of_point;

		}

		// Create ROI from any remaining points
		addPolyLineToOverlay(polygon, current_roi_slice, basename, roi_identifier, color, stroke, overlay);

	}

	private void addPolyLineToOverlay(final FloatPolygon p, final int z_position, final String basename,
			final int roi_id, final Color color, final double strokeWidth, final Overlay overlay) {
		final String sPlane = getExportPlaneAsString();
		if (p.npoints > 0) {
			if (p.npoints == 1) {
				// create 1-pixel length lines for single points
				p.xpoints[0] -= 0.5f;
				p.ypoints[0] -= 0.5f;
				p.addPoint(p.xpoints[0] + 0.5f, p.ypoints[0] + 0.5f);
			}
			final PolygonRoi polyline = new PolygonRoi(p, Roi.FREELINE);
			polyline.enableSubPixelResolution();
			// polyline.fitSplineForStraightening();
			polyline.setStrokeColor(color);
			polyline.setStrokeWidth(strokeWidth);
			polyline.setName(String.format("%s-%s-%04d-Z%d", basename, sPlane, roi_id, z_position));
			polyline.setPosition(0, z_position, 0);
			overlay.add(polyline);
		}
	}

	/* this will aggregate all points into a single multipoint ROI */
	private void convertPoints(final HashSet<PointInImage> points, final Overlay overlay, final Color color,
			final String id) {
		if (points.isEmpty())
			return;
		final ImagePlus boundsImp = tree.getImpContainer(exportPlane); // NB: this image is just required to
																		// assign Z -positions to points. It is
																		// an overhead and not required for 2D
																		// images
		final SNTPointRoi roi = new SNTPointRoi(boundsImp);
		final String sPlane = getExportPlaneAsString();
		for (final PointInImage p : points) {
			final Path path = p.onPath;
			if (path == null) {
				SNT.log("Converting " + path + " failed. Skipping it...");
				continue;
			}
			final double[] coordinates = PathNode.unScale(p, exportPlane);
			final int slice = (int) Math.round(coordinates[2]);
			roi.addPoint(coordinates[0], coordinates[1], slice);
		}
		roi.setStrokeColor(color);
		roi.setName(String.format("%s-roi-%s", id, sPlane));
		overlay.add(roi);
	}

	private String getExportPlaneAsString() {
		switch (exportPlane) {
		case XZ_PLANE:
			return "XZ";
		case ZY_PLANE:
			return "ZY";
		default:
			return "XY";
		}
	}

	/**
	 * With current IJ1.51u API the only way to set the z-position of a point is to
	 * activate the corresponding image z-slice. This class makes it easier to do
	 * so.
	 */
	private static class SNTPointRoi extends PointRoi {

		private static final long serialVersionUID = 1L;
		private final ImagePlus imp;

		public SNTPointRoi(final ImagePlus imp) {
			super(0, 0);
			deletePoint(0);
			this.imp = imp;
		}

		public void addPoint(final double ox, final double oy, final int slice) {
			imp.setPositionWithoutUpdate(1, slice, 1);
			super.addPoint(imp, ox, oy);
		}
	}
}
