/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2006, 2007, 2008, 2009, 2010, 2011 Mark Longair */

/*
  This file is part of the ImageJ plugin "Simple Neurite Tracer".

  The ImageJ plugin "Simple Neurite Tracer" is free software; you
  can redistribute it and/or modify it under the terms of the GNU
  General Public License as published by the Free Software
  Foundation; either version 3 of the License, or (at your option)
  any later version.

  The ImageJ plugin "Simple Neurite Tracer" is distributed in the
  hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE.  See the GNU General Public License for more
  details.

  In addition, as a special exception, the copyright holders give
  you permission to combine this program with free software programs or
  libraries that are released under the Apache Public License.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package tracing;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.scijava.java3d.View;
import org.scijava.vecmath.Color3f;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij3d.Content;
import ij3d.UniverseListener;
import util.Bresenham3D;
import util.XMLFunctions;

@SuppressWarnings("serial")
class TracesFileFormatException extends SAXException {
	public TracesFileFormatException(final String message) {
		super(message);
	}
}

@SuppressWarnings("serial")
class SWCExportException extends Exception {
	public SWCExportException(final String message) {
		super(message);
	}
}

public class PathAndFillManager extends DefaultHandler implements UniverseListener {

	static final boolean verbose = SimpleNeuriteTracer.verbose;

	SimpleNeuriteTracer plugin;
	ImagePlus imagePlus;

	int maxUsedID = -1;

	boolean needImageDataFromTracesFile;

	public PathAndFillManager() {
		allPaths = new ArrayList<>();
		allFills = new ArrayList<>();
		listeners = new ArrayList<>();
		selectedPathsSet = new HashSet<>();
		needImageDataFromTracesFile = true;
		this.imagePlus = null;
		this.x_spacing = Double.MIN_VALUE;
		this.y_spacing = Double.MIN_VALUE;
		this.z_spacing = Double.MIN_VALUE;
		this.spacing_units = null;
		this.width = Integer.MIN_VALUE;
		this.height = Integer.MIN_VALUE;
		this.depth = Integer.MIN_VALUE;
	}

	public PathAndFillManager(final ImagePlus imagePlus) {
		this();
		this.imagePlus = imagePlus;
		final Calibration c = imagePlus.getCalibration();
		this.x_spacing = c.pixelWidth;
		this.y_spacing = c.pixelHeight;
		this.z_spacing = c.pixelDepth;
		this.spacing_units = c.getUnit();
		if (this.spacing_units == null || this.spacing_units.length() == 0)
			this.spacing_units = "" + c.getUnit();
		this.width = imagePlus.getWidth();
		this.height = imagePlus.getHeight();
		this.depth = imagePlus.getStackSize();
		needImageDataFromTracesFile = false;
	}

	public PathAndFillManager(final SimpleNeuriteTracer plugin) {
		this();
		this.plugin = plugin;
		this.x_spacing = plugin.x_spacing;
		this.y_spacing = plugin.y_spacing;
		this.z_spacing = plugin.z_spacing;
		this.spacing_units = plugin.spacing_units;
		this.width = plugin.width;
		this.height = plugin.height;
		this.depth = plugin.depth;
		needImageDataFromTracesFile = false;
	}

	public PathAndFillManager(final int width, final int height, final int depth, final float x_spacing,
			final float y_spacing, final float z_spacing, final String spacing_units) {
		this();
		this.x_spacing = x_spacing;
		this.y_spacing = y_spacing;
		this.z_spacing = z_spacing;
		this.width = width;
		this.height = height;
		this.depth = depth;
		if (spacing_units == null)
			this.spacing_units = "unknown";
		needImageDataFromTracesFile = false;
	}

	int width;
	int height;
	int depth;

	double x_spacing;
	double y_spacing;
	double z_spacing;
	String spacing_units;

	ArrayList<Path> allPaths;
	ArrayList<Fill> allFills;

	ArrayList<PathAndFillListener> listeners;

	HashSet<Path> selectedPathsSet;

	public int size() {
		return allPaths.size();
	}

	/*
	 * This is used by the interface to have changes in the path manager
	 * reported so that they can be reflected in the UI.
	 */

	public synchronized void addPathAndFillListener(final PathAndFillListener listener) {
		listeners.add(listener);
	}

	public synchronized Path getPath(final int i) {
		return allPaths.get(i);
	}

	public synchronized Path getPathFromName(final String name) {
		return getPathFromName(name, true);
	}

	public synchronized Path getPathFromName(final String name, final boolean caseSensitive) {
		for (final Path p : allPaths) {
			if (caseSensitive) {
				if (name.equals(p.getName()))
					return p;
			} else {
				if (name.equalsIgnoreCase(p.getName()))
					return p;
			}
		}
		return null;
	}

	public synchronized Path getPathFrom3DViewerName(final String name) {
		for (final Path p : allPaths) {
			if (p.nameWhenAddedToViewer == null)
				continue;
			if (name.equals(p.nameWhenAddedToViewer))
				return p;
		}
		return null;
	}

	public synchronized Path getPathFromID(final int id) {
		for (final Path p : allPaths) {
			if (id == p.getID()) {
				return p;
			}
		}
		return null;
	}

	/*
	 * This is called to update the PathAndFillManager's idea of which paths are
	 * currently selected. This is also propagated to:
	 *
	 * (a) Each Path object (so that the 3D viewer can reflect the change, for
	 * instance.)
	 *
	 * (b) All the registered PathAndFillListener objects.
	 */
	public synchronized void setSelected(final Path[] selectedPaths, final Object sourceOfMessage) {
		selectedPathsSet.clear();
		for (int i = 0; i < selectedPaths.length; ++i)
			selectedPathsSet.add(selectedPaths[i]);
		for (final PathAndFillListener pafl : listeners) {
			if (pafl != sourceOfMessage)
				// The source of the message already knows the states:
				pafl.setSelectedPaths(selectedPathsSet, this);
		}
		if (plugin != null) {
			plugin.repaintAllPanes();
			plugin.update3DViewerContents();
		}
	}

	public synchronized boolean isSelected(final Path path) {
		return selectedPathsSet.contains(path);
	}

	public boolean anySelected() {
		return selectedPathsSet.size() > 0;
	}

	private static final DecimalFormat fileIndexFormatter = new DecimalFormat("000");

	protected File getSWCFileForIndex(final String prefix, final int index) {
		return new File(prefix + "-" + fileIndexFormatter.format(index) + ".swc");
	}

	public boolean checkOKToWriteAllAsSWC(final String prefix) {
		final List<Path> primaryPaths = Arrays.asList(getPathsStructured());
		final int n = primaryPaths.size();
		String errorMessage = "";
		for (int i = 0; i < n; ++i) {
			final File swcFile = getSWCFileForIndex(prefix, i);
			if (swcFile.exists())
				errorMessage += swcFile.getAbsolutePath() + "\n";
		}
		if (errorMessage.length() == 0)
			return true;
		else {
			errorMessage = "The following files would be overwritten:\n" + errorMessage;
			errorMessage += "Continue to save, overwriting these files?";
			return IJ.showMessageWithCancel("Confirm overwriting SWC files...", errorMessage);
		}
	}

	public synchronized boolean exportAllAsSWC(final String prefix) {
		final List<Path> primaryPaths = Arrays.asList(getPathsStructured());
		int i = 0;
		for (final Path primaryPath : primaryPaths) {
			final File swcFile = getSWCFileForIndex(prefix, i);
			final HashSet<Path> connectedPaths = new HashSet<>();
			final LinkedList<Path> nextPathsToConsider = new LinkedList<>();
			nextPathsToConsider.add(primaryPath);
			while (nextPathsToConsider.size() > 0) {
				final Path currentPath = nextPathsToConsider.removeFirst();
				connectedPaths.add(currentPath);
				for (final Path joinedPath : currentPath.somehowJoins) {
					if (!connectedPaths.contains(joinedPath))
						nextPathsToConsider.add(joinedPath);
				}
			}

			ArrayList<SWCPoint> swcPoints = null;
			try {
				swcPoints = getSWCFor(connectedPaths);
			} catch (final SWCExportException see) {
				SNT.error("" + see.getMessage());
				return false;
			}

			IJ.showStatus("Exporting SWC data to " + swcFile.getAbsolutePath());
			try {
				final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(swcFile), "UTF-8"));
				flushSWCPoints(swcPoints, pw);
			} catch (final IOException ioe) {
				SNT.error("Saving to " + swcFile.getAbsolutePath() + " failed");
				return false;
			}
			++i;
		}
		IJ.showStatus("Export finished.");
		return true;
	}

	protected void flushSWCPoints(final ArrayList<SWCPoint> swcPoints, final PrintWriter pw) {
		pw.println("# Exported from \"Simple Neurite Tracer\" version " + SNT.VERSION + " on "
				+ LocalDateTime.of(LocalDate.now(), LocalTime.now()));
		pw.println("# https://imagej.net/Simple_Neurite_Tracer");
		pw.println("#");
		pw.println("# All positions and radii in " + spacing_units);
		if (usingNonPhysicalUnits())
			pw.println("# WARNING: Usage of pixel coordinates does not respect the SWC specification");
		else
			pw.println("# Voxel separation (x,y,z): " + x_spacing + ", " + y_spacing + ", " + z_spacing);
		pw.println("#");
		for (final SWCPoint p : swcPoints)
			p.println(pw);
		pw.close();
	}

	protected boolean usingNonPhysicalUnits() {
		return (new Calibration().getUnits()).equals(spacing_units)
				|| ("unknown".equals(spacing_units) && x_spacing * y_spacing * z_spacing == 1d);
	}

	/*
	 * This method returns an array of the "primary paths", which should be
	 * displayed at the top of a tree-like hierarchy.
	 *
	 * The paths actually form a graph, of course, but most UIs will want to
	 * display the graph as a tree.
	 */

	public synchronized Path[] getPathsStructured() {

		final ArrayList<Path> primaryPaths = new ArrayList<>();

		/*
		 * Some paths may be explicitly marked as primary, so extract those and
		 * everything connected to them first. If you encounter another path
		 * marked as primary when exploring from these then that's an error...
		 */

		final TreeSet<Path> pathsLeft = new TreeSet<>();

		for (int i = 0; i < allPaths.size(); ++i) {
			final Path p = allPaths.get(i);
			if (!p.isFittedVersionOfAnotherPath())
				pathsLeft.add(allPaths.get(i));
		}

		int markedAsPrimary = 0;

		/*
		 * This is horrendously inefficent but with the number of paths that
		 * anyone might reasonably add by hand (I hope!) it's acceptable.
		 */

		Iterator<Path> pi = pathsLeft.iterator();
		Path primaryPath = null;
		while (pi.hasNext()) {
			final Path p = pi.next();
			if (p.getPrimary()) {
				pi.remove();
				primaryPaths.add(p);
				++markedAsPrimary;
			}
		}

		for (int i = 0; i < primaryPaths.size(); ++i) {
			primaryPath = primaryPaths.get(i);
			primaryPath.setChildren(pathsLeft);
		}

		// Start with each one left that doesn't start on another:
		boolean foundOne = true;
		while (foundOne) {
			foundOne = false;
			pi = pathsLeft.iterator();
			while (pi.hasNext()) {
				final Path p = pi.next();
				if (p.startJoins == null) {
					foundOne = true;
					pi.remove();
					primaryPaths.add(p);
					p.setChildren(pathsLeft);
					break;
				}
			}
		}

		// If there's anything left, start with that:
		while (pathsLeft.size() > 0) {
			pi = pathsLeft.iterator();
			final Path p = pi.next();
			pi.remove();
			primaryPaths.add(p);
			p.setChildren(pathsLeft);
		}

		return primaryPaths.toArray(new Path[] {});
	}

	public synchronized ArrayList<SWCPoint> getSWCFor(final Set<Path> selectedPaths) throws SWCExportException {

		/*
		 * Turn the primary paths into a Set. This call also ensures that the
		 * Path.children and Path.somehowJoins relationships are set up
		 * correctly:
		 */
		final Set<Path> structuredPathSet = new HashSet<>(Arrays.asList(getPathsStructured()));

		/*
		 * Check that there's only one primary path in selectedPaths by taking
		 * the intersection and checking there's exactly one element in it:
		 */

		structuredPathSet.retainAll(selectedPaths);

		if (structuredPathSet.size() == 0)
			throw new SWCExportException(
					"The paths you select for SWC export must include a primary path\n(i.e. one at the top level in the Path Window's tree)");
		if (structuredPathSet.size() > 1)
			throw new SWCExportException("You can only select one connected set of paths for SWC export");

		/*
		 * So now we definitely only have one primary path. All the connected
		 * paths must also be selected, but we'll check that as we go along:
		 */

		final ArrayList<SWCPoint> result = new ArrayList<>();

		int currentPointID = 1;

		/*
		 * nextPathsToAdd is the queue of Paths to add points from, and
		 * pathsAlreadyDone is the set of Paths that have already had their
		 * points added
		 */

		final LinkedList<Path> nextPathsToAdd = new LinkedList<>();
		final Set<Path> pathsAlreadyDone = new HashSet<>();

		final Path firstPath = structuredPathSet.iterator().next();
		if (firstPath.size() == 0)
			throw new SWCExportException("The primary path contained no points!");
		nextPathsToAdd.add(firstPath);

		while (nextPathsToAdd.size() > 0) {

			final Path currentPath = nextPathsToAdd.removeFirst();

			if (!selectedPaths.contains(currentPath))
				throw new SWCExportException("The path \"" + currentPath
						+ "\" is connected to other selected paths, but wasn't itself selected");

			/*
			 * The paths we're dealing with specify connectivity, but we might
			 * be using the fitted versions - take them for the point positions:
			 */

			Path pathToUse = currentPath;
			if (currentPath.getUseFitted()) {
				pathToUse = currentPath.fitted;
			}

			Path parent = null;

			for (final Path possibleParent : currentPath.somehowJoins) {
				if (pathsAlreadyDone.contains(possibleParent)) {
					parent = possibleParent;
					break;
				}
			}

			int indexToStartAt = 0;
			int nearestParentSWCPointID = -1;
			PointInImage connectingPoint = null;
			if (parent != null) {
				if (currentPath.startJoins != null && currentPath.startJoins == parent)
					connectingPoint = currentPath.startJoinsPoint;
				else if (currentPath.endJoins != null && currentPath.endJoins == parent)
					connectingPoint = currentPath.endJoinsPoint;
				else if (parent.startJoins != null && parent.startJoins == currentPath)
					connectingPoint = parent.startJoinsPoint;
				else if (parent.endJoins != null && parent.endJoins == currentPath)
					connectingPoint = parent.endJoinsPoint;
				else
					throw new SWCExportException("Couldn't find the link between parent \"" + parent
							+ "\"\nand child \"" + currentPath + "\" which are somehow joined");

				/* Find the SWC point ID on the parent which is nearest: */

				double distanceSquaredToNearestParentPoint = Double.MAX_VALUE;
				for (final SWCPoint s : result) {
					if (s.fromPath != parent)
						continue;
					final double distanceSquared = connectingPoint.distanceSquaredTo(s.x, s.y, s.z);
					if (distanceSquared < distanceSquaredToNearestParentPoint) {
						nearestParentSWCPointID = s.id;
						distanceSquaredToNearestParentPoint = distanceSquared;
					}
				}

				/*
				 * Now find the index of the point on this path which is nearest
				 */
				indexToStartAt = pathToUse.indexNearestTo(connectingPoint.x, connectingPoint.y, connectingPoint.z);
			}

			SWCPoint firstSWCPoint = null;

			final boolean realRadius = pathToUse.hasCircles();
			for (int i = indexToStartAt; i < pathToUse.points; ++i) {
				double radius = 0;
				if (realRadius)
					radius = pathToUse.radiuses[i];
				final SWCPoint swcPoint = new SWCPoint(currentPointID, pathToUse.getSWCType(),
						pathToUse.precise_x_positions[i], pathToUse.precise_y_positions[i],
						pathToUse.precise_z_positions[i], radius,
						firstSWCPoint == null ? nearestParentSWCPointID : currentPointID - 1);
				swcPoint.fromPath = currentPath;
				result.add(swcPoint);
				++currentPointID;
				if (firstSWCPoint == null)
					firstSWCPoint = swcPoint;
			}

			boolean firstOfOtherBranch = true;
			for (int i = indexToStartAt - 1; i >= 0; --i) {
				int previousPointID = currentPointID - 1;
				if (firstOfOtherBranch) {
					firstOfOtherBranch = false;
					previousPointID = firstSWCPoint.id;
				}
				double radius = 0;
				if (realRadius)
					radius = pathToUse.radiuses[i];
				final SWCPoint swcPoint = new SWCPoint(currentPointID, pathToUse.getSWCType(),
						pathToUse.precise_x_positions[i], pathToUse.precise_y_positions[i],
						pathToUse.precise_z_positions[i], radius, previousPointID);
				swcPoint.fromPath = currentPath;
				result.add(swcPoint);
				++currentPointID;
			}

			pathsAlreadyDone.add(currentPath);

			/*
			 * Add all the connected paths that aren't already in
			 * pathsAlreadyDone
			 */

			for (final Path connectedPath : currentPath.somehowJoins) {
				if (!pathsAlreadyDone.contains(connectedPath)) {
					nextPathsToAdd.add(connectedPath);
				}
			}
		}

		// Now check that all selectedPaths are in pathsAlreadyDone, otherwise
		// give an error:

		Path disconnectedExample = null;
		int selectedAndNotConnected = 0;
		for (final Path selectedPath : selectedPaths) {
			if (!pathsAlreadyDone.contains(selectedPath)) {
				++selectedAndNotConnected;
				if (disconnectedExample == null)
					disconnectedExample = selectedPath;
			}
		}
		if (selectedAndNotConnected > 0)
			throw new SWCExportException("You must select all the connected paths\n(" + selectedAndNotConnected
					+ " paths (e.g. \"" + disconnectedExample + "\") were not connected.)");

		return result;
	}

	public synchronized void resetListeners(final Path justAdded) {
		resetListeners(justAdded, false);
	}

	public synchronized void resetListeners(final Path justAdded, final boolean expandAll) {

		final ArrayList<String> pathListEntries = new ArrayList<>();

		for (final Path p : allPaths) {
			final int pathID = p.getID();
			if (p == null) {
				throw new RuntimeException("BUG: A path in allPaths was null!");
			}
			String name = p.getName();
			if (name == null)
				name = "Path [" + pathID + "]";
			if (p.startJoins != null) {
				name += ", starts on " + p.startJoins.getName();
			}
			if (p.endJoins != null) {
				name += ", ends on " + p.endJoins.getName();
			}
			name += " [" + p.getRealLengthString() + " " + spacing_units + "]";
			pathListEntries.add(name);
		}

		for (final PathAndFillListener listener : listeners)
			listener.setPathList(pathListEntries.toArray(new String[] {}), justAdded, expandAll);

		final int fills = allFills.size();

		final String[] fillListEntries = new String[fills];

		for (int i = 0; i < fills; ++i) {

			final Fill f = allFills.get(i);
			if (f == null) {
				if (verbose)
					SNT.log("fill was null with i " + i + " out of " + fills);
				continue;
			}

			String name = "Fill (" + i + ")";

			if ((f.sourcePaths != null) && (f.sourcePaths.size() > 0)) {
				name += " from paths: " + f.getSourcePathsStringHuman();
			}
			fillListEntries[i] = name;
		}

		for (final PathAndFillListener pafl : listeners)
			pafl.setFillList(fillListEntries);

	}

	public void addPath(final Path p) {
		addPath(p, false);
	}

	public synchronized void addPath(final Path p, final boolean forceNewName) {
		if (getPathFromID(p.getID()) != null)
			throw new RuntimeException("Attempted to add a path with an ID that was already added");
		if (p.getID() < 0) {
			p.setID(++maxUsedID);
		}
		if (maxUsedID < p.getID())
			maxUsedID = p.getID();
		if (p.name == null || forceNewName) {
			final String suggestedName = getDefaultName(p);
			p.setName(suggestedName);
		}
		// Now check if there's already a path with this name.
		// If so, try adding numbered suffixes:
		final String originalName = p.getName();
		String candidateName = originalName;
		int numberSuffix = 2;
		while (getPathFromName(candidateName) != null) {
			candidateName = originalName + " (" + numberSuffix + ")";
			++numberSuffix;
		}
		p.setName(candidateName);
		/*
		 * Generate a new content3D, since it matters that the path is added
		 * with the right name via update3DViewerContents:
		 */
		if (plugin != null && plugin.use3DViewer) {
			p.removeFrom3DViewer(plugin.univ);
			p.addTo3DViewer(plugin.univ, plugin.deselectedColor3f, plugin.colorImage);
		}
		allPaths.add(p);
		resetListeners(p);
	}

	/*
	 * Find the default name for a new path, making sure it doesn't collide with
	 * any of the existing names:
	 */

	protected String getDefaultName(final Path p) {
		if (p.getID() < 0)
			throw new RuntimeException("A path's ID should never be negative");
		return "Path (" + p.getID() + ")";
	}

	public synchronized void deletePath(final int index) {
		deletePath(index, true);
	}

	public synchronized void deletePath(final Path p) {
		final int i = getPathIndex(p);
		if (i < 0)
			throw new RuntimeException("Trying to delete a non-existent path: " + p);
		deletePath(i);
	}

	public synchronized int getPathIndex(final Path p) {
		int i = 0;
		for (i = 0; i < allPaths.size(); ++i) {
			if (p == allPaths.get(i))
				return i;
		}
		return -1;
	}

	private synchronized void deletePath(final int index, final boolean updateInterface) {

		final Path originalPathToDelete = allPaths.get(index);

		Path unfittedPathToDelete = null;
		Path fittedPathToDelete = null;

		if (originalPathToDelete.fittedVersionOf == null) {
			unfittedPathToDelete = originalPathToDelete;
			fittedPathToDelete = originalPathToDelete.fitted;
		} else {
			unfittedPathToDelete = originalPathToDelete.fittedVersionOf;
			fittedPathToDelete = originalPathToDelete;
		}

		allPaths.remove(unfittedPathToDelete);
		if (fittedPathToDelete != null)
			allPaths.remove(fittedPathToDelete);

		// We don't just delete; have to fix up the references
		// in other paths (for start and end joins):

		for (final Path p : allPaths) {
			if (p.startJoins == unfittedPathToDelete) {
				p.startJoins = null;
				p.startJoinsPoint = null;
			}
			if (p.endJoins == unfittedPathToDelete) {
				p.endJoins = null;
				p.endJoinsPoint = null;
			}
		}

		selectedPathsSet.remove(fittedPathToDelete);
		selectedPathsSet.remove(unfittedPathToDelete);

		if (plugin != null && plugin.use3DViewer) {
			if (fittedPathToDelete != null && fittedPathToDelete.content3D != null)
				fittedPathToDelete.removeFrom3DViewer(plugin.univ);
			if (unfittedPathToDelete.content3D != null)
				unfittedPathToDelete.removeFrom3DViewer(plugin.univ);
		}

		if (updateInterface)
			resetListeners(null);
	}

	public void deletePaths(final int[] indices) {

		Arrays.sort(indices);

		for (int i = indices.length - 1; i >= 0; --i) {
			deletePath(indices[i], false);
		}

		resetListeners(null);
	}

	public void addFill(final Fill fill) {

		allFills.add(fill);
		resetListeners(null);
	}

	public void deleteFills(final int[] indices) {

		Arrays.sort(indices);

		for (int i = indices.length - 1; i >= 0; --i) {
			deleteFill(indices[i], false);
		}

		resetListeners(null);
	}

	public void deleteFill(final int index) {
		deleteFill(index, true);
	}

	private synchronized void deleteFill(final int index, final boolean updateInterface) {

		allFills.remove(index);

		if (updateInterface)
			resetListeners(null);
	}

	public void reloadFill(final int index) {

		final Fill toReload = allFills.get(index);

		plugin.startFillerThread(
				FillerThread.fromFill(plugin.getImagePlus(), plugin.stackMin, plugin.stackMax, true, toReload));

	}

	// FIXME: should probably use XMLStreamWriter instead of this ad-hoc
	// approach:
	synchronized public void writeXML(final String fileName, final boolean compress) throws IOException {

		PrintWriter pw = null;

		try {
			if (compress)
				pw = new PrintWriter(
						new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(fileName)), "UTF-8"));
			else
				pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"));

			pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			pw.println("<!DOCTYPE tracings [");
			pw.println("  <!ELEMENT tracings       (samplespacing,imagesize,path*,fill*)>");
			pw.println("  <!ELEMENT imagesize      EMPTY>");
			pw.println("  <!ELEMENT samplespacing  EMPTY>");
			pw.println("  <!ELEMENT path           (point+)>");
			pw.println("  <!ELEMENT point          EMPTY>");
			pw.println("  <!ELEMENT fill           (node*)>");
			pw.println("  <!ELEMENT node           EMPTY>");
			pw.println("  <!ATTLIST samplespacing  x                 CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST samplespacing  y                 CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST samplespacing  z                 CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST samplespacing  units             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST imagesize      width             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST imagesize      height            CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST imagesize      depth             CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST path           id                CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST path           primary           CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           name              CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           startson          CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           startsindex       CDATA           #IMPLIED>"); // deprecated
			pw.println("  <!ATTLIST path           startsx           CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           startsy           CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           startsz           CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           endson            CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           endsindex         CDATA           #IMPLIED>"); // deprecated
			pw.println("  <!ATTLIST path           endsx             CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           endsy             CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           endsz             CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           reallength        CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           usefitted         (true|false)    #IMPLIED>");
			pw.println("  <!ATTLIST path           fitted            CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           fittedversionof   CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           swctype           CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST path           color             CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST point          x                 CDATA           #REQUIRED>"); // deprecated
			pw.println("  <!ATTLIST point          y                 CDATA           #REQUIRED>"); // deprecated
			pw.println("  <!ATTLIST point          z                 CDATA           #REQUIRED>"); // deprecated
			pw.println("  <!ATTLIST point          xd                CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST point          yd                CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST point          zd                CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST point          tx                CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST point          ty                CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST point          tz                CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST point          r                 CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST fill           id                CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST fill           frompaths         CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST fill           metric            CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST fill           threshold         CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST fill           volume            CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST node           id                CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           x                 CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           y                 CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           z                 CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           previousid        CDATA           #IMPLIED>");
			pw.println("  <!ATTLIST node           distance          CDATA           #REQUIRED>");
			pw.println("  <!ATTLIST node           status            (open|closed)   #REQUIRED>");
			pw.println("]>");
			pw.println("");

			pw.println("<tracings>");

			pw.println("  <samplespacing x=\"" + x_spacing + "\" " + "y=\"" + y_spacing + "\" " + "z=\"" + z_spacing
					+ "\" " + "units=\"" + spacing_units + "\"/>");

			pw.println("  <imagesize width=\"" + width + "\" height=\"" + height + "\" depth=\"" + depth + "\"/>");

			for (final Path p : allPaths) {
				// This probably should be a String returning
				// method of Path.
				pw.print("  <path id=\"" + p.getID() + "\"");
				pw.print(" swctype=\"" + p.getSWCType() + "\"");
				pw.print(" color=\"" + SNT.getColorString(p.getColor()) + "\"");
				String startsString = "";
				String endsString = "";
				if (p.startJoins != null) {
					final int startPathID = p.startJoins.getID();
					// Find the nearest index for backward compatability:
					int nearestIndexOnStartPath = -1;
					if (p.startJoins.size() > 0) {
						nearestIndexOnStartPath = p.startJoins.indexNearestTo(p.startJoinsPoint.x, p.startJoinsPoint.y,
								p.startJoinsPoint.z);
					}
					startsString = " startson=\"" + startPathID + "\"" + " startx=\"" + p.startJoinsPoint.x + "\""
							+ " starty=\"" + p.startJoinsPoint.y + "\"" + " startz=\"" + p.startJoinsPoint.z + "\"";
					if (nearestIndexOnStartPath >= 0)
						startsString += " startsindex=\"" + nearestIndexOnStartPath + "\"";
				}
				if (p.endJoins != null) {
					final int endPathID = p.endJoins.getID();
					// Find the nearest index for backward compatability:
					int nearestIndexOnEndPath = -1;
					if (p.endJoins.size() > 0) {
						nearestIndexOnEndPath = p.endJoins.indexNearestTo(p.endJoinsPoint.x, p.endJoinsPoint.y,
								p.endJoinsPoint.z);
					}
					endsString = " endson=\"" + endPathID + "\"" + " endsx=\"" + p.endJoinsPoint.x + "\"" + " endsy=\""
							+ p.endJoinsPoint.y + "\"" + " endsz=\"" + p.endJoinsPoint.z + "\"";
					if (nearestIndexOnEndPath >= 0)
						endsString += " endsindex=\"" + nearestIndexOnEndPath + "\"";
				}
				if (p.getPrimary())
					pw.print(" primary=\"true\"");
				pw.print(" usefitted=\"" + p.getUseFitted() + "\"");
				if (p.fitted != null) {
					pw.print(" fitted=\"" + p.fitted.getID() + "\"");
				}
				if (p.fittedVersionOf != null) {
					pw.print(" fittedversionof=\"" + p.fittedVersionOf.getID() + "\"");
				}
				pw.print(startsString);
				pw.print(endsString);
				if (p.name != null) {
					pw.print(" name=\"" + XMLFunctions.escapeForXMLAttributeValue(p.name) + "\"");
				}
				pw.print(" reallength=\"" + p.getRealLength() + "\"");
				pw.println(">");

				for (int i = 0; i < p.size(); ++i) {
					final int px = p.getXUnscaled(i);
					final int py = p.getYUnscaled(i);
					final int pz = p.getZUnscaled(i);
					final double pxd = p.precise_x_positions[i];
					final double pyd = p.precise_y_positions[i];
					final double pzd = p.precise_z_positions[i];
					String attributes = "x=\"" + px + "\" " + "y=\"" + py + "\" z=\"" + pz + "\" " + "xd=\"" + pxd
							+ "\" yd=\"" + pyd + "\" zd=\"" + pzd + "\"";
					if (p.hasCircles()) {
						attributes += " tx=\"" + p.tangents_x[i] + "\"";
						attributes += " ty=\"" + p.tangents_y[i] + "\"";
						attributes += " tz=\"" + p.tangents_z[i] + "\"";
						attributes += " r=\"" + p.radiuses[i] + "\"";
					}
					pw.println("    <point " + attributes + "/>");
				}
				pw.println("  </path>");
			}
			// Now output the fills:
			int fillIndex = 0;
			for (final Fill f : allFills) {
				f.writeXML(pw, fillIndex);
				++fillIndex;
			}
			pw.println("</tracings>");
		} finally {
			if (pw != null)
				pw.close();
		}
	}

	double parsed_x_spacing;
	double parsed_y_spacing;
	double parsed_z_spacing;

	String parsed_units;

	int parsed_width;
	int parsed_height;
	int parsed_depth;

	Fill current_fill;
	Path current_path;

	HashMap<Integer, Integer> startJoins;
	HashMap<Integer, Integer> startJoinsIndices;
	HashMap<Integer, PointInImage> startJoinsPoints;
	HashMap<Integer, Integer> endJoins;
	HashMap<Integer, Integer> endJoinsIndices;
	HashMap<Integer, PointInImage> endJoinsPoints;
	HashMap<Integer, Boolean> useFittedFields;
	HashMap<Integer, Integer> fittedFields;
	HashMap<Integer, Integer> fittedVersionOfFields;

	ArrayList<int[]> sourcePathIDForFills;

	int last_fill_node_id;

	int last_fill_id;

	HashSet<Integer> foundIDs;

	@Override
	public void startElement(final String uri, final String localName, final String qName, final Attributes attributes)
			throws TracesFileFormatException {

		if (qName.equals("tracings")) {

			startJoins = new HashMap<>();
			startJoinsIndices = new HashMap<>();
			startJoinsPoints = new HashMap<>();
			endJoins = new HashMap<>();
			endJoinsIndices = new HashMap<>();
			endJoinsPoints = new HashMap<>();
			useFittedFields = new HashMap<>();
			fittedFields = new HashMap<>();
			fittedVersionOfFields = new HashMap<>();

			sourcePathIDForFills = new ArrayList<>();
			foundIDs = new HashSet<>();

			last_fill_id = -1;

			/*
			 * We need to remove the old paths and fills before loading the
			 * ones:
			 */

			if (verbose)
				SNT.log("Clearing old paths and fills...");

			clearPathsAndFills();

			if (verbose)
				SNT.log("Now " + allPaths.size() + " paths and " + allFills.size() + " fills");

		} else if (qName.equals("imagesize")) {

			try {

				final String widthString = attributes.getValue("width");
				final String heightString = attributes.getValue("height");
				final String depthString = attributes.getValue("depth");

				parsed_width = Integer.parseInt(widthString);
				parsed_height = Integer.parseInt(heightString);
				parsed_depth = Integer.parseInt(depthString);

				if (needImageDataFromTracesFile) {
					this.width = parsed_width;
					this.height = parsed_height;
					this.depth = parsed_depth;
				} else if (!((parsed_width == width) && (parsed_height == height) && (parsed_depth == depth))) {
					throw new TracesFileFormatException(
							"The image size in the traces file didn't match - it's probably for another image");
				}

			} catch (final NumberFormatException e) {
				throw new TracesFileFormatException("There was an invalid attribute to <imagesize/>: " + e);
			}

		} else if (qName.equals("samplespacing")) {

			try {

				final String xString = attributes.getValue("x");
				final String yString = attributes.getValue("y");
				final String zString = attributes.getValue("z");
				parsed_units = attributes.getValue("units");

				parsed_x_spacing = Double.parseDouble(xString);
				parsed_y_spacing = Double.parseDouble(yString);
				parsed_z_spacing = Double.parseDouble(zString);

				if (needImageDataFromTracesFile) {
					this.x_spacing = parsed_x_spacing;
					this.y_spacing = parsed_y_spacing;
					this.z_spacing = parsed_z_spacing;
					this.spacing_units = parsed_units;
				}

			} catch (final NumberFormatException e) {
				throw new TracesFileFormatException("There was an invalid attribute to <samplespacing/>: " + e);
			}

		} else if (qName.equals("path")) {

			final String idString = attributes.getValue("id");

			final String swcTypeString = attributes.getValue("swctype");
			final String colorString = attributes.getValue("color");
			final String useFittedString = attributes.getValue("usefitted");
			final String fittedIDString = attributes.getValue("fitted");
			final String fittedVersionOfIDString = attributes.getValue("fittedversionof");

			final String startsonString = attributes.getValue("startson");
			final String startsindexString = attributes.getValue("startsindex");
			final String startsxString = attributes.getValue("startsx");
			final String startsyString = attributes.getValue("startsy");
			final String startszString = attributes.getValue("startsz");
			final String endsonString = attributes.getValue("endson");
			final String endsindexString = attributes.getValue("endsindex");
			final String endsxString = attributes.getValue("endsx");
			final String endsyString = attributes.getValue("endsy");
			final String endszString = attributes.getValue("endsz");

			final String nameString = attributes.getValue("name");

			final String primaryString = attributes.getValue("primary");

			if (startsxString == null && startsyString == null && startszString == null) {
			} else if (startsxString != null && startsyString != null && startszString != null) {
			} else {
				throw new TracesFileFormatException("If one of starts[xyz] is specified, all of them must be.");
			}

			if (endsxString == null && endsyString == null && endszString == null) {
			} else if (endsxString != null && endsyString != null && endszString != null) {
			} else {
				throw new TracesFileFormatException("If one of ends[xyz] is specified, all of them must be.");
			}

			final boolean accurateStartProvided = startsxString != null;
			final boolean accurateEndProvided = endsxString != null;

			if (startsonString != null && (startsindexString == null && !accurateStartProvided)) {
				throw new TracesFileFormatException(
						"If startson is specified for a path, then startsindex or starts[xyz] must also be specified.");
			}

			if (endsonString != null && (endsindexString == null && !accurateEndProvided)) {
				throw new TracesFileFormatException(
						"If endson is specified for a path, then endsindex or ends[xyz] must also be specified.");
			}

			int startson, endson, endsindex;

			current_path = new Path(x_spacing, y_spacing, z_spacing, spacing_units);

			Integer startsOnInteger = null;
			Integer startsIndexInteger = null;
			PointInImage startJoinPoint = null;
			Integer endsOnInteger = null;
			Integer endsIndexInteger = null;
			PointInImage endJoinPoint = null;

			Integer fittedIDInteger = null;
			Integer fittedVersionOfIDInteger = null;

			if (primaryString != null && primaryString.equals("true"))
				current_path.setPrimary(true);

			int id = -1;

			try {

				id = Integer.parseInt(idString);
				if (foundIDs.contains(id)) {
					throw new TracesFileFormatException("There is more than one path with ID " + id);
				}
				current_path.setID(id);
				if (id > maxUsedID)
					maxUsedID = id;

				if (swcTypeString != null) {
					final int swcType = Integer.parseInt(swcTypeString);
					current_path.setSWCType(swcType, false);
				}

				if (colorString != null) {
					current_path.setColor(SNT.getColor(colorString));
				}

				if (startsonString == null) {
					startson = -1;
				} else {
					startson = Integer.parseInt(startsonString);
					startsOnInteger = new Integer(startson);

					if (startsxString == null) {
						// The index (older file format) was supplied:
						startsIndexInteger = new Integer(startsindexString);
					} else {
						startJoinPoint = new PointInImage(Double.parseDouble(startsxString),
								Double.parseDouble(startsyString), Double.parseDouble(startszString));
					}
				}

				if (endsonString == null)
					endson = endsindex = -1;
				else {
					endson = Integer.parseInt(endsonString);
					endsOnInteger = new Integer(endson);

					if (endsxString != null) {
						endJoinPoint = new PointInImage(Double.parseDouble(endsxString),
								Double.parseDouble(endsyString), Double.parseDouble(endszString));
					} else {
						// The index (older file format) was supplied:
						endsindex = Integer.parseInt(endsindexString);
						endsIndexInteger = new Integer(endsindex);
					}
				}

				if (fittedVersionOfIDString != null)
					fittedVersionOfIDInteger = new Integer(Integer.parseInt(fittedVersionOfIDString));
				if (fittedIDString != null)
					fittedIDInteger = new Integer(Integer.parseInt(fittedIDString));

			} catch (final NumberFormatException e) {
				e.printStackTrace();
				throw new TracesFileFormatException("There was an invalid attribute in <path/>: " + e);
			}

			if (nameString == null)
				current_path.setDefaultName();
			else
				current_path.setName(nameString);

			if (startsOnInteger != null)
				startJoins.put(id, startsOnInteger);
			if (endsOnInteger != null)
				endJoins.put(id, endsOnInteger);

			if (startJoinPoint != null)
				startJoinsPoints.put(id, startJoinPoint);
			if (endJoinPoint != null)
				endJoinsPoints.put(id, endJoinPoint);

			if (startsIndexInteger != null) {
				startJoinsIndices.put(id, startsIndexInteger);
			}
			if (endsIndexInteger != null)
				endJoinsIndices.put(id, endsIndexInteger);

			if (useFittedString == null)
				useFittedFields.put(id, false);
			else {
				if (useFittedString.equals("true"))
					useFittedFields.put(id, true);
				else if (useFittedString.equals("false"))
					useFittedFields.put(id, false);
				else
					throw new TracesFileFormatException(
							"Unknown value for 'fitted' attribute: '" + useFittedString + "'");
			}

			if (fittedIDInteger != null)
				fittedFields.put(id, fittedIDInteger);
			if (fittedVersionOfIDInteger != null)
				fittedVersionOfFields.put(id, fittedVersionOfIDInteger);

		} else if (qName.equals("point")) {

			try {

				double parsed_xd, parsed_yd, parsed_zd;

				final String xdString = attributes.getValue("xd");
				final String ydString = attributes.getValue("yd");
				final String zdString = attributes.getValue("zd");

				final String xString = attributes.getValue("x");
				final String yString = attributes.getValue("y");
				final String zString = attributes.getValue("z");

				if (xdString != null && ydString != null && zdString != null) {
					parsed_xd = Double.parseDouble(xdString);
					parsed_yd = Double.parseDouble(ydString);
					parsed_zd = Double.parseDouble(zdString);
				} else if (xdString != null || ydString != null || zdString != null) {
					throw new TracesFileFormatException(
							"If one of the attributes xd, yd or zd to the point element is specified, they all must be.");
				} else if (xString != null && yString != null && zString != null) {
					parsed_xd = parsed_x_spacing * Integer.parseInt(xString);
					parsed_yd = parsed_y_spacing * Integer.parseInt(yString);
					parsed_zd = parsed_z_spacing * Integer.parseInt(zString);
				} else if (xString != null || yString != null || zString != null) {
					throw new TracesFileFormatException(
							"If one of the attributes x, y or z to the point element is specified, they all must be.");
				} else {
					throw new TracesFileFormatException(
							"Each point element must have at least the attributes (x, y and z) or (xd, yd, zd)");
				}

				current_path.addPointDouble(parsed_xd, parsed_yd, parsed_zd);

				final int lastIndex = current_path.size() - 1;
				final String radiusString = attributes.getValue("r");
				final String tXString = attributes.getValue("tx");
				final String tYString = attributes.getValue("ty");
				final String tZString = attributes.getValue("tz");

				if (radiusString != null && tXString != null && tYString != null && tZString != null) {
					if (lastIndex == 0)
						// Then we've just started, create the arrays in Path:
						current_path.createCircles();
					else if (!current_path.hasCircles())
						throw new TracesFileFormatException(
								"The point at index " + lastIndex + " had a fitted circle, but none previously did");
					current_path.tangents_x[lastIndex] = Double.parseDouble(tXString);
					current_path.tangents_y[lastIndex] = Double.parseDouble(tYString);
					current_path.tangents_z[lastIndex] = Double.parseDouble(tZString);
					current_path.radiuses[lastIndex] = Double.parseDouble(radiusString);
				} else if (radiusString != null || tXString != null || tYString != null || tZString != null)
					throw new TracesFileFormatException(
							"If one of the r, tx, ty or tz attributes to the point element is specified, they all must be");
				else {
					// All circle attributes are null:
					if (current_path.hasCircles())
						throw new TracesFileFormatException(
								"The point at index " + lastIndex + " had no fitted circle, but all previously did");
				}

			} catch (final NumberFormatException e) {
				throw new TracesFileFormatException("There was an invalid attribute to <imagesize/>");
			}

		} else if (qName.equals("fill")) {

			try {

				String[] sourcePaths = {};
				final String fromPathsString = attributes.getValue("frompaths");
				if (fromPathsString != null)
					sourcePaths = fromPathsString.split(", *");

				current_fill = new Fill();

				final String metric = attributes.getValue("metric");
				current_fill.setMetric(metric);

				last_fill_node_id = -1;

				final String fill_id_string = attributes.getValue("id");

				int fill_id = Integer.parseInt(fill_id_string);

				if (fill_id < 0) {
					throw new TracesFileFormatException("Can't have a negative id in <fill>");
				}

				if (fill_id != (last_fill_id + 1)) {
					SNT.log("Out of order id in <fill> (" + fill_id + " when we were expecting " + (last_fill_id + 1)
							+ ")");
					fill_id = last_fill_id + 1;
				}

				final int[] sourcePathIndices = new int[sourcePaths.length];

				for (int i = 0; i < sourcePaths.length; ++i)
					sourcePathIndices[i] = Integer.parseInt(sourcePaths[i]);

				sourcePathIDForFills.add(sourcePathIndices);

				last_fill_id = fill_id;

				final String thresholdString = attributes.getValue("threshold");
				final double fillThreshold = Double.parseDouble(thresholdString);

				current_fill.setThreshold(fillThreshold);

			} catch (final NumberFormatException e) {
				throw new TracesFileFormatException("There was an invalid attribute to <fill>");
			}

		} else if (qName.equals("node")) {

			try {

				final String xString = attributes.getValue("x");
				final String yString = attributes.getValue("y");
				final String zString = attributes.getValue("z");
				final String idString = attributes.getValue("id");
				final String distanceString = attributes.getValue("distance");
				final String previousString = attributes.getValue("previousid");

				final int parsed_x = Integer.parseInt(xString);
				final int parsed_y = Integer.parseInt(yString);
				final int parsed_z = Integer.parseInt(zString);
				final int parsed_id = Integer.parseInt(idString);
				final double parsed_distance = Double.parseDouble(distanceString);
				int parsed_previous;
				if (previousString == null)
					parsed_previous = -1;
				else
					parsed_previous = Integer.parseInt(previousString);

				if (parsed_id != (last_fill_node_id + 1)) {
					throw new TracesFileFormatException("Fill node IDs weren't consecutive integers");
				}

				final String openString = attributes.getValue("status");

				current_fill.add(parsed_x, parsed_y, parsed_z, parsed_distance, parsed_previous,
						openString.equals("open"));

				last_fill_node_id = parsed_id;

			} catch (final NumberFormatException e) {
				throw new TracesFileFormatException("There was an invalid attribute to <node/>: " + e);
			}

		} else {
			throw new TracesFileFormatException("Unknown element: '" + qName + "'");
		}

	}

	public void addTo3DViewer(final Path p) {
		if (plugin != null && plugin.use3DViewer && p.fittedVersionOf == null && p.size() > 1) {
			Path pathToAdd;
			if (p.getUseFitted())
				pathToAdd = p.fitted;
			else
				pathToAdd = p;
			pathToAdd.addTo3DViewer(plugin.univ, plugin.deselectedColor, plugin.colorImage);
		}
	}

	@Override
	public void endElement(final String uri, final String localName, final String qName)
			throws TracesFileFormatException {

		if (qName.equals("path")) {

			allPaths.add(current_path);

		} else if (qName.equals("fill")) {

			allFills.add(current_fill);

		} else if (qName.equals("tracings")) {

			// Then we've finished...

			for (int i = 0; i < allPaths.size(); ++i) {
				final Path p = allPaths.get(i);

				final Integer startID = startJoins.get(p.getID());
				final Integer startIndexInteger = startJoinsIndices.get(p.getID());
				PointInImage startJoinPoint = startJoinsPoints.get(p.getID());
				final Integer endID = endJoins.get(p.getID());
				final Integer endIndexInteger = endJoinsIndices.get(p.getID());
				PointInImage endJoinPoint = endJoinsPoints.get(p.getID());
				final Integer fittedID = fittedFields.get(p.getID());
				final Integer fittedVersionOfID = fittedVersionOfFields.get(p.getID());
				final Boolean useFitted = useFittedFields.get(p.getID());

				if (startID != null) {
					final Path startPath = getPathFromID(startID);
					if (startJoinPoint == null) {
						// Then we have to get it from startIndexInteger:
						startJoinPoint = startPath.getPointInImage(startIndexInteger.intValue());
					}
					p.setStartJoin(startPath, startJoinPoint);
				}
				if (endID != null) {
					final Path endPath = getPathFromID(endID);
					if (endJoinPoint == null) {
						// Then we have to get it from endIndexInteger:
						endJoinPoint = endPath.getPointInImage(endIndexInteger.intValue());
					}
					p.setEndJoin(endPath, endJoinPoint);
				}
				if (fittedID != null) {
					final Path fitted = getPathFromID(fittedID);
					p.fitted = fitted;
					p.setUseFitted(useFitted.booleanValue());
				}
				if (fittedVersionOfID != null) {
					final Path fittedVersionOf = getPathFromID(fittedVersionOfID);
					p.fittedVersionOf = fittedVersionOf;
				}
			}

			// Do some checks that the fitted and fittedVersionOf fields match
			// up:
			for (int i = 0; i < allPaths.size(); ++i) {
				final Path p = allPaths.get(i);
				if (p.fitted != null) {
					if (p.fitted.fittedVersionOf == null)
						throw new TracesFileFormatException("Malformed traces file: p.fitted.fittedVersionOf was null");
					else if (p != p.fitted.fittedVersionOf)
						throw new TracesFileFormatException(
								"Malformed traces file: p didn't match p.fitted.fittedVersionOf");
				} else if (p.fittedVersionOf != null) {
					if (p.fittedVersionOf.fitted == null)
						throw new TracesFileFormatException("Malformed traces file: p.fittedVersionOf.fitted was null");
					else if (p != p.fittedVersionOf.fitted)
						throw new TracesFileFormatException(
								"Malformed traces file: p didn't match p.fittedVersionOf.fitted");
				}
				if (p.useFitted && p.fitted == null) {
					throw new TracesFileFormatException(
							"Malformed traces file: p.useFitted was true but p.fitted was null");
				}
			}

			// Now we're safe to add them all to the 3D Viewer
			for (int i = 0; i < allPaths.size(); ++i) {
				final Path p = allPaths.get(i);
				addTo3DViewer(p);
			}

			// Now turn the source paths into real paths...
			for (int i = 0; i < allFills.size(); ++i) {
				final Fill f = allFills.get(i);
				final Set<Path> realSourcePaths = new HashSet<>();
				final int[] sourcePathIDs = sourcePathIDForFills.get(i);
				for (int j = 0; j < sourcePathIDs.length; ++j) {
					final Path sourcePath = getPathFromID(sourcePathIDs[j]);
					if (sourcePath != null)
						realSourcePaths.add(sourcePath);
				}
				f.setSourcePaths(realSourcePaths);
			}

			setSelected(new Path[0], this);
			resetListeners(null, true);
			if (plugin != null)
				plugin.repaintAllPanes();
		}

	}

	public static PathAndFillManager createFromTracesFile(final String filename) {
		final PathAndFillManager pafm = new PathAndFillManager();
		if (pafm.loadGuessingType(filename))
			return pafm;
		else
			return null;
	}

	public boolean loadFromString(final String tracesFileAsString) {

		final StringReader reader = new StringReader(tracesFileAsString);
		final boolean result = load(null, reader);
		reader.close();
		return result;

	}

	public boolean load(final InputStream is, final Reader reader) {

		try {

			final SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setValidating(true);
			final SAXParser parser = factory.newSAXParser();

			if (is != null)
				parser.parse(is, this);
			else if (reader != null) {
				final InputSource inputSource = new InputSource(reader);
				parser.parse(inputSource, this);
			}

			// We must have got the image data if we've got to this stage...
			needImageDataFromTracesFile = false;

		} catch (final javax.xml.parsers.ParserConfigurationException e) {

			clearPathsAndFills();
			SNT.error("There was a ParserConfigurationException: " + e);
			return false;

		} catch (final SAXException e) {

			clearPathsAndFills();
			SNT.error(e.toString());
			return false;

		} catch (final FileNotFoundException e) {

			clearPathsAndFills();
			SNT.error("File not found: " + e);
			return false;

		} catch (final IOException e) {

			clearPathsAndFills();
			SNT.error("There was an IO exception while reading the file: " + e);
			return false;

		}

		return true;

	}

	void clearPathsAndFills() {
		maxUsedID = -1;
		if (plugin != null && plugin.use3DViewer) {
			for (final Path p : allPaths)
				p.removeFrom3DViewer(plugin.univ);
		}
		allPaths.clear();
		allFills.clear();
		resetListeners(null);
	}

	/*
	 * The two useful documents about the SWC file formats are:
	 *
	 * doi:10.1016/S0165-0270(98)00091-0
	 * http://linkinghub.elsevier.com/retrieve/pii/S0165027098000910 J Neurosci
	 * Methods. 1998 Oct 1;84(1-2):49-54.Links
	 * "An on-line archive of reconstructed hippocampal neurons." Cannon RC,
	 * Turner DA, Pyapali GK, Wheal HV.
	 *
	 * http://www.personal.soton.ac.uk/dales/morpho/morpho_doc/index.html
	 *
	 * Annoyingly, some published SWC files use world coordinates in microns
	 * (correct as I understand the specification) while some others use image
	 * coordinates (incorrect and less useful). An example of the latter seems
	 * to part of the DIADEM Challenge data set.
	 *
	 * There aren't any really good workarounds for this, since if we try to
	 * guess whether the files are broken or not, there are always going to be
	 * odd cases where the heuristics fail. In addition, it's not at all clear
	 * what the "radius" column is meant to mean in these files.
	 *
	 * So, the extent to which I'm going to work around these broken files is
	 * that there's a flag to this method which says
	 * "assume that the coordinates are image coordinates". The broken files
	 * also seem to require that you scale the radius by the minimum voxel
	 * separation (!) so that flag also turns on that workaround.
	 */

	public boolean importSWC(final BufferedReader br, final boolean assumeCoordinatesIndexVoxels) throws IOException {
		return importSWC(br, assumeCoordinatesIndexVoxels, 0, 0, 0, 1, 1, 1, true);
	}

	public boolean importSWC(final BufferedReader br, final boolean assumeCoordinatesIndexVoxels, final double x_offset,
			final double y_offset, final double z_offset, final double x_scale, final double y_scale,
			final double z_scale, final boolean replaceAllPaths) throws IOException {

		if (needImageDataFromTracesFile)
			throw new RuntimeException("[BUG] Trying to load SWC file while we still need image data information");

		if (replaceAllPaths)
			clearPathsAndFills();

		final Pattern pEmpty = Pattern.compile("^\\s*$");
		final Pattern pComment = Pattern.compile("^([^#]*)#.*$");

		final Set<Integer> alreadySeen = new HashSet<>();
		final Map<Integer, SWCPoint> idToSWCPoint = new HashMap<>();

		final List<SWCPoint> primaryPoints = new ArrayList<>();

		/*
		 * Some SWC files I've tried use world co-ordinates (good) but some seem
		 * to have the sign wrong, so calculate what should be the minimum and
		 * maximum value in each axis so we can test for this later.
		 */

		final double minX = Math.min(0, width * x_spacing);
		final double minY = Math.min(0, height * y_spacing);
		final double minZ = Math.min(0, depth * z_spacing);

		final double maxX = Math.max(0, width * x_spacing);
		final double maxY = Math.max(0, height * y_spacing);
		final double maxZ = Math.max(0, depth * z_spacing);

		final double minimumVoxelSpacing = Math.min(Math.abs(x_spacing),
				Math.min(Math.abs(y_spacing), Math.abs(z_spacing)));

		int pointsOutsideImageRange = 0;

		String line;
		while ((line = br.readLine()) != null) {
			final Matcher mComment = pComment.matcher(line);
			line = mComment.replaceAll("$1").trim();
			final Matcher mEmpty = pEmpty.matcher(line);
			if (mEmpty.matches())
				continue;
			final String[] fields = line.split("\\s+");
			if (fields.length < 7) {
				SNT.error("Wrong number of fields (" + fields.length + ") in line: " + line);
				return false;
			}
			try {
				final int id = Integer.parseInt(fields[0]);
				final int type = Integer.parseInt(fields[1]);
				double x = x_scale * Double.parseDouble(fields[2]) + x_offset;
				double y = y_scale * Double.parseDouble(fields[3]) + y_offset;
				double z = z_scale * Double.parseDouble(fields[4]) + z_offset;
				if (assumeCoordinatesIndexVoxels) {
					x *= x_spacing;
					y *= y_spacing;
					z *= z_spacing;
				}
				double radius = Double.parseDouble(fields[5]);
				if (assumeCoordinatesIndexVoxels) {
					/*
					 * See the comment above; this just seems to be the
					 * convention in the broken files that I've come across:
					 */
					radius *= minimumVoxelSpacing;
				}

				/*
				 * If the radius is set to near zero, then artificially set it
				 * to half of the voxel spacing so that something* appears in
				 * the 3D Viewer
				 */

				if (Math.abs(radius) < 0.0000001)
					radius = minimumVoxelSpacing / 2;

				final int previous = Integer.parseInt(fields[6]);
				if (alreadySeen.contains(id)) {
					SNT.error("Point with ID " + id + " found more than once");
					return false;
				}
				alreadySeen.add(id);

				if (x < minX || x > maxX)
					++pointsOutsideImageRange;
				if (y < minY || y > maxY)
					++pointsOutsideImageRange;
				if (z < minZ || z > maxZ)
					++pointsOutsideImageRange;

				final SWCPoint p = new SWCPoint(id, type, x, y, z, radius, previous);
				idToSWCPoint.put(id, p);
				if (previous == -1)
					primaryPoints.add(p);
				else {
					final SWCPoint previousPoint = idToSWCPoint.get(previous);
					if (previousPoint != null) {
						p.previousPoint = previousPoint;
						previousPoint.addNextPoint(p);
					}
				}
			} catch (final NumberFormatException nfe) {
				SNT.error("There was a malformed number in line: " + line);
				return false;
			}
		}

		if (pointsOutsideImageRange > 0)
			SNT.warn("" + pointsOutsideImageRange
					+ " points were outside the image volume - you may need to change your SWC import options");

		final HashMap<SWCPoint, Path> pointToPath = new HashMap<>();

		final PriorityQueue<SWCPoint> backtrackTo = new PriorityQueue<>();

		for (final SWCPoint start : primaryPoints)
			backtrackTo.add(start);

		final HashMap<Path, SWCPoint> pathStartsOnSWCPoint = new HashMap<>();
		final HashMap<Path, PointInImage> pathStartsAtPointInImage = new HashMap<>();

		SWCPoint start;
		Path currentPath;
		while ((start = backtrackTo.poll()) != null) {
			currentPath = new Path(x_spacing, y_spacing, z_spacing, spacing_units);
			currentPath.createCircles();
			int added = 0;
			if (start.previousPoint != null) {
				final SWCPoint beforeStart = start.previousPoint;
				pathStartsOnSWCPoint.put(currentPath, beforeStart);
				pathStartsAtPointInImage.put(currentPath, beforeStart.getPointInImage());
				currentPath.addPointDouble(beforeStart.x, beforeStart.y, beforeStart.z);
				currentPath.radiuses[added] = beforeStart.radius;
				++added;

			}
			// Now we can start adding points to the path:
			SWCPoint currentPoint = start;
			while (currentPoint != null) {
				currentPath.addPointDouble(currentPoint.x, currentPoint.y, currentPoint.z);
				currentPath.radiuses[added] = currentPoint.radius;
				++added;
				pointToPath.put(currentPoint, currentPath);
				/*
				 * Remove each one from "alreadySeen" when we add it to a path,
				 * just to check that nothing's left at the end, which indicates
				 * that the file is malformed.
				 */
				alreadySeen.remove(currentPoint.id);
				if (currentPoint.nextPoints.size() > 0) {
					final SWCPoint newCurrentPoint = currentPoint.nextPoints.get(0);
					currentPoint.nextPoints.remove(0);
					for (int i = 0; i < currentPoint.nextPoints.size(); ++i) {
						final SWCPoint pointToQueue = currentPoint.nextPoints.get(i);
						backtrackTo.add(pointToQueue);
					}
					currentPoint = newCurrentPoint;
				} else {
					currentPath.setSWCType(currentPoint.type); // Assign point
																// type to path
					currentPoint = null;
				}
			}
			currentPath.setGuessedTangents(2);
			addPath(currentPath);
		}

		if (alreadySeen.size() > 0) {
			SNT.error("Malformed file: there are some misconnected points.\n"
					+ "(List will now be shown in ImageJ's Console)");
			for (final int i : alreadySeen) {
				final SWCPoint p = idToSWCPoint.get(i);
				SNT.log("  Misconnected: " + p);
			}
			return false;
		}

		// Set the start joins:
		for (final Path p : allPaths) {
			final SWCPoint swcPoint = pathStartsOnSWCPoint.get(p);
			if (swcPoint == null)
				continue;
			final Path previousPath = pointToPath.get(swcPoint);
			final PointInImage pointInImage = pathStartsAtPointInImage.get(p);
			p.setStartJoin(previousPath, pointInImage);
		}

		resetListeners(null, true);
		return true;
	}

	public boolean importSWC(final String filename, final boolean ignoreCalibration) {
		return importSWC(filename, ignoreCalibration, 0, 0, 0, 1, 1, 1, true);
	}

	public boolean importSWC(final String filename, final boolean ignoreCalibration, final double x_offset,
			final double y_offset, final double z_offset, final double x_scale, final double y_scale,
			final double z_scale, final boolean replaceAllPaths) {

		final File f = new File(filename);
		if (!f.exists()) {
			SNT.error("The traces file '" + filename + "' does not exist.");
			return false;
		}

		InputStream is = null;
		boolean result = false;

		try {

			is = new BufferedInputStream(new FileInputStream(filename));
			final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));

			result = importSWC(br, ignoreCalibration, x_offset, y_offset, z_offset, x_scale, y_scale, z_scale,
					replaceAllPaths);

			if (is != null)
				is.close();

		} catch (final IOException ioe) {
			SNT.error("Couldn't open file '" + filename + "' for reading.");
			return false;
		}

		return result;

	}

	public static final int TRACES_FILE_TYPE_COMPRESSED_XML = 1;
	public static final int TRACES_FILE_TYPE_UNCOMPRESSED_XML = 2;
	public static final int TRACES_FILE_TYPE_SWC = 3;

	public static int guessTracesFileType(final String filename) {

		/*
		 * Look at the magic bytes at the start of the file:
		 *
		 * If this looks as if it's gzip compressed, assume it's a compressed
		 * traces file - the native format of this plugin.
		 *
		 * If it begins "<?xml", assume it's an uncompressed traces file.
		 *
		 * Otherwise, assum it's an SWC file.
		 */

		final File f = new File(filename);
		if (!f.exists()) {
			SNT.error("The traces file '" + filename + "' does not exist.");
			return -1;
		}

		try {
			InputStream is;
			final byte[] buf = new byte[8];
			is = new FileInputStream(filename);
			is.read(buf, 0, 8);
			is.close();
			if (verbose)
				SNT.log("buf[0]: " + buf[0] + ", buf[1]: " + buf[1]);
			if (((buf[0] & 0xFF) == 0x1F) && ((buf[1] & 0xFF) == 0x8B))
				return TRACES_FILE_TYPE_COMPRESSED_XML;
			else if (((buf[0] == '<') && (buf[1] == '?') && (buf[2] == 'x') && (buf[3] == 'm') && (buf[4] == 'l')
					&& (buf[5] == ' ')))
				return TRACES_FILE_TYPE_UNCOMPRESSED_XML;

		} catch (final IOException e) {
			SNT.error("Couldn't read from file: " + filename);
			return -1;
		}

		return TRACES_FILE_TYPE_SWC;
	}

	public boolean loadCompressedXML(final String filename) {
		try {
			if (verbose)
				SNT.log("Loading gzipped file...");
			return load(new GZIPInputStream(new BufferedInputStream(new FileInputStream(filename))), null);
		} catch (final IOException ioe) {
			SNT.error("Couldn't open file '" + filename + "' for reading\n(n.b. it was expected to be compressed XML)");
			return false;
		}
	}

	public boolean loadUncompressedXML(final String filename) {
		try {
			if (verbose)
				SNT.log("Loading uncompressed file...");
			return load(new BufferedInputStream(new FileInputStream(filename)), null);
		} catch (final IOException ioe) {
			SNT.error("Couldn't open file '" + filename + "' for reading\n(n.b. it was expected to be XML)");
			return false;
		}
	}

	public boolean loadGuessingType(final String filename) {

		final int guessedType = guessTracesFileType(filename);
		switch (guessedType) {

		case TRACES_FILE_TYPE_COMPRESSED_XML:
			return loadCompressedXML(filename);
		case TRACES_FILE_TYPE_UNCOMPRESSED_XML:
			return loadUncompressedXML(filename);
		case TRACES_FILE_TYPE_SWC:
			return importSWC(filename, false, 0, 0, 0, 1, 1, 1, true);
		default:
			SNT.error("guessTracesFileType() return an unknown type" + guessedType);
			return false;
		}
	}

	synchronized void setPathPointsInVolume(final byte[][] slices, final int width, final int height, final int depth) {
		setPathPointsInVolume(allPaths, slices, width, height, depth);
	}

	/*
	 * This method will set all the points in array that correspond to points on
	 * one of the paths to 255, leaving everything else as it is. This is useful
	 * for creating stacks that can be used in skeleton analysis plugins that
	 * expect a stack of this kind.
	 */
	synchronized void setPathPointsInVolume(final ArrayList<Path> paths, final byte[][] slices, final int width,
			final int height, final int depth) {
		for (final Path topologyPath : paths) {
			Path p = topologyPath;
			if (topologyPath.getUseFitted()) {
				p = topologyPath.fitted;
			}
			if (topologyPath.fittedVersionOf != null)
				continue;

			final int n = p.size();

			final ArrayList<Bresenham3D.IntegerPoint> pointsToJoin = new ArrayList<>();

			if (p.startJoins != null) {
				final PointInImage s = p.startJoinsPoint;
				final Path sp = p.startJoins;
				final int spi = sp.indexNearestTo(s.x, s.y, s.z);
				pointsToJoin.add(
						new Bresenham3D.IntegerPoint(sp.getXUnscaled(spi), sp.getYUnscaled(spi), sp.getZUnscaled(spi)));
			}

			for (int i = 0; i < n; ++i) {
				pointsToJoin.add(new Bresenham3D.IntegerPoint(p.getXUnscaled(i), p.getYUnscaled(i), p.getZUnscaled(i)));
			}

			if (p.endJoins != null) {
				final PointInImage s = p.endJoinsPoint;
				final Path sp = p.endJoins;
				final int spi = sp.indexNearestTo(s.x, s.y, s.z);
				pointsToJoin.add(
						new Bresenham3D.IntegerPoint(sp.getXUnscaled(spi), sp.getYUnscaled(spi), sp.getZUnscaled(spi)));
			}

			Bresenham3D.IntegerPoint previous = null;
			for (final Bresenham3D.IntegerPoint current : pointsToJoin) {
				if (previous == null) {
					previous = current;
					continue;
				}

				/*
				 * If we don't actually need to draw a line, just put a point:
				 */
				if (current.diagonallyAdjacentOrEqual(previous)) {
					slices[current.z][current.y * width + current.x] = (byte) 255;
				} else {
					/*
					 * Otherwise draw a line with the 3D version of Bresenham's
					 * algorithm:
					 */
					final List<Bresenham3D.IntegerPoint> pointsToDraw = Bresenham3D.bresenham3D(previous, current);
					for (final Bresenham3D.IntegerPoint ip : pointsToDraw) {
						slices[ip.z][ip.y * width + ip.x] = (byte) 255;
					}
				}

				previous = current;
			}
		}
	}

	synchronized PointInImage nearestJoinPointOnSelectedPaths(final double x, final double y, final double z) {

		PointInImage result = null;

		double minimumDistanceSquared = Double.MAX_VALUE;

		final int paths = allPaths.size();

		for (int s = 0; s < paths; ++s) {

			final Path p = allPaths.get(s);

			if (!selectedPathsSet.contains(p))
				continue;

			if (0 == p.size())
				continue;

			final int i = p.indexNearestTo(x * x_spacing, y * y_spacing, z * z_spacing);

			final PointInImage nearestOnPath = p.getPointInImage(i);

			final double distanceSquared = nearestOnPath.distanceSquaredTo(x * x_spacing, y * y_spacing, z * z_spacing);

			if (distanceSquared < minimumDistanceSquared) {
				result = nearestOnPath;
				minimumDistanceSquared = distanceSquared;
			}
		}

		return result;
	}

	@Deprecated
	ArrayList<Path> getAllPaths() {
		return allPaths;
	}

	// Methods we need to implement for UniverseListener:
	@Override
	public void transformationStarted(final View view) {
	}

	@Override
	public void transformationUpdated(final View view) {
	}

	@Override
	public void transformationFinished(final View view) {
	}

	@Override
	public void contentAdded(final Content c) {
	}

	@Override
	public void contentRemoved(final Content c) {
	}

	@Override
	public void contentChanged(final Content c) {
	}

	@Override
	public void contentSelected(final Content c) {
		if (c == null)
			return;
		final String contentName = c.getName();
		final Path selectedPath = getPathFrom3DViewerName(contentName);
		if (plugin != null && selectedPath != null)
			plugin.selectPath(selectedPath, false);
	}

	@Override
	public void canvasResized() {
	}

	@Override
	public void universeClosed() {
		if (plugin != null)
			plugin.use3DViewer = false;
	}
	// ... end of methods for UniverseListener

	public NearPoint nearestPointOnAnyPath(final double x, final double y, final double z, final double distanceLimit) {

		/*
		 * Order all points in all paths by their euclidean distance to (x,y,z):
		 */

		final PriorityQueue<NearPoint> pq = new PriorityQueue<>();

		for (final Path path : allPaths) {
			if (!path.versionInUse())
				continue;
			for (int j = 0; j < path.size(); ++j) {
				pq.add(new NearPoint(x, y, z, path, j));
			}
		}

		while (true) {

			final NearPoint np = pq.poll();
			if (np == null)
				return null;

			/*
			 * Don't bother looking at points that are more than distanceLimit
			 * away. Since we get them in the order closest to furthest away, if
			 * we exceed this limit returned:
			 */

			if (np.distanceToPathPointSquared() > (distanceLimit * distanceLimit))
				return null;

			final double distanceToPath = np.distanceToPathNearPoint();
			if (distanceToPath >= 0)
				return np;
		}
	}

	public AllPointsIterator allPointsIterator() {
		return new AllPointsIterator();
	}

	/*
	 * Note that this returns the number of points in th currently in-use
	 * version of each path.
	 */

	public int pointsInAllPaths() {
		final AllPointsIterator a = allPointsIterator();
		int points = 0;
		while (a.hasNext()) {
			a.next();
			++points;
		}
		return points;
	}

	public class AllPointsIterator implements Iterator<PointInImage> {

		public AllPointsIterator() {
			numberOfPaths = allPaths.size();
			currentPath = null;
			currentPathIndex = -1;
			currentPointIndex = -1;
		}

		int numberOfPaths;
		// These should all be set to be appropriate to the
		// last point that was returned:
		Path currentPath;
		int currentPathIndex;
		int currentPointIndex;

		@Override
		public boolean hasNext() {
			if (currentPath == null || currentPointIndex == currentPath.points - 1) {
				/*
				 * Find out if there is a non-empty path after this:
				 */
				int tmpPathIndex = currentPathIndex + 1;
				while (tmpPathIndex < numberOfPaths) {
					final Path p = allPaths.get(tmpPathIndex);
					if (p.size() > 0 && p.versionInUse())
						return true;
					++tmpPathIndex;
				}
				return false;
			}
			/*
			 * So we know that there's a current path and we're not at the end
			 * of it, so there must be another point:
			 */
			return true;
		}

		@Override
		public PointInImage next() {
			if (currentPath == null || currentPointIndex == currentPath.points - 1) {
				currentPointIndex = 0;
				/* Move to the next non-empty path: */
				while (true) {
					++currentPathIndex;
					if (currentPathIndex == numberOfPaths)
						throw new java.util.NoSuchElementException();
					currentPath = allPaths.get(currentPathIndex);
					if (currentPath.size() > 0 && currentPath.versionInUse())
						break;
				}
			} else
				++currentPointIndex;
			return currentPath.getPointInImage(currentPointIndex);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("AllPointsIterator does not allow the removal of points");
		}

	}

	/*
	 * For each point in *this* PathAndFillManager, find the corresponding point
	 * on the other one. If there's no corresponding one, include a null
	 * instead.
	 */

	public ArrayList<NearPoint> getCorrespondences(final PathAndFillManager other, final double maxDistance) {

		final ArrayList<NearPoint> result = new ArrayList<>();

		final AllPointsIterator i = allPointsIterator();
		while (i.hasNext()) {
			final PointInImage p = i.next();
			final NearPoint np = other.nearestPointOnAnyPath(p.x, p.y, p.z, maxDistance);
			result.add(np);
		}
		return result;
	}

	public static String stringForCSV(final String s) {
		boolean quote = false;
		String result = s;
		if (s.indexOf(',') >= 0)
			quote = true;
		if (s.indexOf('"') >= 0) {
			quote = true;
			result = s.replaceAll("\"", "\"\"");
		}
		if (quote)
			return "\"" + result + "\"";
		else
			return result;
	}

	public static void csvQuoteAndPrint(final PrintWriter pw, final Object o) {
		pw.print(PathAndFillManager.stringForCSV("" + o));
	}

	public void exportFillsAsCSV(final File outputFile) throws IOException {

		final String[] headers = new String[] { "FillID", "SourcePaths", "Threshold", "Metric", "Volume",
				"LengthUnits" };

		final PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(outputFile.getAbsolutePath()), "UTF-8"));
		final int columns = headers.length;
		for (int c = 0; c < columns; ++c) {
			csvQuoteAndPrint(pw, headers[c]);
			if (c < (columns - 1))
				pw.print(",");
		}
		pw.print("\r\n");
		for (int i = 0; i < allFills.size(); ++i) {
			final Fill f = allFills.get(i);
			csvQuoteAndPrint(pw, i);
			pw.print(",");
			csvQuoteAndPrint(pw, f.getSourcePathsStringMachine());
			pw.print(",");
			csvQuoteAndPrint(pw, f.getThreshold());
			pw.print(",");
			csvQuoteAndPrint(pw, f.getMetric());
			pw.print(",");
			csvQuoteAndPrint(pw, f.getVolume());
			pw.print(",");
			csvQuoteAndPrint(pw, f.spacing_units);
			pw.print("\r\n");
		}
		pw.close();
	}

	/*
	 * Output some potentially useful information about the paths as a CSV
	 * (comma separated values) file.
	 */

	public void exportToCSV(final File outputFile) throws IOException {
		// FIXME: also add statistics on volumes of fills and
		// reconstructions...
		final String[] headers = { "PathID", "PathName", "SWCType", "PrimaryPath", "PathLength", "PathLengthUnits", "StartsOnPath",
				"EndsOnPath", "ConnectedPathIDs", "ChildPathIDs", "StartX", "StartY", "StartZ", "EndX", "EndY", "EndZ",
				"ApproximateFittedVolume" };

		final Path[] primaryPaths = getPathsStructured();
		final HashSet<Path> h = new HashSet<>();
		for (int i = 0; i < primaryPaths.length; ++i)
			h.add(primaryPaths[i]);

		final PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(outputFile.getAbsolutePath()), "UTF-8"));
		final int columns = headers.length;
		for (int c = 0; c < columns; ++c) {
			pw.print(stringForCSV(headers[c]));
			if (c < (columns - 1))
				pw.print(",");
		}
		pw.print("\r\n");
		for (final Path p : allPaths) {
			Path pForLengthAndName = p;
			if (p.getUseFitted()) {
				pForLengthAndName = p.fitted;
			}
			if (p.fittedVersionOf != null)
				continue;
			pw.print(stringForCSV("" + p.getID()));
			pw.print(",");
			pw.print(stringForCSV("" + pForLengthAndName.getName()));
			pw.print(",");
			pw.print(stringForCSV("" + Path.getSWCtypeName(p.getSWCType())));
			pw.print(",");
			final boolean primary = h.contains(p);
			pw.print(stringForCSV("" + primary));
			pw.print(",");
			pw.print(stringForCSV("" + pForLengthAndName.getRealLength()));
			pw.print(",");
			pw.print(stringForCSV("" + p.spacing_units));
			pw.print(",");
			if (p.startJoins != null)
				pw.print("" + p.startJoins.getID());
			pw.print(",");
			if (p.endJoins != null)
				pw.print("" + p.endJoins.getID());
			pw.print(",");
			pw.print(stringForCSV(p.somehowJoinsAsString()));
			pw.print(",");
			pw.print(stringForCSV(p.childrenAsString()));
			pw.print(",");

			final double[] startPoint = new double[3];
			final double[] endPoint = new double[3];

			pForLengthAndName.getPointDouble(0, startPoint);
			pForLengthAndName.getPointDouble(pForLengthAndName.size() - 1, endPoint);

			pw.print("" + startPoint[0]);
			pw.print(",");
			pw.print("" + startPoint[1]);
			pw.print(",");
			pw.print("" + startPoint[2]);
			pw.print(",");
			pw.print("" + endPoint[0]);
			pw.print(",");
			pw.print("" + endPoint[1]);
			pw.print(",");
			pw.print("" + endPoint[2]);

			pw.print(",");
			final double fittedVolume = pForLengthAndName.getApproximateFittedVolume();
			if (fittedVolume >= 0)
				pw.print(fittedVolume);
			else
				pw.print("");

			pw.print("\r\n");
			pw.flush();
		}
		pw.close();
	}

	/*
	 * Whatever the state of the paths, update the 3D viewer to make sure that
	 * they're the right colour, the right version (fitted or unfitted) is being
	 * used, whether the line or surface representation is being used, or
	 * whether the path should be displayed at all (it shouldn't if the "Show
	 * only selected paths" option is set.)
	 */

	public void update3DViewerContents() {
		if (plugin != null && !plugin.use3DViewer)
			return;
		final boolean showOnlySelectedPaths = plugin.getShowOnlySelectedPaths();
		// Now iterate over all the paths:
		for (Path p : allPaths) {

			if (p.fittedVersionOf != null)
				continue;

			final boolean selected = p.getSelected();
			final boolean customColor = (p.hasCustomColor && plugin.displayCustomPathColors);
			Color3f color3f;
			if (customColor)
				color3f = new Color3f(p.getColor());
			else if (selected)
				color3f = plugin.selectedColor3f;
			else
				color3f = plugin.deselectedColor3f;

			p.updateContent3D(plugin.univ, // The appropriate 3D universe
					(selected || !showOnlySelectedPaths), // Visible at all?
					plugin.getPaths3DDisplay(), // How to display?
					color3f, plugin.colorImage); // Colour?

			// If path is being rendered with its own custom color, highlight it
			// somehow if is being selected
			if (p.getUseFitted())
				p = p.getFitted();
			if (p.content3D != null)
				p.content3D.setShaded(!(customColor && selected));

		}
	}

	/**
	 * A base class for all the methods we might want to use to transform paths.
	 */

	// Note that this will transform fitted Paths but lose the radiuses

	public PathAndFillManager transformPaths(final PathTransformer transformation, final ImagePlus templateImage) {

		double pixelWidth = 1;
		double pixelHeight = 1;
		double pixelDepth = 1;
		String units = "pixels";

		final Calibration templateCalibration = templateImage.getCalibration();
		if (templateCalibration != null) {
			pixelWidth = templateCalibration.pixelWidth;
			pixelHeight = templateCalibration.pixelHeight;
			pixelDepth = templateCalibration.pixelDepth;
			units = templateCalibration.getUnits();
		}

		final PathAndFillManager pafmResult = new PathAndFillManager(templateImage.getWidth(),
				templateImage.getHeight(), templateImage.getStackSize(), (float) pixelWidth, (float) pixelHeight,
				(float) pixelDepth, units);

		final int[] startJoinsIndices = new int[size()];
		final int[] endJoinsIndices = new int[size()];

		final PointInImage[] startJoinsPoints = new PointInImage[size()];
		final PointInImage[] endJoinsPoints = new PointInImage[size()];

		final Path[] addedPaths = new Path[size()];

		int i = 0;
		for (final Path p : allPaths) {

			final Path startJoin = p.getStartJoins();
			if (startJoin == null) {
				startJoinsIndices[i] = -1;
				endJoinsPoints[i] = null;
			} else {
				startJoinsIndices[i] = allPaths.indexOf(startJoin);
				final PointInImage transformedPoint = p.getStartJoinsPoint().transform(transformation);
				if (transformedPoint.isReal())
					startJoinsPoints[i] = transformedPoint;
			}

			final Path endJoin = p.getEndJoins();
			if (endJoin == null) {
				endJoinsIndices[i] = -1;
				endJoinsPoints[i] = null;
			} else {
				endJoinsIndices[i] = allPaths.indexOf(endJoin);
				final PointInImage transformedPoint = p.getEndJoinsPoint().transform(transformation);
				if (transformedPoint.isReal())
					endJoinsPoints[i] = transformedPoint;
			}

			final Path transformedPath = p.transform(transformation, templateImage, imagePlus);
			if (transformedPath.size() >= 2) {
				addedPaths[i] = transformedPath;
				pafmResult.addPath(transformedPath);
			}

			++i;
		}

		for (i = 0; i < size(); ++i) {
			final int si = startJoinsIndices[i];
			final int ei = endJoinsIndices[i];
			if (addedPaths[i] != null) {
				if (si >= 0 && addedPaths[si] != null && startJoinsPoints[i] != null)
					addedPaths[i].setStartJoin(addedPaths[si], startJoinsPoints[i]);
				if (ei >= 0 && addedPaths[ei] != null && endJoinsPoints[i] != null)
					addedPaths[i].setEndJoin(addedPaths[ei], endJoinsPoints[i]);
			}
		}

		return pafmResult;
	}

	public void downsampleAll(final double maximumPermittedDistance) {
		for (final Path p : allPaths) {
			p.downsample(maximumPermittedDistance);
		}
	}
}
