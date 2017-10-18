/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2017 Fiji developers.
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

import java.awt.Graphics;
import java.util.ArrayList;

public class ManualTracerThread extends Thread implements SearchInterface {

	private final int start_x;
	private final int start_y;
	private final int start_z;
	private final int goal_x;
	private final int goal_y;
	private final int goal_z;
	private final SimpleNeuriteTracer plugin;
	private final ArrayList<SearchProgressCallback> progListeners =
		new ArrayList<>();
	private volatile int threadStatus = SearchThread.PAUSED;
	private Path result;

	public ManualTracerThread(final SimpleNeuriteTracer plugin, final int start_x,
		final int start_y, final int start_z, final int goal_x, final int goal_y,
		final int goal_z)
	{
		if (goal_x > plugin.width || goal_y > plugin.width || goal_z > plugin.depth)
			throw new IllegalArgumentException("Out-of bounds goal");
		this.start_x = start_x;
		this.start_y = start_y;
		this.start_z = start_z;
		this.goal_x = goal_x;
		this.goal_y = goal_y;
		this.goal_z = goal_z;
		this.plugin = plugin;
	}

	@Override
	public void run() {
		result = new Path(plugin.x_spacing, plugin.y_spacing, plugin.z_spacing,
			plugin.spacing_units);
		result.addPointDouble(start_x, start_y, start_z);
		result.addPointDouble(goal_x, goal_y, goal_z);
		threadStatus = SearchThread.SUCCESS;
		for (final SearchProgressCallback progress : progListeners)
			progress.finished(this, true);
		return;
	}

	@Override
	public Path getResult() {
		return result;
	}

	@Override
	public void drawProgressOnSlice(final int plane,
		final int currentSliceInPlane, final TracerCanvas canvas, final Graphics g)
	{
		// do nothing.
	}

	public int getThreadStatus() {
		return threadStatus;
	}

	@Override
	public void requestStop() {
		SNT.debug("requestStop called on manual tracing thread?");
		synchronized (this) {
			if (threadStatus == SearchThread.PAUSED) this.interrupt();
			threadStatus = SearchThread.STOPPING;
			reportThreadStatus();
		}
	}

	public void addProgressListener(final SearchProgressCallback callback) {
		progListeners.add(callback);
	}

	public void reportThreadStatus() {
		for (final SearchProgressCallback progress : progListeners)
			progress.threadStatus(this, threadStatus);
	}

}
