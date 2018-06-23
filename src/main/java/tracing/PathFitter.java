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

	private static int DEFAULT_NEIGHBORHOOD_SEARCH = 40;
	private final SimpleNeuriteTracer plugin;
	private final Path path;
	private final boolean showDetailedFittingResults;
	private final ImagePlus imp;
	private int fitterIndex;
	private MultiTaskProgress progress;
	private boolean succeeded;
	private int sideSearch = DEFAULT_NEIGHBORHOOD_SEARCH;

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
	 * Instantiates a new PathFitter
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
	 * Takes the signal from the image specified in the constructor to fit radii
	 * around the nodes of input path. Computation of radii is confined to the
	 * neighborhood specified by {@link #setMaxBounds(int)}
	 * 
	 * @return the reference to the fitted result.This Path is automatically set as
	 *         the fitted version of input Path.
	 */
	@Override
	public Path call() throws IllegalArgumentException {
		final Path fitted = path.fitCircles(getMaxBounds(), imp, showDetailedFittingResults, plugin, fitterIndex,
				progress);
		if (fitted == null) {
			succeeded = false;
			return null;
		}
		succeeded = true;
		path.setUseFitted(true);
		return fitted;
	}

	/**
	 * Gets the current fitting boundaries
	 *
	 * @return the boundaries, or {@link #DEFAULT_NEIGHBORHOOD_SEARCH} if no
	 *         boundaries have been set
	 */
	public int getMaxBounds() {
		return sideSearch;
	}

	/**
	 * Sets the current fitting boundaries
	 *
	 * @param maxBounds
	 *            the new fitting boundaries
	 */
	public void setMaxBounds(final int maxBounds) {
		this.sideSearch = maxBounds;
	}

}
