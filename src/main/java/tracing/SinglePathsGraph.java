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

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.GZIPOutputStream;

import ij.IJ;

public class SinglePathsGraph {

	int width, height, depth;
	float spacing_x, spacing_y, spacing_z;

	public SinglePathsGraph(final int width, final int height, final int depth, final double spacing_x,
			final double spacing_y, final double spacing_z) {
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.spacing_x = (float) spacing_x;
		this.spacing_y = (float) spacing_y;
		this.spacing_z = (float) spacing_z;
	}

	// For fast lookup from positions:
	Hashtable<Integer, AutoPoint> fromPosition = new Hashtable<>();

	public AutoPoint get(final int x, final int y, final int z) {
		final int k = x + y * width + z * width * height;
		return fromPosition.get(k);
	}

	public void addPoint(final AutoPoint p, final AutoPoint previous) {
		final int k = p.x + p.y * width + p.z * width * height;
		final AutoPoint existingPoint = fromPosition.get(k);
		if (existingPoint == null) {
			fromPosition.put(k, p);
		}
		if (previous != null) {
			final int k_previous = previous.x + previous.y * width + previous.z * width * height;
			addLink(k_previous, k);
		}
	}

	/*
	 * This all turns out to be very memory hungry, so keep track of the links
	 * between points here, encoded into a long for each link:
	 */

	HashSet<Long> directedLinks = new HashSet<>();

	void addLink(final int from_k, final int to_k) {
		final long toAdd = (from_k << 32) + to_k;
		directedLinks.add(toAdd);
	}

	void addLink(final int from_x, final int from_y, final int from_z, final int to_x, final int to_y, final int to_z) {
		final int from_k = from_x + from_y * width + from_z * width * height;
		final int to_k = to_x + to_y * width + to_z * width * height;
		addLink(from_k, to_k);
	}

	void writeWavefrontObj(final String outputFilename) throws IOException {

		final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF-8"));

		// It's a bit ugly encoding this data in the comments,
		// but it's a useful enough stop gap measure:
		pw.println("# width: " + width);
		pw.println("# height: " + height);
		pw.println("# depth: " + depth);
		pw.println("# spacing_x: " + spacing_x);
		pw.println("# spacing_y: " + spacing_y);
		pw.println("# spacing_z: " + spacing_z);

		/* First build a map from keys to wavefront indices: */

		final Hashtable<Integer, Integer> wavefrontToKey = new Hashtable<>();

		final Set<Integer> keySet = fromPosition.keySet();

		int counter = 1;
		for (final int k : keySet) {
			wavefrontToKey.put(k, counter);
			final int x = k % width;
			final int y = (k / width) % height;
			final int z = (k / (width * height)) % depth;
			pw.println("v " + (x * spacing_x) + " " + (y * spacing_y) + " " + (z * spacing_z));
			++counter;
		}

		for (final long l : directedLinks) {
			final long from = l >> 32;
			final long to = l & 0xFFFFFFFF;
			final int from_k = (int) from;
			final int to_k = (int) to;
			final int from_vertex = wavefrontToKey.get(from_k);
			final int to_vertex = wavefrontToKey.get(to_k);
			pw.println("l " + from_vertex + " " + to_vertex);
		}

		pw.print("g");

		pw.close();
	}

	static public int getNonNegativeIntAfterPrefix(final String line, final String prefix) {
		try {
			final String s = line.substring(prefix.length());
			return Integer.parseInt(s);
		} catch (final NumberFormatException e) {
			return -1;
		}
	}

	static public float getNonNegativeFloatAfterPrefix(final String line, final String prefix) {
		try {
			final String s = line.substring(prefix.length());
			return Float.parseFloat(s);
		} catch (final NumberFormatException e) {
			return -1;
		}
	}

