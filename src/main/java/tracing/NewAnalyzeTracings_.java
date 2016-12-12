/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package tracing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.PriorityQueue;

import amira.AmiraParameters;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import landmarks.Bookstein_From_Landmarks;
import util.BatchOpener;
import util.FileAndChannel;
import vib.oldregistration.RegistrationAlgorithm;

class ImagesFromLine {

	String lineName;
	ArrayList<String> baseNames = new ArrayList<>();

}

public class NewAnalyzeTracings_ implements PlugIn, TraceLoaderListener {

	int[] labelIndices = { 7, // mushroom_body_r
			8, // mushroom_body_l
			9, // ellipsoid_body
			11, // noduli
			10, // fan_shaped_body
			12, // protocerebral_bridge
			15, // antennal_lobe_r
			16 }; // antennal_lobe_l

	class NewGraphNode implements Comparable {
		public NewGraphNode() {
		}

		public NewGraphNode(final int x, final int y, final int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		int x = -1;
		int y = -1;
		int z = -1;
		NewGraphNode linkedTo[] = null;

		@Override
		public boolean equals(final Object other) {
			final NewGraphNode o = (NewGraphNode) other;
			return x == o.x && y == o.y && z == o.z;
		}

		@Override
		public int hashCode() {
			return x + y * (1 << 11) + z * (1 << 22);
		}

		@Override
		public String toString() {
			return "(" + x + "," + y + "," + z + ")";
		}

		// These members are only used for the search:
		float g = Float.MIN_VALUE;
		float h = Float.MIN_VALUE;
		NewGraphNode previous = null;

		public float distanceTo(final NewGraphNode o) {
			final float xdiff = (x - o.x) * spacing_x;
			final float ydiff = (y - o.y) * spacing_y;
			final float zdiff = (z - o.z) * spacing_y;
			final float distSq = xdiff * xdiff + ydiff * ydiff + zdiff * zdiff;
			return (float) Math.sqrt(distSq);
		}

		public void setFrom(final NewGraphNode o) {
			this.x = o.x;
			this.y = o.y;
			this.z = o.z;
			this.linkedTo = o.linkedTo;
		}

		double f() {
			return g + h;
		}

		// @Override
		@Override
		public int compareTo(final Object other) {
			final NewGraphNode n = (NewGraphNode) other;
			return Double.compare(f(), n.f());
		}
	}

	ArrayList<NewGraphNode> makePath(final NewGraphNode lastNode) {

		// SNT.log( "Trying to return result" );

		final ArrayList<NewGraphNode> resultReversed = new ArrayList<>();
		NewGraphNode p = lastNode;
		do {
			resultReversed.add(p);
			// SNT.log( "adding "+p.toDotName());
		} while (null != (p = p.previous));

		final ArrayList<NewGraphNode> realResult = new ArrayList<>();

		for (int i = resultReversed.size() - 1; i >= 0; --i)
			realResult.add(resultReversed.get(i));

		return realResult;
	}

	class PathWithLength {

		int startNeuropilRegion;
		int endNeuropilRegion;

		public double length;
		public ArrayList<NewGraphNode> path;

		public Path toPath() {
			final Path p = new Path(spacing_x, spacing_y, spacing_z,
					"" /* FIXME: get the real spacing_units */, path.size());
			for (int i = 0; i < path.size(); ++i) {
				final NewGraphNode n = path.get(i);
				p.addPointDouble(n.x * spacing_x, n.y * spacing_y, n.z * spacing_z);
			}
			return p;
		}
	}

