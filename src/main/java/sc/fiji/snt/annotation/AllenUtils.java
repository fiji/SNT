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
package sc.fiji.snt.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.scijava.util.ColorRGB;

import sc.fiji.snt.Tree;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SNTPoint;
import sc.fiji.snt.viewer.OBJMesh;

/**
 * Utility methods for accessing/handling {@link AllenCompartment}s
 * 
 * @author Tiago Ferreira
 */
public class AllenUtils {

	/** The version of the Common Coordinate Framework currently used by SNT */
	public static final int VERSION = 3;
	protected static final int BRAIN_ROOT_ID = 997;
	private final static PointInImage BRAIN_BARYCENTRE = new PointInImage(5687.5435f, 3849.6099f, 6595.3813f);

	private static JSONArray areaList;

	private AllenUtils() {
	}

	private static JSONObject getJSONfile(final String resourcePath) {
		final ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		final InputStream is = classloader.getResourceAsStream(resourcePath);
		final JSONTokener tokener = new JSONTokener(is);
		final JSONObject json = new JSONObject(tokener);
		try {
			is.close();
		} catch (final IOException ignored) {// do nothing

		}
		return json;
	}

	protected static JSONArray getBrainAreasList() {
		if (areaList == null) {
			final JSONObject json = getJSONfile("ml/brainAreas.json");
			areaList = json.getJSONObject("data").getJSONArray("brainAreas");
		}
		return areaList;
	}

	@SuppressWarnings("unused")
	private static AllenCompartment getCompartment(final UUID uuid) {
		areaList = getBrainAreasList();
		for (int n = 0; n < areaList.length(); n++) {
			final JSONObject area = (JSONObject) areaList.get(n);
			final UUID areaId = UUID.fromString(area.getString("id"));
			if (uuid.equals(areaId)) {
				return new AllenCompartment(area, areaId);
			}
		}
		return null;
	}

	/**
	 * Constructs a compartment from its CCF id
	 *
	 * @param id the integer identifier
	 * @return the compartment matching the id or null if id is not valid
	 */
	public static AllenCompartment getCompartment(final int id) {
		areaList = getBrainAreasList();
		for (int n = 0; n < areaList.length(); n++) {
			final JSONObject area = (JSONObject) areaList.get(n);
			if (area.optInt("structureId") == id) {
				return new AllenCompartment(area, UUID.fromString(area.getString("id")));
			}
		}
		return null;
	}

	/**
	 * Constructs a compartment from its CCF name or acronym
	 *
	 * @param nameOrAcronym the name or acronym (case insensitive) identifying the
	 *                      compartment
	 * @return the compartment whose name or acronym matches the specified string or
	 *         null if no match was found
	 */
	public static AllenCompartment getCompartment(final String nameOrAcronym) {
		areaList = getBrainAreasList();
		for (int n = 0; n < areaList.length(); n++) {
			final JSONObject area = (JSONObject) areaList.get(n);
			if (nameOrAcronym.equalsIgnoreCase(area.getString("name"))
					|| nameOrAcronym.equalsIgnoreCase(area.getString("acronym"))) {
				return new AllenCompartment(area, UUID.fromString(area.getString("id")));
			}
		}
		return null;
	}

	public static void assignToLeftHemisphere(final Tree tree) {
		final PointInImage root = tree.getRoot();
		if (root == null || isLeftHemisphere(root)) return;
		tree.translate(-2 * (root.getX() - BRAIN_BARYCENTRE.x), 0, 0);
	}

	public static void assignToRightHemisphere(final Tree tree) {
		final PointInImage root = tree.getRoot();
		if (root == null || !isLeftHemisphere(root)) return;
		tree.translate(2 * (BRAIN_BARYCENTRE.x - root.getX()), 0, 0);
	}

	/**
	 * Checks the hemisphere a neuron belongs to.
	 *
	 * @param tree the Tree to be tested
	 * @return true, if soma (or root node) is in the left hemisphere, false
	 *         otherwise
	 */
	public static boolean isLeftHemisphere(final Tree tree) {
		return isLeftHemisphere(tree.getRoot());
	}

	/**
	 * Checks the hemisphere a reconstruction node belongs to.
	 *
	 * @param point the point
	 * @return true, if is left hemisphere, false otherwise
	 */
	public static boolean isLeftHemisphere(final SNTPoint point) {
		return point.getX() <= BRAIN_BARYCENTRE.x;
	}

	/**
	 * Returns the spatial centroid of the Allen CCF.
	 *
	 * @return the SNT point defining the (X,Y,Z) center of the ARA
	 */
	public static SNTPoint brainCenter() {
		return BRAIN_BARYCENTRE;
	}

	/**
	 * Gets the maximum number of ontology levels in the Allen CCF.
	 * 
	 * @return the max number of ontology levels.
	 */
	public static int getHighestOntologyDepth() {
		int maxLevel = 0;
		for (final AllenCompartment c : getOntologies()) {
			maxLevel = Math.max(maxLevel, c.getOntologyDepth());
		}
		return maxLevel;
	}

