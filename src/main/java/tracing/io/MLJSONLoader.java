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

package tracing.io;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import tracing.Path;
import tracing.SNT;
import tracing.SWCPoint;

/**
 * Importer for retrieving reconstructions from MouseLight's online database at
 * <a href=
 * "https://ml-neuronbrowser.janelia.org/">ml-neuronbrowser.janelia.org</a>
 *
 * @author Tiago Ferreira
 */
public class MLJSONLoader {

	/** The Constant AXON. */
	public static final String AXON = "axon";

	/** The Constant DENDRITE. */
	public static final String DENDRITE = "dendrite";

	private final static String TRACINGS_URL = "https://ml-neuronbrowser.janelia.org/tracings";
	private final static String GRAPHQL_URL = "https://ml-neuronbrowser.janelia.org/graphql";
	private final static String GRAPHQL_BODY = "{\n" + //
			"    \"query\": \"query QueryData($filters: [FilterInput!]) {\\n  queryData(filters: $filters) {\\n    totalCount\\n    queryTime\\n    nonce\\n    error {\\n      name\\n      message\\n      __typename\\n    }\\n    neurons {\\n      id\\n      idString\\n      brainArea {\\n        id\\n        acronym\\n        __typename\\n      }\\n      tracings {\\n        id\\n        tracingStructure {\\n          id\\n          name\\n          value\\n          __typename\\n        }\\n        soma {\\n          id\\n          x\\n          y\\n          z\\n          radius\\n          parentNumber\\n          sampleNumber\\n          brainAreaId\\n          structureIdentifierId\\n          __typename\\n        }\\n        __typename\\n      }\\n      __typename\\n    }\\n    __typename\\n  }\\n}\\n\",\n"
			+ "    \"variables\": {\n" + //
			"        \"filters\": [\n" + //
			"            {\n" + //
			"            	\"tracingIdsOrDOIs\": [\"%s\"],\n" + //
			"                \"tracingIdsOrDOIsExactMatch\": true,\n" + //
			"                \"tracingStructureIds\": [\"\"],\n" + //
			"                \"nodeStructureIds\": [\"\"],\n" + //
			"                \"operatorId\": \"\",\n" + //
			"                \"amount\": null,\n" + //
			"                \"brainAreaIds\": [\"\"],\n" + //
			"                \"arbCenter\": {\n" + //
			"                    \"x\": null,\n" + //
			"                    \"y\": null,\n" + //
			"                    \"z\": null\n" + //
			"                },\n" + //
			"                \"arbSize\": null,\n" + //
			"                \"invert\": false,\n" + //
			"                \"composition\": null,\n" + //
			"                \"nonce\": \"\"\n" + //
			"            }\n" + //
			"        ]\n" + //
			"    },\n" + //
			"    \"operationName\": \"QueryData\"\n" + //
			"}";

	/* Maps tracingStructure name to tracingStructure id */
	private Map<String, UUID> nameMap;
	/* Maps tracingStructure id to its SWC-type flag */
	private Map<UUID, Integer> swcTypeMap;
	/* The 1-point soma of the cell */
	private SWCPoint soma = null;
	/* The public id or DOI of the cell */
	private final String publicID;
	private boolean initialized = false;

	/**
	 * Instantiates a new loader.
	 *
	 * @param id the neuron id (e.g., "AA0001") or DOI (e.g.,
	 *           "10.25378/janelia.5527672") of the neuron to be loaded
	 */
	public MLJSONLoader(final String id) {
		this.publicID = id;
	}