	PathWithLength findPath(final NewGraphNode start, final int endMaterial) {

		// SNT.log("Starting path finding:");

		// First reset all the search parameters:
		{
			final Collection<NewGraphNode> c = positionToNode.values();
			for (final NewGraphNode n : c) {
				n.g = 0;
				n.h = 0;
				n.previous = null;
			}
		}

		final PriorityQueue<NewGraphNode> closed_from_start = new PriorityQueue<>();
		final PriorityQueue<NewGraphNode> open_from_start = new PriorityQueue<>();

		final Hashtable<NewGraphNode, NewGraphNode> open_from_start_hash = new Hashtable<>();
		final Hashtable<NewGraphNode, NewGraphNode> closed_from_start_hash = new Hashtable<>();

		start.g = 0;
		start.h = 0;
		start.previous = null;

		// add_node( open_from_start, open_from_start_hash, start );
		open_from_start.add(start);
		open_from_start_hash.put(start, start);

		while (open_from_start.size() > 0) {

			// NewGraphNode p = get_highest_priority( open_from_start,
			// open_from_start_hash );

			// SNT.log("Before poll:
			// "+open_from_start_hash.size()+"/"+open_from_start.size());
			final NewGraphNode p = open_from_start.poll();
			open_from_start_hash.remove(p);
			// SNT.log("After poll:
			// "+open_from_start_hash.size()+"/"+open_from_start.size());

			// SNT.log( " Got node "+p.toDotName()+" from the queue"
			// );

			final int pointMaterial = label_data[p.z][p.y * width + p.x];

			// Has the route from the start found the goal?

			if (pointMaterial == endMaterial) {
				// SNT.log( "Found the goal! (from start to end)" );
				final ArrayList<NewGraphNode> path = makePath(p);
				if (path == null)
					return null;
				else {
					final PathWithLength result = new PathWithLength();
					result.path = path;
					result.length = p.g;
					return result;
				}
			}

			// add_node( closed_from_start, closed_from_start_hash, p );
			closed_from_start.add(p);
			closed_from_start_hash.put(p, p);

			// Now look at all the neighbours...

			// SNT.log("linkedTo "+p.linkedTo.length+" neigbours");
			for (int i = 0; i < p.linkedTo.length; ++i) {

				final NewGraphNode neighbour = p.linkedTo[i];
				final float distance = p.distanceTo(neighbour);
				if (neighbour.z == 118 || (neighbour.y * width + neighbour.x) == 118)
					SNT.log("neighbour is: (" + neighbour.x + "," + neighbour.y + "," + neighbour.z
							+ " and width " + width + " height " + height + " depth " + depth);
				final int neighbourMaterial = label_data[neighbour.z][neighbour.y * width + neighbour.x];

				// Ignore this neighbour if it's it's not of the exterior or end
				// material

				if (!(neighbourMaterial == 0 || neighbourMaterial == endMaterial))
					continue;

				final NewGraphNode newNode = new NewGraphNode();
				newNode.setFrom(neighbour);
				newNode.g = p.g + distance;
				newNode.h = 0;
				newNode.previous = p;

				final NewGraphNode foundInClosed = closed_from_start_hash.get(neighbour);

				final NewGraphNode foundInOpen = open_from_start_hash.get(neighbour);

				// Is there an exisiting route which is
				// better? If so, discard this new candidate...

				if ((foundInClosed != null) && (foundInClosed.f() <= newNode.f())) {
					// SNT.log( " Found in closed, but no better.");
					continue;
				}

				if ((foundInOpen != null) && (foundInOpen.f() <= newNode.f())) {
					// SNT.log( " Found in open, but no better.");
					continue;
				}

				if (foundInClosed != null) {

					// SNT.log("Found in closed and better");

					// remove( closed_from_start, closed_from_start_hash,
					// foundInClosed );
					closed_from_start.remove(foundInClosed);
					closed_from_start_hash.remove(foundInClosed);

					foundInClosed.setFrom(newNode);

					// add_node( open_from_start, open_from_start_hash,
					// foundInClosed );
					open_from_start.add(foundInClosed);
					open_from_start_hash.put(foundInClosed, foundInClosed);

					continue;
				}

				if (foundInOpen != null) {

					// SNT.log("Found in open and better");

					// remove( open_from_start, open_from_start_hash,
					// foundInOpen );
					open_from_start.remove(foundInOpen);
					open_from_start_hash.remove(foundInOpen);

					foundInOpen.setFrom(newNode);

					// add_node( open_from_start, open_from_start_hash,
					// foundInOpen );
					open_from_start.add(foundInOpen);
					open_from_start_hash.put(foundInOpen, foundInOpen);

					continue;
				}

				// Otherwise we add a new node:

				// SNT.log(" Adding new node to open " +
				// newNode.toDotName() );

				// add_node( open_from_start, open_from_start_hash, newNode );
				open_from_start.add(newNode);
				open_from_start_hash.put(newNode, newNode);
			}
		}

		/*
		 * If we get to here then we haven't found a route to the end point.
		 */

		return null;
	}

	public int positionToKey(final int x, final int y, final int z) {
		return x + y * width + z * width * height;
	}

