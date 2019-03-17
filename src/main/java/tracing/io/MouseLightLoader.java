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

package tracing.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.TreeSet;

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
import tracing.annotation.AllenCompartment;
import tracing.util.SWCPoint;

/**
 * Methods for retrieving reconstructions from MouseLight's online database at
 * <a href=
 * "https://ml-neuronbrowser.janelia.org/">ml-neuronbrowser.janelia.org</a> *
 *
 * @author Tiago Ferreira
 */
public class MouseLightLoader {

	/** The Constant AXON. */
	public static final String AXON = MouseLightQuerier.AXON;

	/** The Constant DENDRITE. */
	public static final String DENDRITE = MouseLightQuerier.DENDRITE;

	/** The Constant SOMA. */
	public static final String SOMA = MouseLightQuerier.SOMA;

	private static final String JSON_URL = "https://ml-neuronbrowser.janelia.org/json";
	private static final String SWC_URL = "https://ml-neuronbrowser.janelia.org/swc";
	private static final int MIN_CHARS_IN_VALID_RESPONSE_BODY = 150;

	private final String id;
	private JSONObject jsonData;

	/**
	 * Instantiates a new loader.
	 *
	 * @param id the neuron id (e.g., "AA0001"). Note that DOIs are not allowed
	 */
	public MouseLightLoader(final String id) {
		this.id = id;
	}

	private Response getResponse(final String url) throws IOException {
		final OkHttpClient client = new OkHttpClient();
		final MediaType mediaType = MediaType.parse("application/json");
		final RequestBody body = RequestBody.create(mediaType, "{\"ids\": [\"" + id + "\"]}");
		final Request request = new Request.Builder() //
				.url(url) //
				.post(body).addHeader("Content-Type", "application/json") //
				.addHeader("cache-control", "no-cache") //
				.build(); //
		return client.newCall(request).execute();
	}

	private static boolean validDirectory(final File directory) {
		if (!directory.isDirectory()) {
			SNT.log("Invalid directory path: " + directory);
			return false;
		}
		try {
			if (!directory.exists())
				directory.mkdirs();
		} catch (final SecurityException ex) {
			SNT.error("Could not create path", ex);
		}
		return directory.exists();
	}

	private JSONObject getJSON(final String url) {
		try {
			final Response response = getResponse(url);
			final String responseBody = response.body().string();
			if (responseBody.length() < MIN_CHARS_IN_VALID_RESPONSE_BODY) {
				return null;
			}
			final JSONObject json = new JSONObject(responseBody);
			if (json != null && url.equals(JSON_URL)
					&& json.getJSONObject("contents").getJSONArray("neurons").isEmpty())
				return null;
			return json;
		} catch (final IOException e) {
			SNT.error("Failed to retrieve id " + id, e);
		}
		return null;
	}

	/**
	 * Extracts the nodes (single-point soma, axonal and dendritic arbor) of the
	 * loaded neuron.
	 *
	 * @return the list of nodes of the neuron as {@link SWCPoint}s.
	 * @throws IllegalArgumentException if compartment is not recognized or it was
	 *                                  not possible to retrieve data
	 */
	public TreeSet<SWCPoint> getNodes() throws IllegalArgumentException {
		return getNodes("all");
	}

