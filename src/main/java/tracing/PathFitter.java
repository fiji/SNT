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

/* This class represents a list of points, and has methods for drawing
 * them onto ThreePanes-style image canvases. */

public class PathFitter implements Callable<Path> {

	private final SimpleNeuriteTracer plugin;
	private final Path path;
	private final boolean showDetailedFittingResults;
	private int fitterIndex;
	private MultiTaskProgress progress;
	private boolean succeeded;

	public boolean getSucceeded() {
		return succeeded;
	}

	public PathFitter(final SimpleNeuriteTracer plugin, final Path path, final boolean showDetailedFittingResults) {
		if (path == null)
			throw new IllegalArgumentException("Cannot fit a null path");
		if (path.isFittedVersionOfAnotherPath())
			throw new IllegalArgumentException("Trying to fit an already fitted path");
		this.plugin = plugin;
		this.path = path;
		this.fitterIndex = -1;
		this.progress = null;
		this.showDetailedFittingResults = showDetailedFittingResults;
	}

	public void setProgressCallback(final int fitterIndex, final MultiTaskProgress progress) {
		this.fitterIndex = fitterIndex;
		this.progress = progress;
	}

	@Override
	public Path call() throws IllegalArgumentException {
		final Path fitted = path.fitCircles(40, plugin.getLoadedDataAsImp(), showDetailedFittingResults, plugin,
				fitterIndex, progress);
		if (fitted == null) {
			succeeded = false;
			return null;
		}
		succeeded = true;
		path.setUseFitted(true);
		return fitted;
	}

}