	int width = -1, height = -1, depth = -1;
	float spacing_x = Float.MIN_VALUE;
	float spacing_y = Float.MIN_VALUE;
	float spacing_z = Float.MIN_VALUE;
	String spacing_units = "";

	ArrayList<NewGraphNode> verticesInObjOrder;
	Hashtable<Integer, NewGraphNode> positionToNode;
	int numberOfVertices = -1;
	ArrayList<ArrayList<NewGraphNode>> links;

	@Override
	public void gotVertex(final int vertexIndex, final float x_scaled, final float y_scaled, final float z_scaled,
			final int x_image, final int y_image, final int z_image) {

		if (width < 0 || height < 0 || depth < 0 || spacing_x == Float.MIN_VALUE || spacing_y == Float.MIN_VALUE
				|| spacing_z == Float.MIN_VALUE) {

			throw new RuntimeException("Some metadata was missing from the comments before the first vertex.");
		}

		if (z_image >= depth) {
			SNT.log("z is too deep:");
			SNT.log("x_scaled: " + x_scaled);
			SNT.log("y_scaled: " + y_scaled);
			SNT.log("z_scaled: " + z_scaled);
			SNT.log("x_image: " + x_image);
			SNT.log("y_image: " + y_image);
			SNT.log("z_image: " + z_image);
			SNT.log("spacing_x: " + spacing_x);
			SNT.log("spacing_y: " + spacing_y);
			SNT.log("spacing_z: " + spacing_z);
		}

		verticesInObjOrder.add(new NewGraphNode(x_image, y_image, z_image));
	}

	@Override
	public void gotLine(final int fromVertexIndex, final int toVertexIndex) {
		if (links == null) {
			numberOfVertices = verticesInObjOrder.size() - 1;
			links = new ArrayList<>(numberOfVertices);
			for (int i = 0; i <= numberOfVertices; ++i)
				links.add(new ArrayList<NewGraphNode>());
		}

		final ArrayList<NewGraphNode> fromLinks = links.get(fromVertexIndex);
		final ArrayList<NewGraphNode> toLinks = links.get(toVertexIndex);

		final NewGraphNode toVertex = verticesInObjOrder.get(toVertexIndex);
		final NewGraphNode fromVertex = verticesInObjOrder.get(fromVertexIndex);

		if (!fromLinks.contains(toVertex))
			fromLinks.add(toVertex);

		if (!toLinks.contains(fromVertex))
			toLinks.add(fromVertex);
	}

	@Override
	public void gotWidth(final int width) {
		this.width = width;
	}

	@Override
	public void gotHeight(final int height) {
		this.height = height;
	}

	@Override
	public void gotDepth(final int depth) {
		this.depth = depth;
	}

	@Override
	public void gotSpacingX(final float spacing_x) {
		this.spacing_x = spacing_x;
	}

	@Override
	public void gotSpacingY(final float spacing_y) {
		this.spacing_y = spacing_y;
	}

	@Override
	public void gotSpacingZ(final float spacing_z) {
		this.spacing_z = spacing_z;
	}

	// FIXME: call from somewhere to set:
	public void gotSpacingUnits(final String spacing_units) {
		this.spacing_units = spacing_units;
	}

	byte[][] label_data;
	byte[][] registered_label_data;
	String[] materialNames;

	int[] redValues;
	int[] greenValues;
	int[] blueValues;

	int materials;
	AmiraParameters parameters;