	/**
	 * Script-friendly method to extract the nodes of a compartment.
	 *
	 * @param compartment 'soma', 'axon', 'dendrite', 'all' (case insensitive)
	 * @return the list of nodes of the neuron as {@link SWCPoint}s.
	 * @throws IllegalArgumentException if compartment is not recognized or it was
	 *                                  not possible to retrieve data
	 */
	public TreeSet<SWCPoint> getNodes(final String compartment) throws IllegalArgumentException {
		if (compartment == null || compartment.trim().isEmpty())
			throw new IllegalArgumentException("Invalid compartment" + compartment);
		jsonData = getJSON();
		if (jsonData == null) return null;
		final JSONObject neuron = jsonData.getJSONObject("contents").getJSONArray("neurons").optJSONObject(0);
		if (neuron == null) return null;
		final TreeSet<SWCPoint> nodes = new TreeSet<>();
		switch (compartment.toLowerCase()) {
		case SOMA:
			// single point soma
			final JSONObject sNode = neuron.getJSONObject("soma");
			nodes.add(jsonObjectToSWCPoint(sNode, Path.SWC_SOMA));
			break;
		case DENDRITE:
			final JSONArray dNodeList = neuron.getJSONArray(DENDRITE);
			for (int n = 0; n < dNodeList.length(); n++) {
				nodes.add(jsonObjectToSWCPoint(dNodeList.getJSONObject(n), Path.SWC_DENDRITE));
			}
			break;
		case AXON:
			final JSONArray aNodeList = neuron.getJSONArray(AXON);
			for (int n = 0; n < aNodeList.length(); n++) {
				nodes.add(jsonObjectToSWCPoint(aNodeList.getJSONObject(n), Path.SWC_AXON));
			}
			break;
		case "all":
			int sn = 1;
			final JSONObject somaNode = neuron.getJSONObject("soma");
			nodes.add(jsonObjectToSWCPoint(somaNode, Path.SWC_SOMA));
			sn++;
			final JSONArray dendriteList = neuron.getJSONArray(DENDRITE);
			for (int n = 1; n < dendriteList.length(); n++) {
				final SWCPoint node = jsonObjectToSWCPoint(dendriteList.getJSONObject(n), Path.SWC_DENDRITE);
				node.id = sn++;
				nodes.add(node);
			}
			final JSONArray axonList = neuron.getJSONArray(AXON);
			final int parentOffset = nodes.size() - 1;
			for (int n = 1; n < axonList.length(); n++) {
				final SWCPoint node = jsonObjectToSWCPoint(axonList.getJSONObject(n), Path.SWC_AXON);
				if (n > 1) node.parent += parentOffset;
				node.id = sn++;
				nodes.add(node);
			}
			break;
		default:
			throw new IllegalArgumentException(compartment + " is not a valid argument");
		}
		return nodes;
	}

	private SWCPoint jsonObjectToSWCPoint(final JSONObject node, final int swcType) {
		final int sn = node.optInt("sampleNumber", 1);
		final double x = node.getDouble("x");
		final double y = node.getDouble("y");
		final double z = node.getDouble("z");
		final double radius = node.optDouble("radius", 1);
		final int parent = node.optInt("parentNumber", -1);
		final SWCPoint point = new SWCPoint(sn, swcType, x, y, z, radius, parent);
		point.setLabel(new AllenCompartment(node.getInt("allenId")));
		return point;
	}

	/**
	 * Gets all the data associated with this reconstruction as a JSON object.
	 * 
	 * @return the JSON data (null if data could not be retrieved).
	 */
	public JSONObject getJSON() {
		if (jsonData == null) jsonData = getJSON(JSON_URL);
		return jsonData;
	}

	/**
	 * Checks whether the neuron to be loaded was found in the database.
	 *
	 * @return true, if the neuron id specified in the constructor was found in the
	 *         database
	 */
	public boolean idExists() {
		return getJSON() != null;
	}

	/**
	 * Gets all the data associated with this reconstruction in the SWC format.
	 * 
	 * @return the SWC data (null if data could not be retrieved).
	 */
	public String getSWC() {
		final JSONObject json = getJSON(SWC_URL);
		if (json == null) return null;
		final String jsonContents = json.get("contents").toString();
		final byte[] decodedContents = Base64.getDecoder().decode(jsonContents);
		return new String(decodedContents, StandardCharsets.UTF_8);
	}

