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

import java.util.concurrent.Callable;

import ij.ImagePlus;

/**
 * Class for fitting circular cross-sections around existing nodes of a
 * {@link Path} in order to compute radii (node thickness) and midpoint
 * refinement of existing coordinates.
 */
public class PathFitter implements Callable<Path> {

	/** The default max radius constraining the fit. */
	public static int DEFAULT_MAX_RADIUS = 40;

	/**
	 * Flag specifying that the computed path should only inherit fitted radii
	 * attributes
	 */
	public static final int RADII = 1;

	/**
	 * Flag specifying that the computed path should only inherit midpoint
	 * refinement of node coordinates
	 */
	public static final int MIDPOINTS = 2;

	/**
	 * Flag specifying that the computed path should inherit both midpoint
	 * refinement of node coordinates and radii
	 */
	public static final int RADII_AND_MIDPOINTS = 4;

	private final SimpleNeuriteTracer plugin;
	private final Path path;
	private final boolean showDetailedFittingResults;
	private final ImagePlus imp;
	private int fitterIndex;
	private MultiTaskProgress progress;
	private boolean succeeded;
	private int sideSearch = DEFAULT_MAX_RADIUS;
	private int fitScope = RADII_AND_MIDPOINTS;

	/**
	 * Checks whether the fit succeeded.
	 *
	 * @return true if fit was successful, false otherwise
	 */
	public boolean getSucceeded() {
		return succeeded;
	}

	/**
	 * Instantiates a new PathFitter.
	 *
	 * @param imp
	 *            the Image containing the signal to which the fit will be performed
	 * @param path
	 *            the {@link Path} to be fitted
	 * 
	 */
	public PathFitter(final ImagePlus imp, final Path path) {
		if (path == null)
			throw new IllegalArgumentException("Cannot fit a null path");
		if (path.isFittedVersionOfAnotherPath())
			throw new IllegalArgumentException("Trying to fit an already fitted path");
		if (imp == null)
			throw new IllegalArgumentException("Cannot fit a null image");
		this.imp = imp;
		this.plugin = null;
		this.path = path;
		this.fitterIndex = -1;
		this.progress = null;
		this.showDetailedFittingResults = false;
	}

	/**
	 * Instantiates a new PathFitter.
	 *
	 * @param plugin
	 *            the {@link SimpleNeuriteTracer} instance specifying input image.
	 *            The computation will be performed on the image currently loaded by
	 *            the plugin.
	 * @param path
	 *            the {@link Path} to be fitted
	 * @param showFit
	 *            If true, an interactive stack (cross-section view) of the fit is
	 *            displayed. Note that this probably requires SNT's UI to be visible
	 *            and functional.
	 */
	public PathFitter(final SimpleNeuriteTracer plugin, final Path path, final boolean showFit) {
		if (path == null)
			throw new IllegalArgumentException("Cannot fit a null path");
		if (path.isFittedVersionOfAnotherPath())
			throw new IllegalArgumentException("Trying to fit an already fitted path");
		this.plugin = plugin;
		this.imp = plugin.getLoadedDataAsImp();
		this.path = path;
		this.fitterIndex = -1;
		this.progress = null;
		this.showDetailedFittingResults = showFit;
	}

	public void setProgressCallback(final int fitterIndex, final MultiTaskProgress progress) {
		this.fitterIndex = fitterIndex;
		this.progress = progress;
	}

	/**
	 * Takes the signal from the image specified in the constructor to fit
	 * cross-section circles around the nodes of input path. Computation of fit is
	 * confined to the neighborhood specified by {@link #setMaxRadius(int)}
	 *
	 * @return the reference to the computed result. This Path is automatically set
	 *         as the fitted version of input Path.
	 * @throws IllegalArgumentException
	 *             If path already has been fitted, and its fitted version not nullified
	 * @see #setScope(int)
	 */
	@Override
	public Path call() throws IllegalArgumentException {
		final Path fitted = path.fitCircles(getMaxRadius(), imp, fitScope, showDetailedFittingResults, plugin,
				fitterIndex, progress);
		if (fitted == null) {
			succeeded = false;
			return null;
		}
		succeeded = true;
		path.setFitted(fitted);
		path.setUseFitted(true);
		return fitted;
	}

	/**
	 * Gets the current max radius
	 *
	 * @return the maximum radius currently being considered, or
	 *         {@link #DEFAULT_MAX_RADIUS} if no {@link #setMaxRadius(int)} has not
	 *         been called
	 */
	public int getMaxRadius() {
		return sideSearch;
	}

	/**
	 * Sets the max radius for constraining the fit.
	 *
	 * @param maxRadius
	 *            the new maximum radius
	 */
	public void setMaxRadius(final int maxRadius) {
		this.sideSearch = maxRadius;
	}

	/**
	 * Sets the fitting scope.
	 *
	 * @param scope
	 *            Either {@link #RADII}, {@link #MIDPOINTS}, or
	 *            {@link #RADII_AND_MIDPOINTS} Note that the computation is always
	 *            performed assuming {@link #RADII_AND_MIDPOINTS}, but only the
	 *            attributes specified by {@code scope} will be applied to fitted
	 *            Path
	 */
	public void setScope(final int scope) {
		if (scope != PathFitter.RADII_AND_MIDPOINTS && scope != PathFitter.RADII && scope != PathFitter.MIDPOINTS) {
			throw new IllegalArgumentException(" Invalid flag. Only RADII, RADII, or RADII_AND_MIDPOINTS allowed");
		}
		this.fitScope = scope;
	}

	protected static String getScopeAsString(final int scope) {
		switch(scope) {
		case RADII_AND_MIDPOINTS:
			return "radii and midpoint refinement";
		case RADII:
			return "radii";
		case MIDPOINTS:
			return "midpoint refinement";
		default:
			return "Unrecognized option";
		}
	}
}