	private void initialize() {
		try {
			final OkHttpClient client = new OkHttpClient();
			final MediaType mediaType = MediaType.parse("application/json");
			final RequestBody body = RequestBody.create(mediaType, String.format(GRAPHQL_BODY, publicID));
			final Request request = new Request.Builder().url(GRAPHQL_URL).post(body)
					.addHeader("Content-Type", "application/json").addHeader("Cache-Control", "no-cache").build();
			final Response response = client.newCall(request).execute();
			final String resStr = response.body().string().toString();
			response.close();
			// Parse response
			final JSONObject json = new JSONObject(resStr);
			final JSONArray neuronsArray = json.getJSONObject("data").getJSONObject("queryData")
					.getJSONArray("neurons");
			if (neuronsArray == null || neuronsArray.length() == 0)
				throw new JSONException("Empty neurons array");

			nameMap = new HashMap<>();
			for (int n = 0; n < neuronsArray.length(); n++) {
				final JSONArray tracingsArray = neuronsArray.getJSONObject(n).getJSONArray("tracings");

				for (int t = 0; t < tracingsArray.length(); t++) {
					final JSONObject compartment = tracingsArray.getJSONObject(t);
					if (soma == null) {
						final JSONObject jsonSoma = compartment.getJSONObject("soma");
						final double sX = jsonSoma.getDouble("x");
						final double sY = jsonSoma.getDouble("y");
						final double sZ = jsonSoma.getDouble("z");
						final double sRadius = jsonSoma.getDouble("radius");
						final int parent = jsonSoma.getInt("parentNumber"); // always -1
						soma = new SWCPoint(0, Path.SWC_SOMA, sX, sY, sZ, sRadius, parent);
					}
					final JSONObject tracingStructure = compartment.getJSONObject("tracingStructure");
					final String name = tracingStructure.getString("name");
					final UUID compartmentID = UUID.fromString(compartment.getString("id"));
					nameMap.put(name, compartmentID);
				}
			}
			assembleSwcTypeMap();
		} catch (final IOException | JSONException | IllegalArgumentException exc) {
			SNT.error("Failed to initialize loader", exc);
			initialized = false;
		}
		initialized = true;
		if (SNT.isDebugMode()) {
			SNT.log("Retrieving compartment UUIDs for ML neuron " + publicID);
			for (final Entry<String, UUID> entry : nameMap.entrySet()) {
				SNT.log(entry.toString());
			}
		}
	}

	private void assembleSwcTypeMap() throws IllegalArgumentException {
		swcTypeMap = new HashMap<>();
		for (final Entry<String, UUID> entry : nameMap.entrySet()) {
			final String key = entry.getKey();
			if (key == null)
				throw new IllegalArgumentException("structureIdentifierId UUIDs unset");
			switch (key) {
			case AXON:
				swcTypeMap.put(entry.getValue(), Path.SWC_AXON);
				break;
			case DENDRITE:
				swcTypeMap.put(entry.getValue(), Path.SWC_DENDRITE);
				break;
			default:
				swcTypeMap.put(entry.getValue(), Path.SWC_UNDEFINED);
				break;
			}
		}
	}

	/**
	 * Checks whether a connection to the MouseLight database can be established.
	 *
	 * @return true, if an HHTP connection could be established
	 */
	public static boolean isDatabaseAvailable() {
		boolean success;
		Response response = null;
		try {
			final OkHttpClient client = new OkHttpClient();
			final Request request = new Request.Builder().url(TRACINGS_URL).build();
			response = client.newCall(request).execute();
			success = response.isSuccessful();
		} catch (final IOException ignored) {
			success = false;
		} finally {
			if (response != null)
				response.close();
		}
		return success;
	}

	/**
	 * Gets a compartment of the loaded cell.
	 *
	 * @param structure compartment (either {@link AXON} or {@link DENDRITE})
	 * @return the specified compartment as a JSON object
	 * @see {@link#getNodes()}, {@link#getSoma()}, {@link #getAxonNodes()},
	 *      {@link #getDendriteNodes()}
	 */
	public JSONObject getCompartment(final String structure) {
		if (!initialized)
			initialize();
		final UUID structureID = nameMap.get(structure.toLowerCase());
		if (structureID == null)
			throw new IllegalArgumentException("Structure name not recognized: " + structure);

		final OkHttpClient client = new OkHttpClient();
		final MediaType mediaType = MediaType.parse("application/json");
		final RequestBody body = RequestBody.create(mediaType, "{\n\"ids\": [\n\"" + structureID + "\"\n]\n}");
		try {
			final Request request = new Request.Builder().url(TRACINGS_URL).post(body)
					.addHeader("Content-Type", "application/json").addHeader("Cache-Control", "no-cache").build();
			final Response response = client.newCall(request).execute();
			final String resStr = response.body().string().toString();
			response.close();
			return new JSONObject(resStr);
		} catch (final IOException | JSONException exc) {
			exc.printStackTrace();
			return null;
		}
	}

	/**
	 * Extracts the nodes (single-point soma, axonal and dendritic arbor) of the
	 * loaded neuron.
	 *
	 * @return the list of nodes of the neuron as {@link SWCPoint}s. Note that the
	 *         first point in the set (the soma) has an SWC sample number of 0.
	 * @see {@link#getSoma()}, {@link #getAxonNodes()}, {@link #getDendriteNodes()}
	 */
	public TreeSet<SWCPoint> getNodes() {
		if (!initialized)
			initialize();
		final TreeSet<SWCPoint> points = new TreeSet<>();
		if (soma != null)
			points.add(soma);
		int idOffset = 0;
		for (final Entry<String, UUID> entry : nameMap.entrySet()) {
			final JSONObject c = getCompartment(entry.getKey());
			this.assignNodes(c, points, idOffset);
			idOffset += points.last().getId();
		}
		return points;
	}

