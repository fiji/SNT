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

public interface TraceLoaderListener {

	public void gotVertex(int vertexIndex, float x_scaled, float y_scaled, float z_scaled, int x_image, int y_image,
			int z_image);

	public void gotLine(int fromVertexIndex, int toVertexIndex);

	public void gotWidth(int width);

	public void gotHeight(int height);

	public void gotDepth(int depth);

	public void gotSpacingX(float spacing_x);

	public void gotSpacingY(float spacing_y);

	public void gotSpacingZ(float spacing_z);

}