	/**
	 * Convenience method to save SWC data to a local directory.
	 *
	 * @param outputDirectory the output directory
	 * @return true, if file was successful saved. If saving failed, exceptions
	 *         messages are logged to the console window when running SNT in debug
	 *         mode
	 */
	public boolean saveAsSWC(final String outputDirectory) throws IllegalArgumentException {
		final File dir = new File(outputDirectory);
		if (!validDirectory(dir)) return false;
		final File file = new File(outputDirectory + id + ".swc");
		return saveJSONContents(getSWC(), file);
	}

	/**
	 * Convenience method to save JSON data to a local directory.
	 *
	 * @param outputDirectory the output directory
	 * @return true, if file was successful saved. If saving failed, exceptions
	 *         messages are logged to the console window when running SNT in debug
	 *         mode
	 */
	public boolean saveAsJSON(final String outputDirectory) throws IllegalArgumentException {
		final File dir = new File(outputDirectory);
		if (!validDirectory(dir)) return false;
		final File file = new File(dir, id + ".json");
		final JSONObject json = getJSON();
		if (json == null) return false;
		final String jsonContents = json.getJSONObject("contents").toString();
		return saveJSONContents(jsonContents, file);
	}

	private static boolean saveJSONContents(final String jsonContents, final File file) {
		try {
			if (jsonContents == null) {
				throw new IOException("Id(s) not found!?");
			}
			final PrintWriter out = new PrintWriter(file);
			out.print(jsonContents);
			out.close();
		} catch (SecurityException | IOException ex) {
			SNT.error("Could not save requested data", ex);
			return false;
		}
		return true;
	}

	/**
	 * Checks whether a connection to the MouseLight database can be established.
	 *
	 * @return true, if an HHTP connection could be established
	 */
	public static boolean isDatabaseAvailable() {
		return MouseLightQuerier.isDatabaseAvailable();
	}

	/**
	 * Gets the number of cells publicly available in the MouseLight database.
	 *
	 * @return the number of available cells, or -1 if the database could not be
	 *         reached.
	 */
	public static int getNeuronCount() {
		int count = -1;
		final OkHttpClient client = new OkHttpClient();
		final MediaType mediaType = MediaType.parse("application/json");
		final RequestBody body = RequestBody.create(mediaType, "{\"query\":\"{systemSettings{neuronCount}}\"}");
		final Request request = new Request.Builder() //
				.url("https://ml-neuronbrowser.janelia.org/graphql") //
				.post(body) //
				.addHeader("Content-Type", "application/json") //
				.addHeader("cache-control", "no-cache") //
				.build();
		try {
			final Response response = client.newCall(request).execute();
			final JSONObject json = new JSONObject(response.body().string());
			count = json.getJSONObject("data").getJSONObject("systemSettings").getInt("neuronCount");
		} catch (IOException | JSONException ignored) {
			// do nothing
		}
		return count;
	}

	/* IDE debug method */
	public static void main(final String... args) {
		final String dir = "/home/tferr/Desktop/testjson/";
		final String id = "AA0360";
		System.out.println("# starting");
		final MouseLightLoader loader = new MouseLightLoader(id);
		System.out.println(loader.idExists());
		System.out.println(MouseLightLoader.isDatabaseAvailable());
		try (PrintWriter out = new PrintWriter(dir + id + "manual.swc")) {
			final StringReader reader = SWCPoint.collectionAsReader(loader.getNodes());
			try (BufferedReader br = new BufferedReader(reader)) {
				br.lines().forEach(out::println);
			} catch (final IOException e) {
				e.printStackTrace();
			}
			out.println("# End of Tree ");
		} catch (final FileNotFoundException | IllegalArgumentException e) {
			e.printStackTrace();
		}
		System.out.println("# All done");
		System.out.println("Saving bogus id: " + new MouseLightLoader("AA01").saveAsSWC(dir));
		System.out.println("Saving valid id: " + new MouseLightLoader("AA0360").saveAsSWC(dir));
		System.out.println("# done");
	}

}
