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

import java.util.ArrayList;

import org.scijava.app.StatusService;

/**
 * An implementation of the MultiTaskProgress interface that updates the
 * {@link StatusService} progress bar
 */
public class FittingProgress implements MultiTaskProgress {

	int totalTasks;
	double progress;
	private final StatusService statusService;
	private final ArrayList<Double> tasksProportionsDone;

	public FittingProgress(final StatusService statusService, final int totalTasks) {
		this.statusService = statusService;
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
		statusService.showStatus((int) totalDone, totalTasks, "Fitting... ");
	}

	protected double getProgress() {
		return progress;
	}

	@Override
	public void done() {
		statusService.clearStatus();
		statusService.showProgress(0, 0);
	}
}
