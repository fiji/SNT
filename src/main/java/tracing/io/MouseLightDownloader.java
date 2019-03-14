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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import tracing.SNT;

/**
 * Static methods for downloading reconstructions from MouseLight's online
 * database at <a href=
 * "https://ml-neuronbrowser.janelia.org/">ml-neuronbrowser.janelia.org</a>
 *
 * @author Tiago Ferreira
 */
class MouseLightDownloader {

	private final static String JSON_URL = "https://ml-neuronbrowser.janelia.org/json";
	private static final int MAX_JSON_LIMIT = 100;

	private MouseLightDownloader() {
	}

	private static Response getJSONResponse(final Collection<String> ids) throws IOException {
		final StringBuilder sb = new StringBuilder();
		for (final String id : ids) {
			sb.append('"').append(id).append("\",");
		}
		final String idsString = sb.substring(0, sb.length() - 1);
		final OkHttpClient client = new OkHttpClient();
		final MediaType mediaType = MediaType.parse("application/json");
		final RequestBody body = RequestBody.create(mediaType, "{\"ids\": [" + idsString + "]}");
		final Request request = new Request.Builder() //
				.url(JSON_URL) //
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

	protected static JSONObject getJSON(final String id) throws IOException {
		return getJSON(Collections.singletonList(id));
	}

	protected static JSONObject getJSON(final Collection<String> ids) throws IOException {
		final Response response = getJSONResponse(ids);
		final String responseBody = response.body().string();
		final JSONObject json = new JSONObject(responseBody);
		return json;
	}

	protected static boolean saveAsJSON(final String id, final String outputDirectory) {
		return saveAsJSON(Collections.singletonList(id), outputDirectory);
	}

	protected static boolean saveAsJSON(final Collection<String> ids, final String outputDirectory)
			throws IllegalArgumentException {
		if (ids.size() > MAX_JSON_LIMIT)
			throw new IllegalArgumentException("No. of ids exceeds the server imposed limit of " + MAX_JSON_LIMIT);
		final File dir = new File(outputDirectory);
		if (!validDirectory(dir))
			return false;
		try {
			final JSONObject json = getJSON(ids);
			final JSONArray neuronsArray = json.getJSONObject("contents").getJSONArray("neurons");
			if (neuronsArray.isEmpty()) {
				SNT.log("ID not found");
				return false;
			}
			final String filename = (ids.size() == 1) ? ids.stream().findFirst().get() : "mlnb-export";
			final PrintWriter out = new PrintWriter(outputDirectory + filename + ".json");
			out.println(json);
			out.close();
		} catch (SecurityException | IOException ex) {
			SNT.error("Could not save requested data", ex);
			return false;
		}
		return true;
	}

	/* IDE debug method */
	public static void main(final String... args) {
		System.out.println("# starting");
		System.out.println("Saving bogus id: "+ saveAsJSON("AA01", "/home/tferr/Desktop/testjson/"));
		System.out.println("Saving valid id: "+ saveAsJSON("AA0001", "/home/tferr/Desktop/testjson/"));
		final boolean list = saveAsJSON(Arrays.asList("AA0001", "bar", "baz"), "/home/tferr/Desktop/testjson/");
		System.out.println(list);
		System.out.println("# done");
	}

}