	public byte[][] loadRegisteredLabels(final String basename) {

		final String originalImageFileName = basename + ".lsm";

		final ImagePlus labels = BatchOpener.openFirstChannel(standardBrainLabelsFileName);

		final ImageStack labelStack = labels.getStack();
		final int templateWidth = labelStack.getWidth();
		final int templateHeight = labelStack.getHeight();
		final int templateDepth = labelStack.getSize();
		final byte[][] new_label_data = new byte[templateDepth][];
		for (int z = 0; z < templateDepth; ++z)
			new_label_data[z] = (byte[]) labelStack.getPixels(z + 1);

		parameters = new AmiraParameters(labels);
		materials = parameters.getMaterialCount();
		materialNames = new String[256];
		materialNameToIndex = new Hashtable<>();
		for (int i = 0; i < materials; ++i) {
			materialNames[i] = parameters.getMaterialName(i);
			materialNameToIndex.put(materialNames[i], new Integer(i));
			SNT.log("Material: " + i + " is " + materialNames[i]);
		}

		redValues = new int[materials];
		greenValues = new int[materials];
		blueValues = new int[materials];

		for (int i = 0; i < materials; i++) {

			final double[] c = parameters.getMaterialColor(i);

			redValues[i] = (int) (255 * c[0]);
			greenValues[i] = (int) (255 * c[1]);
			blueValues[i] = (int) (255 * c[2]);
		}

		byte[][] transformed_label_data = null;

		final String registeredLabelFileName = basename + ".registered.labels";

		final File registeredLabelFile = new File(registeredLabelFileName);
		if (registeredLabelFile.exists()) {

			// Then just load it:

			final ImagePlus registeredLabels = BatchOpener.openFirstChannel(registeredLabelFileName);
			final ImageStack tmpStack = registeredLabels.getStack();

			transformed_label_data = new byte[registeredLabels.getStackSize()][];

			for (int z = 0; z < registeredLabels.getStackSize(); ++z) {
				transformed_label_data[z] = (byte[]) tmpStack.getPixels(z + 1);
			}

			labels.close();

		} else {

			// We have to calculate the registration:

			final FileAndChannel fc = new FileAndChannel(originalImageFileName, 0);
			final FileAndChannel standardBrainFC = new FileAndChannel(standardBrainFileName, 0);

			final Bookstein_From_Landmarks matcher = new Bookstein_From_Landmarks();
			matcher.loadImages(standardBrainFC, fc);
			matcher.generateTransformation();

			transformed_label_data = new byte[depth][width * height];
			final RegistrationAlgorithm.ImagePoint imagePoint = new RegistrationAlgorithm.ImagePoint();

			for (int z = 0; z < depth; ++z) {
				SNT.log("doing slice: " + z);
				for (int y = 0; y < height; ++y) {
					for (int x = 0; x < width; ++x) {

						matcher.transformDomainToTemplate(x, y, z, imagePoint);

						final int x_in_template = imagePoint.x;
						final int y_in_template = imagePoint.y;
						final int z_in_template = imagePoint.z;

						int label_value = 0;

						if (z_in_template >= 0 && z_in_template < templateDepth && y_in_template >= 0
								&& y_in_template < templateHeight && x_in_template >= 0
								&& x_in_template < templateWidth) {

							label_value = new_label_data[z_in_template][y_in_template * templateWidth + x_in_template]
									& 0xFF;
						}

						transformed_label_data[z][y * width + x] = (byte) label_value;

						if (label_value >= materials) {
							throw new RuntimeException("A label value of " + label_value
									+ " was found, which is not a valid material (max " + (materials - 1) + ")");
						}
					}
				}
			}

			final ImageStack stack = new ImageStack(width, height);

			for (int z = 0; z < depth; ++z) {
				final ByteProcessor bp = new ByteProcessor(width, height);
				bp.setPixels(transformed_label_data[z]);
				stack.addSlice("", bp);
			}

			final ImagePlus t = new ImagePlus("transformed labels", stack);
			t.getProcessor().setColorModel(labels.getProcessor().getColorModel());
			final boolean saved = new FileSaver(t).saveAsTiffStack(registeredLabelFileName);
			if (!saved) {
				throw new RuntimeException("Failed to save registered labels to: " + registeredLabelFileName);
			}
		}

		labels.close();

		return transformed_label_data;
	}

