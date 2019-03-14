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
package tracing.annotation;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.scijava.util.ColorRGB;

import tracing.SNT;
import tracing.viewer.OBJMesh;

/**
 * Defines an Allen Reference Atlas (ARA) [Allen Mouse Common Coordinate
 * Framework] annotation.
 * 
 * @author tferr
 *
 */
public class AllenCompartment implements BrainAnnotation {

	private String name;
	private String acronym;
	private String[] aliases;
	private int structureId;
	private final UUID uuid;
	protected JSONObject jsonObj;

	public AllenCompartment(final UUID uuid) {
		this(null, uuid);
	}

	protected AllenCompartment(final JSONObject jsonObj, final UUID uuid) {
		this.jsonObj = jsonObj;
		this.uuid = uuid;
	}

	private void loadJsonObj() {
		if (jsonObj != null) return;
		final JSONArray areaList = AllenUtils.getBrainAreasList();
		for (int n = 0; n < areaList.length(); n++) {
			final JSONObject area = (JSONObject) areaList.get(n);
			final UUID id = UUID.fromString(area.getString("id"));
			if (uuid.equals(id)) {
				jsonObj = area;
				break;
			}
		}
	}

	private void initializeAsNeeded() {
		if (name != null) return;
		loadJsonObj();
		name = jsonObj.getString("name");
		acronym = jsonObj.getString("acronym");
		structureId = jsonObj.optInt("structureId");
	}

	private String[] getArray(final JSONArray jArray) {
		final String[] array = new String[jArray.length()];
		for (int i = 0; i < jArray.length(); i++) {
			array[i] = (String) jArray.get(i);
		}
		return array;
	}

	protected int depth() {
		initializeAsNeeded();
		return jsonObj.getInt("depth");
	}

	protected int graphOrder() {
		return jsonObj.getInt("graphOrder");
	}

	protected String getStructureIdPath() {
		return jsonObj.optString("structureIdPath");
	}

	protected int getParentStructureId() {
		return jsonObj.optInt("parentStructureId");
	}

	public UUID getUUID() {
		return uuid;
	}

	@Override
	public int id() {
		initializeAsNeeded();
		return structureId;
	}

	@Override
	public String name() {
		initializeAsNeeded();
		return name;
	}

	@Override
	public String acronym() {
		initializeAsNeeded();
		return acronym;
	}

	@Override
	public String[] aliases() {
		initializeAsNeeded();
		aliases = getArray(jsonObj.getJSONArray("aliases"));
		return aliases;
	}

	@Override
	public OBJMesh getMesh() {
		initializeAsNeeded();
		final ColorRGB geometryColor = ColorRGB.fromHTMLColor("#" + jsonObj.getString("geometryColor"));
		OBJMesh mesh = null;
		try {
			final URL url = new URL("https://ml-neuronbrowser.janelia.org/static/allen/obj/" + jsonObj.getString("geometryFile"));
			mesh = new OBJMesh(url);
			mesh.setColor(geometryColor, 87.5f);
			mesh.setLabel(name);
		} catch (MalformedURLException | JSONException e) {
			SNT.error("Could not retrieve mesh ", e);
		}
		return mesh;
	}

	@Override
	public String toString() {
		return name + " [" + acronym + "]";
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this)
			return true;
		if (!(o instanceof AllenCompartment))
			return false;
		return uuid.equals(((AllenCompartment) o).uuid);
	}

}