	/**
	 * Retrieves the Allen CCF hierarchical tree data.
	 *
	 * @param meshesOnly Whether only compartments with known meshes should be included
	 * @return the Allen CCF tree data model
	 */
	public static DefaultTreeModel getTreeModel(final boolean meshesOnly) {
		return new AllenTreeModel().getTreeModel(meshesOnly);
	}

	/**
	 * Gets the Allen CCF as a flat (non-hierarchical) collection of ontologies.
	 *
	 * @return the "flattened" ontologies list
	 */
	public static Collection<AllenCompartment> getOntologies() {
		return new AllenTreeModel().getOntologies();
	}

	private static class AllenTreeModel {

		private static final int ROOT_ID = 997;
		private final JSONArray areaList;
		private DefaultMutableTreeNode root;

		private AllenTreeModel() {
			areaList = getBrainAreasList();
		}

		private AllenCompartment getAreaListCompartment(final int idx) {
			final JSONObject area = (JSONObject) areaList.get(idx);
			final AllenCompartment ac = new AllenCompartment(UUID.fromString(area.getString("id")));
			return ac;
		}

		private Collection<AllenCompartment> getOntologies() {
			final Collection<AllenCompartment> list = new ArrayList<>(areaList.length());
			for (int n = 0; n < areaList.length(); n++) {
				list.add(getAreaListCompartment(n));
			}
			return list;
		}

		private DefaultTreeModel getTreeModel(final boolean meshesOnly) {
			final TreeSet<AllenCompartment> all = new TreeSet<>((ac1, ac2)->ac1.getStructureIdPath().compareTo(ac2.getStructureIdPath()));
			final Map<Integer, AllenCompartment> idsMap = new HashMap<>();
			final Set<Integer> visitedIds = new HashSet<>();
			root = new DefaultMutableTreeNode();
			for (int n = 0; n < areaList.length(); n++) {
				final AllenCompartment ac = getAreaListCompartment(n);
				if (ac.id() == ROOT_ID) {
					root.setUserObject(ac);
					visitedIds.add(ac.id());
				} else {
					idsMap.put(ac.id(), ac);
				}
				all.add(ac);
			}

			for (final AllenCompartment ac : all) {
				final String path = ac.getStructureIdPath();
				DefaultMutableTreeNode node = root;
				for (final String structureID : path.split("/")) {
					if (structureID.isEmpty())
						continue;
					final int id = Integer.parseInt(structureID);
					if (visitedIds.contains(id))
						continue;
					final AllenCompartment c = idsMap.get(id);
					if (meshesOnly && !c.isMeshAvailable()) continue;
					final AllenCompartment pc = idsMap.get(c.getParentStructureId());
					final DefaultMutableTreeNode parentNode = getParentNode(pc);
					if (parentNode!=null) {
						node = parentNode;
					}
					node.add(new DefaultMutableTreeNode(c));
					visitedIds.add(id);
				}
			}
			assert(visitedIds.size()==idsMap.size()+1);
			return new DefaultTreeModel(root);
		}

		private DefaultMutableTreeNode getParentNode(final AllenCompartment parentStructure) {
			@SuppressWarnings("unchecked")
			final Enumeration<TreeNode> en = root.depthFirstEnumeration();
			while (en.hasMoreElements()) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) en.nextElement();
				final AllenCompartment structure = (AllenCompartment)node.getUserObject();
				if (structure.equals(parentStructure)) {
					return node;
				}
			}
			return null;
		}
	}

	/**
	 * Retrieves the surface contours for the Allen Mouse Brain Atlas (CCF), bundled
	 * with SNT.
	 *
	 * @param color the color to be assigned to the mesh
	 * @return a reference to the retrieved mesh
	 */
	public static OBJMesh getRootMesh(final ColorRGB color) {
		final String meshLabel = "Whole Brain";
		final String meshPath = "meshes/MouseBrainAllen.obj";
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		final URL url = loader.getResource(meshPath);
		if (url == null)
			throw new IllegalArgumentException(meshLabel + " not found");
		final OBJMesh mesh = new OBJMesh(url, "um");
		mesh.setColor(color, 95f);
		mesh.setLabel(meshLabel);
		return mesh;
	}

	/* IDE Debug method */
	public static void main(final String[] args) {
		final AllenCompartment compartmentOfInterest = AllenUtils.getCompartment("CA3");
		System.out.println(compartmentOfInterest);
		System.out.println(compartmentOfInterest.getTreePath());
		final javax.swing.JTree tree = new javax.swing.JTree(getTreeModel(false));
		final javax.swing.JScrollPane treeView = new javax.swing.JScrollPane(tree);
		final javax.swing.JFrame f = new javax.swing.JFrame();
		f.add(treeView);
		f.pack();
		f.setVisible(true);
	}

}