	public ArrayList<PathWithLength> buildGraph(final File tracesObjFile, final File labelsFile,
			final File writePathsTo, final File writeDotTo) {

		final boolean usePointRegisteredLabels = true;

		final String tracesObjFileName = tracesObjFile.getAbsolutePath();
		final String labelsFileName = labelsFile.getAbsolutePath();

		/* First load the traces file: */

		// The indices in the .obj begin at 1, so put in a dummy node at 0:

		verticesInObjOrder = new ArrayList<>();
		verticesInObjOrder.add(new NewGraphNode());

		SNT.log("Loading traces file: " + tracesObjFileName);

		final boolean success = SinglePathsGraph.loadWithListener(tracesObjFileName, this);

		if (!success) {
			throw new RuntimeException("Failed to load traces");
		}

		SNT.log("Finished loading: " + (verticesInObjOrder.size() - 1) + " vertices found");

		SNT.log("  traces width:" + width);
		SNT.log("  traces height:" + height);
		SNT.log("  traces depth:" + depth);

		long linksBothWays = 0;
		for (int i = 1; i < verticesInObjOrder.size(); ++i) {
			final NewGraphNode v = verticesInObjOrder.get(i);
			final ArrayList<NewGraphNode> linkedTo = links.get(i);
			final int l = linkedTo.size();
			linksBothWays += l;
			final NewGraphNode[] a = new NewGraphNode[l];
			v.linkedTo = linkedTo.toArray(a);
		}

		links = null;

		SNT.log("And set the links in the NewGraphNodes: " + linksBothWays);

		positionToNode = new Hashtable<>();

		// Now we want to index by position rather than vertex index:
		boolean first = true;
		for (final NewGraphNode n : verticesInObjOrder) {
			if (first) {
				first = false;
				continue;
			}
			final int k = positionToKey(n.x, n.y, n.z);
			positionToNode.put(k, n);
		}

		verticesInObjOrder = null;

		SNT.log("Added vertices to the hash, now has: " + positionToNode.size() + " entries");

		/* And now the real labels file: */

		final ImagePlus labels = BatchOpener.openFirstChannel(labelsFileName);
		if (labels == null)
			throw new RuntimeException("Couldn't open labels file " + labelsFileName);
		final ImageStack labelStack = labels.getStack();

		SNT.log("label file width:" + labels.getWidth());
		SNT.log("label file height:" + labels.getHeight());
		SNT.log("label file depth:" + labels.getStackSize());

		label_data = new byte[depth][];
		for (int z = 0; z < depth; ++z)
			label_data[z] = (byte[]) labelStack.getPixels(z + 1);

		if (usePointRegisteredLabels) {

			/*
			 * Now load the registered labels file. This registration will be
			 * very approximate, so take this with a pinch of salt...
			 */

			final String basename = labelsFileName.substring(0, labelsFileName.lastIndexOf("."));

			registered_label_data = loadRegisteredLabels(basename);

			/*
			 * For the moment, only include the mushroom bodies (7 & 8) and the
			 * antennal lobes (15 & 16)
			 */

			for (int z = 0; z < depth; ++z) {
				for (int y = 0; y < height; ++y) {
					for (int x = 0; x < width; ++x) {

						// Don't copy over anything if there's a real value
						// here:
						if (label_data[z][y * width + x] == 0) {
							final byte registered_value = registered_label_data[z][y * width + x];
							if (registered_value == 7 || registered_value == 8 || registered_value == 15
									|| registered_value == 16) {
								label_data[z][y * width + x] = registered_label_data[z][y * width + x];
							}
						}
					}
				}
			}

		} else {

			parameters = new AmiraParameters(labels);
			materials = parameters.getMaterialCount();
			materialNames = new String[256];
			materialNameToIndex = new Hashtable<>();
			for (int i = 0; i < materials; ++i) {
				materialNames[i] = parameters.getMaterialName(i);
				materialNameToIndex.put(materialNames[i], new Integer(i));
				SNT.log("Material: " + i + " is " + materialNames[i]);
			}

			redValues = new int[materials];
			greenValues = new int[materials];
			blueValues = new int[materials];

		}

		final ArrayList<ArrayList<NewGraphNode>> allEdges = new ArrayList<>();

		for (int i = 0; i < materials; i++) {
			allEdges.add(new ArrayList<NewGraphNode>());
			final double[] c = parameters.getMaterialColor(i);
			redValues[i] = (int) (255 * c[0]);
			greenValues[i] = (int) (255 * c[1]);
			blueValues[i] = (int) (255 * c[2]);
		}

		/* Find all the points on the edge of a neuropil regions: */

		for (int a = 0; a < labelIndices.length; ++a) {
			final int labelIndex = labelIndices[a];
			final String labelName = materialNames[labelIndex];

			SNT.log("   Dealing with label index " + labelIndex + ", name: " + labelName);

			final ArrayList<NewGraphNode> neuropilEdgePoints = allEdges.get(labelIndex);

			for (int z = 0; z < depth; ++z)
				for (int y = 0; y < height; ++y)
					for (int x = 0; x < width; ++x) {
						if (label_data[z][y * width + x] != labelIndex)
							continue;
						final int k = positionToKey(x, y, z);
						final NewGraphNode n = positionToNode.get(k);
						if (n == null)
							continue;
						/*
						 * So now we have a traced point in the right neuropil
						 * region. We only care about edge points, though, so
						 * check that it has a neighbour that's in the exterior.
						 */
						final NewGraphNode[] linkedNodes = n.linkedTo;
						for (int i = 0; i < linkedNodes.length; ++i) {
							final NewGraphNode l = linkedNodes[i];
							if (label_data[l.z][l.y * width + l.x] == 0) {
								neuropilEdgePoints.add(l);
								break;
							}
						}
					}

			SNT.log("   Found " + neuropilEdgePoints.size() + " points on the edge of the " + labelName);
		}

		// We'll store copies of these in a PathAndFillManager
		// so that we can write out something that will be
		// loadable by the manual tracer afterwards:

		final PathAndFillManager manager = new PathAndFillManager(width, height, depth, spacing_x, spacing_y, spacing_z,
				null);

		final ArrayList<PathWithLength> paths = new ArrayList<>();

		// Now start a search from each of these points trying
		// to find an end point at one of the edge points from
		// the other neuropil regions:

		for (int a = 0; a < labelIndices.length; ++a) {

			final int labelIndex = labelIndices[a];
			final String labelName = materialNames[labelIndex];
			SNT.log("Starting searches from " + labelIndex + ", name: " + labelName);

			final ArrayList<NewGraphNode> startPoints = allEdges.get(labelIndex);

			for (int endM = labelIndex + 1; endM < materials; ++endM) {

				final ArrayList<NewGraphNode> potentialEndPoints = allEdges.get(endM);
				if (potentialEndPoints.size() == 0)
					continue;

				for (final NewGraphNode startPoint : startPoints) {

					SNT.log("  Starting from point " + startPoint + " (" + labelName
							+ " looking for material: " + materialNames[endM]);

					final PathWithLength route = findPath(startPoint, endM);
					if (route == null) {
						// SNT.log("No route found.");
						continue;
					}

					route.startNeuropilRegion = labelIndex;
					route.endNeuropilRegion = endM;

					paths.add(route);
					final Path newPath = route.toPath();
					newPath.setName(materialNames[labelIndex] + " to " + materialNames[endM]);
					manager.addPath(newPath);

					// Add that path to the fill manager as well:

					SNT.log("  Found a route!");
				}
			}
		}

		if (writePathsTo != null) {
			try {
				manager.writeXML(writePathsTo.getAbsolutePath(), true);
			} catch (final IOException e) {
				SNT.log("Writing to: " + writePathsTo + " failed");
			}
		}

		if (writeDotTo != null) {
			try {
				final BufferedWriter out = new BufferedWriter(new FileWriter(writeDotTo.getAbsolutePath(), false));

				out.write("graph G {\n");
				out.write("        graph [overlap=scale,splines=true];\n");
				out.write("        node [fontname=\"DejaVuSans\",style=filled];\n");

				final HashSet<Integer> materialsInGraph = new HashSet<>();
				for (final PathWithLength p : paths) {
					materialsInGraph.add(p.startNeuropilRegion);
					materialsInGraph.add(p.endNeuropilRegion);
				}

				for (final int m : materialsInGraph) {
					final String name = materialNames[m];
					out.write("        \"" + name + "\" [fillcolor=\"" + colorString(name) + "\"];\n");
				}

				final HashSet<String> connectionsDone = new HashSet<>();

				for (final PathWithLength p : paths) {
					final String dotLine = "        \"" + materialNames[p.startNeuropilRegion] + "\" -- \""
							+ materialNames[p.endNeuropilRegion] + "\";\n";
					if (!connectionsDone.contains(dotLine)) {
						out.write(dotLine);
						connectionsDone.add(dotLine);
					}
				}
				out.write("}");
				out.close();

			} catch (final IOException ioe) {
				IJ.error("Exception while writing the file");
			}
		}

		return paths;
	}

