/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
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

package tracing.analysis.graph;

import java.util.Map;

import org.jgrapht.Graph;
import org.jgrapht.ext.JGraphXAdapter;
import org.scijava.util.ColorRGB;

import com.mxgraph.util.mxConstants;

class TreeGraphAdapter<V, E> extends JGraphXAdapter<V, E> {

	private static final String DARK_GRAY = "#222222";
	private static final String LIGHT_GRAY = "#eeeeee";

	protected TreeGraphAdapter(final Graph<V, E> graph) {
		this(graph, LIGHT_GRAY);
	}

	protected TreeGraphAdapter(final Graph<V, E> graph, final String verticesColor) {
		super(graph);
		final String vColor = (verticesColor == null) ? LIGHT_GRAY : new ColorRGB(verticesColor).toHTMLColor();
		final Map<String, Object> edgeStyle = getStylesheet().getDefaultEdgeStyle();
		edgeStyle.put(mxConstants.STYLE_NOLABEL, true);
		edgeStyle.put(mxConstants.STYLE_STROKECOLOR, DARK_GRAY);
		final Map<String, Object> vertexStyle = getStylesheet().getDefaultVertexStyle();
		vertexStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
		vertexStyle.put(mxConstants.STYLE_VERTICAL_LABEL_POSITION, mxConstants.ALIGN_MIDDLE);
		vertexStyle.put(mxConstants.STYLE_FONTCOLOR, DARK_GRAY);
		vertexStyle.put(mxConstants.STYLE_STROKECOLOR, DARK_GRAY);
		vertexStyle.put(mxConstants.STYLE_FILLCOLOR, vColor);
	}

}
