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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;

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
 * "https://ml-neuronbrowser.janelia.org/">ml-neuronbrowser.janelia.org</a> *
 *
 * @author Tiago Ferreira
 */
class MouseLightDownloader {

	private final static String JSON_URL = "https://ml-neuronbrowser.janelia.org/json";
	private final static String SWC_URL = "https://ml-neuronbrowser.janelia.org/swc";
	private static final int MIN_CHARS_IN_VALID_RESPONSE_BODY = 150;

	private MouseLightDownloader() {
	}

	private static Response getResponse(final String url, final Collection<String> ids) throws IOException {
		final StringBuilder sb = new StringBuilder();
		for (final String id : ids) {
			sb.append('"').append(id).append("\",");
		}
		final String idsString = sb.substring(0, sb.length() - 1);
		final OkHttpClient client = new OkHttpClient();
		final MediaType mediaType = MediaType.parse("application/json");
		final RequestBody body = RequestBody.create(mediaType, "{\"ids\": [" + idsString + "]}");
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

	private static JSONObject getJSON(final String url, final Collection<String> ids) throws IOException {
		final Response response = getResponse(url, ids);
		final String responseBody = response.body().string();
		if (responseBody.length() < MIN_CHARS_IN_VALID_RESPONSE_BODY)
			return null;
		final JSONObject json = new JSONObject(responseBody);
		return json;
	}

	protected static JSONObject getJSON(final String id) {
		try {
			final JSONObject json = getJSON(JSON_URL, Collections.singletonList(id));
			if (json != null && json.getJSONObject("contents").getJSONArray("neurons").isEmpty())
				return null;
			return json;
		} catch (final IOException e) {
			SNT.error("Failed to retrieve id " + id, e);
		}
		return null;
	}

	protected static String getSWC(final String id) {
		JSONObject json;
		try {
			json = getJSON(SWC_URL, Collections.singletonList(id));
			if (json == null)
				return null;
			final String jsonContents = json.get("contents").toString();
			final byte[] decodedContents = Base64.getDecoder().decode(jsonContents);
			return new String(decodedContents, StandardCharsets.UTF_8);
		} catch (final IOException e) {
			SNT.error("Failed to retrieve id " + id, e);
		}
		return null;
	}

	protected static boolean saveAsSWC(final String id, final String outputDirectory) throws IllegalArgumentException {
		final File dir = new File(outputDirectory);
		if (!validDirectory(dir))
			return false;
		final File file = new File(outputDirectory + id + ".swc");
		return saveJSONContents(getSWC(id), file);
	}

	protected static boolean saveAsJSON(final String id, final String outputDirectory) throws IllegalArgumentException {
		final File dir = new File(outputDirectory);
		if (!validDirectory(dir))
			return false;
		final File file = new File(dir, id + ".json");
		final JSONObject json = getJSON(id);
		if (json == null)
			return false;
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

	/* IDE debug method */
	public static void main(final String... args) {
		System.out.println("# starting");
		System.out.println("Saving bogus id: " + saveAsSWC("AA02", "/home/tferr/Desktop/testjson/"));
		System.out.println("Saving valid id: " + saveAsSWC("AA0002", "/home/tferr/Desktop/testjson/"));
		System.out.println("Saving bogus id: " + saveAsJSON("AA02", "/home/tferr/Desktop/testjson/"));
		System.out.println("Saving valid id: " + saveAsJSON("AA0002", "/home/tferr/Desktop/testjson/"));
		System.out.println("# done");
	}

}
