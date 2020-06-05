/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2020 Fiji developers.
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

import java.util.ArrayList;

import ij.IJ;

/**
 * An implementation of the MultiTaskProgress interface that updates the ImageJ
 * progress bar
 */

public class FittingProgress implements MultiTaskProgress {
	ArrayList<Double> tasksProportionsDone;
	int totalTasks;

	public FittingProgress(final int totalTasks) {
		tasksProportionsDone = new ArrayList<>();
		this.totalTasks = totalTasks;
		for (int i = 0; i < totalTasks; ++i)
			tasksProportionsDone.add(0.0);
	}

	@Override
	synchronized public void updateProgress(final double proportion, final int taskIndex) {
		tasksProportionsDone.set(taskIndex, proportion);
		updateStatus();
	}

	protected void updateStatus() {
		double totalDone = 0;
		for (final double p : tasksProportionsDone)
			totalDone += p;
		IJ.showProgress(totalDone / totalTasks);
	}

	@Override
	public void done() {
		IJ.showProgress(1.0);
	}
}