	String standardBrainFileName = "/home/mark/arnim-brain/CantonF41c.grey";
	String standardBrainLabelsFileName = "/home/mark/arnim-brain/CantonF41c.labels";

	@Override
	public void run(final String argument) {

		final String baseDirectory = "/media/WD Passport/corpus/central-complex/";
		// String baseDirectory = "/media/WD USB 2/corpus/central-complex/";
		// String baseDirectory = "/home/mark/tmp-corpus/";
		final ArrayList<ImagesFromLine> lines = new ArrayList<>();

		final ImagesFromLine linec5 = new ImagesFromLine();
		linec5.lineName = "c005";
		linec5.baseNames.add("c005BA");
		linec5.baseNames.add("c005BB");
		linec5.baseNames.add("c005BC");
		linec5.baseNames.add("c005BE");
		linec5.baseNames.add("c005BF");
		linec5.baseNames.add("c5xUAS-CD8GFP-40x-central-complex-BF");
		linec5.baseNames.add("c5xUAS-lacZ-40x-cc-BB");
		linec5.baseNames.add("c5xUAS-lacZ-40x-cc-BC");
		lines.add(linec5);

		final ImagesFromLine line210y = new ImagesFromLine();
		line210y.lineName = "210y";
		line210y.baseNames.add("210y-40x-central-complex-CA");
		line210y.baseNames.add("210y-40x-central-complex-CB");
		line210y.baseNames.add("210y-40x-central-complex-CD");
		line210y.baseNames.add("210y-40x-central-complex-CE");
		line210y.baseNames.add("210yAC");
		line210y.baseNames.add("210yAD");
		line210y.baseNames.add("210yAE");
		line210y.baseNames.add("210yAO");
		line210y.baseNames.add("210yAP");
		lines.add(line210y);

		final ImagesFromLine line71y = new ImagesFromLine();
		line71y.lineName = "71y";
		line71y.baseNames.add("71yAAeastmost");
		line71y.baseNames.add("71yABwestmost");
		line71y.baseNames.add("71yAF");
		line71y.baseNames.add("71yAM");
		line71y.baseNames.add("71yAN");
		line71y.baseNames.add("71yAQ");
		line71y.baseNames.add("71yAR");
		lines.add(line71y);

		final ImagesFromLine linec61 = new ImagesFromLine();
		linec61.lineName = "c61";
		linec61.baseNames.add("c061AG");
		linec61.baseNames.add("c061AH");
		linec61.baseNames.add("c061AI()");
		linec61.baseNames.add("c061AK");
		linec61.baseNames.add("c061AL");
		linec61.baseNames.add("c061AU");
		lines.add(linec61);

		for (final ImagesFromLine line : lines) {

			SNT.log("Looking at line: " + line.lineName);

			for (final String baseName : line.baseNames) {

				SNT.log("  Image basename: " + baseName);

				final File lsmFile = new File(baseDirectory, baseName + ".lsm");
				/*
				 * if( ! lsmFile.exists() ) continue;
				 */

				final File tracesObjFile = new File(baseDirectory, baseName + ".traces.obj");
				if (!lsmFile.exists())
					continue;

				final File labelsFile = new File(baseDirectory, baseName + ".labels");
				if (!lsmFile.exists())
					continue;

				SNT.log("!!!");

				// Load labels and traces.obj ...

				final ArrayList<PathWithLength> foundPaths = buildGraph(tracesObjFile, labelsFile,
						new File(baseDirectory, baseName + ".neuropil-connections.traces"),
						new File(baseDirectory, baseName + ".dot"));
			}
		}
	}

	public Hashtable<String, Integer> materialNameToIndex;

	public String colorString(final String materialName) {

		if (materialName.equals("Exterior"))
			return "#DDDDDD";
		else {

			final Integer material_id_integer = materialNameToIndex.get(materialName);
			final int material_id = material_id_integer.intValue();

			final double scaling = 1.4;

			int r = (int) (redValues[material_id] * scaling);
			int g = (int) (greenValues[material_id] * scaling);
			int b = (int) (blueValues[material_id] * scaling);

			if (r > 255)
				r = 255;
			if (g > 255)
				g = 255;
			if (b > 255)
				b = 255;

			String redValueString = Integer.toHexString(r);
			if (redValueString.length() <= 1)
				redValueString = "0" + redValueString;

			String greenValueString = Integer.toHexString(g);
			if (greenValueString.length() <= 1)
				greenValueString = "0" + greenValueString;

			String blueValueString = Integer.toHexString(b);
			if (blueValueString.length() <= 1)
				blueValueString = "0" + blueValueString;

			return "#" + redValueString + greenValueString + blueValueString;
		}
	}

}