	/**
	 * Extracts the nodes of the axonal arbor of loaded neuron.
	 *
	 * @return the list of nodes of the axonal arbor as {@link SWCPoint}s.
	 * @see {@link#getNodes()}, {@link#getSoma()}, {@link #getDendriteNodes()}
	 */
	public TreeSet<SWCPoint> getAxonNodes() {
		return getNodes(MLJSONLoader.AXON);
	}

	/**
	 * Extracts the nodes of the dendritic arbor of loaded neuron.
	 *
	 * @return the list of nodes of the dendritic arbor as {@link SWCPoint}s.
	 * @see {@link#getNodes()}, {@link#getSoma()}, {@link #getAxonNodes()}
	 */
	public TreeSet<SWCPoint> getDendriteNodes() {
		return getNodes(MLJSONLoader.DENDRITE);
	}

	private TreeSet<SWCPoint> getNodes(final String compartment) {
		if (!initialized)
			initialize();
		final TreeSet<SWCPoint> points = new TreeSet<>();
//		if (!nameMap.containsKey(compartment.toLowerCase()))
//			throw new IllegalArgumentException("Invalid compartment" + compartment);
		this.assignNodes(getCompartment(compartment), points);
		return points;
	}

	/**
	 * Retrieves the soma (single-point representation) of the loaded neuron.
	 *
	 * @return the soma as {@link SWCPoint}. Note that point has an SWC sample
	 *         number of 0.
	 * @see {@link#getNodes()}, {@link #getAxonNodes()}, {@link #getDendriteNodes()}
	 * 
	 */
	public SWCPoint getSoma() {
		if (!initialized)
			initialize();
		return soma;
	}

	private void assignNodes(final JSONObject compartment, final TreeSet<SWCPoint> points) {
		assignNodes(compartment, points, 0);
	}

	private void assignNodes(final JSONObject compartment, final TreeSet<SWCPoint> points, final int idOffset) {
		if (compartment == null)
			throw new IllegalArgumentException("Cannot extract nodes from null compartment");
		if (!initialized)
			initialize();
		try {
			final JSONArray tracings = compartment.getJSONArray("tracings");
			for (int traceIdx = 0; traceIdx < tracings.length(); traceIdx++) {
				final JSONObject tracing = tracings.getJSONObject(traceIdx);
				final int type = getSWCflag(UUID.fromString(tracing.getString("id")));
				final JSONArray nodesArray = tracing.getJSONArray("nodes");
				for (int nodeIdx = 0; nodeIdx < nodesArray.length(); nodeIdx++) {
					final JSONObject node = (JSONObject) nodesArray.get(nodeIdx);
					final int sn = idOffset + node.getInt("sampleNumber");
					final double x = node.getDouble("x");
					final double y = node.getDouble("y");
					final double z = node.getDouble("z");
					final double radius = node.getDouble("radius");
					int parent = node.getInt("parentNumber");
					if (parent > -1)
						parent += idOffset;
					points.add(new SWCPoint(sn, type, x, y, z, radius, parent));
				}
			}

		} catch (final JSONException exc) {
			SNT.error("Error while extracting nodes", exc);
		}
	}

	/* Gets the SWC type flag from a JSON 'tracingStructure' UUID */
	private int getSWCflag(final UUID tracingStructureUUID) {
		try {
			return swcTypeMap.get(tracingStructureUUID);
		} catch (final NullPointerException nep) {
			return Path.SWC_UNDEFINED;
		}
	}

	/* IDE debug method */
	public static void main(final String... args) {
		System.out.println("# Retrieving neuron");
		final String id = "10.25378/janelia.5527672";
		final MLJSONLoader loader = new MLJSONLoader(id);
		try (PrintWriter out = new PrintWriter("/home/tferr/Desktop/"+id.replaceAll("/", "-") +".swc")) {
			final StringReader reader = SWCPoint.collectionAsReader(loader.getNodes());
			try (BufferedReader br = new BufferedReader(reader)) {
				br.lines().forEach(out::println);
			} catch (final IOException e) {
				e.printStackTrace();
			}
			out.println("# End of Tree ");
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		}
		System.out.println("# All done");
	}

}