	static boolean loadWithListener(final String inputFilename, final TraceLoaderListener listener) {

		int width = -1, height = -1, depth = -1;
		float spacing_x = Float.MIN_VALUE;
		float spacing_y = Float.MIN_VALUE;
		float spacing_z = Float.MIN_VALUE;

		try {
			final String widthPrefix = "# width: ";
			final String heightPrefix = "# height: ";
			final String depthPrefix = "# depth: ";
			final String spacingXPrefix = "# spacing_x: ";
			final String spacingYPrefix = "# spacing_y: ";
			final String spacingZPrefix = "# spacing_z: ";

			final String vertexPrefix = "v ";
			final String linePrefix = "l ";
			final String groupPrefix = "g ";

			final int vertexIndex = 1;

			final BufferedReader br = new BufferedReader(new FileReader(inputFilename));
			String lastLine;
			while (null != (lastLine = br.readLine())) {

				if (lastLine.startsWith(widthPrefix)) {
					width = getNonNegativeIntAfterPrefix(lastLine, widthPrefix);
					listener.gotWidth(width);
				}
				if (lastLine.startsWith(heightPrefix)) {
					height = getNonNegativeIntAfterPrefix(lastLine, heightPrefix);
					listener.gotHeight(height);
				}
				if (lastLine.startsWith(depthPrefix)) {
					depth = getNonNegativeIntAfterPrefix(lastLine, depthPrefix);
					listener.gotDepth(depth);
				}
				if (lastLine.startsWith(spacingXPrefix)) {
					spacing_x = getNonNegativeFloatAfterPrefix(lastLine, spacingXPrefix);
					listener.gotSpacingX(spacing_x);
				}
				if (lastLine.startsWith(spacingYPrefix)) {
					spacing_y = getNonNegativeFloatAfterPrefix(lastLine, spacingYPrefix);
					listener.gotSpacingY(spacing_y);
				}
				if (lastLine.startsWith(spacingZPrefix)) {
					spacing_z = getNonNegativeFloatAfterPrefix(lastLine, spacingZPrefix);
					listener.gotSpacingZ(spacing_z);
				}

				if (lastLine.startsWith(vertexPrefix)) {

					final StringTokenizer tokenizer = new StringTokenizer(lastLine.substring(vertexPrefix.length()),
							" ");

					final float[] vertex = new float[3];

					int i = 0;
					while (tokenizer.hasMoreTokens()) {
						vertex[i] = Float.parseFloat(tokenizer.nextToken());
						++i;
					}

					final int x = Math.round(vertex[0] / spacing_x);
					final int y = Math.round(vertex[1] / spacing_y);
					final int z = Math.round(vertex[2] / spacing_z);

					listener.gotVertex(vertexIndex, vertex[0], vertex[1], vertex[2], x, y, z);

				}

				if (lastLine.startsWith(linePrefix)) {
					final StringTokenizer tokenizer = new StringTokenizer(lastLine.substring(linePrefix.length()), " ");
					final int[] vertexIndices = new int[2];

					int i = 0;
					while (tokenizer.hasMoreTokens()) {
						vertexIndices[i] = Integer.parseInt(tokenizer.nextToken());
						++i;
					}

					listener.gotLine(vertexIndices[0], vertexIndices[1]);
				}

				if (lastLine.startsWith(groupPrefix))
					; // Do nothing
			}

		} catch (final IOException e) {
			IJ.error("IOException loading " + inputFilename + ": " + e);
			return false;
		}

		return true;
	}

	void writeXML(final String outputFilename) throws IOException {

		PrintWriter pw = null;

		final boolean compress = false;

		try {
			if (compress) {
				System.out.println("Creating, with compression...");
				pw = new PrintWriter(
						new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outputFilename)), "UTF-8"));
				System.out.println("Created, pw is: " + pw);
			} else {
				System.out.println("Creating, without compression...");
				pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF-8"));
				System.out.println("Created, pw is: " + pw);
			}

			pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			pw.println("<!DOCTYPE connections [");
			pw.println("  <!ELEMENT tracings       (samplespacing,imagesize,path*,fill*)>");
			pw.println("  <!ELEMENT imagesize      EMPTY>");
			pw.println("  <!ELEMENT samplespacing  EMPTY>");
			pw.println("  <!ELEMENT path           (point+)>");
			pw.println("  <!ELEMENT point          EMPTY>");
			pw.println("  <!ELEMENT fill           (node*)>");
			pw.println("  <!ELEMENT node           EMPTY>");
			pw.println("  <!ATTLIST samplespacing  x             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST samplespacing  y             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST samplespacing  z             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST samplespacing  units         CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST imagesize      width         CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST imagesize      height        CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST imagesize      depth         CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST path           id            CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST path           startson      CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           startsindex   CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           endson        CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           endsindex     CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           reallength    CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST point          x             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST point          y             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST point          z             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST fill           id            CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST fill           frompaths     CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST fill           metric        CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST fill           threshold     CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           id            CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           x             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           y             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           z             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           previousid    CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST node           distance      CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           status        (open|closed)   #REQUIRED>");
			pw.println("]>");
			pw.println("");

			pw.println("<tracings>");

			// FIXME: implement

			pw.println("</tracings>");

		} finally {
			pw.close();
		}
	}
}
