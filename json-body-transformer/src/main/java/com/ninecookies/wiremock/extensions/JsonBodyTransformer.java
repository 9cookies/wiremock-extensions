package com.ninecookies.wiremock.extensions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class JsonBodyTransformer extends ResponseTransformer {

	private static final Logger LOG = LoggerFactory.getLogger(JsonBodyTransformer.class);

	private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

	private final Pattern interpolationPattern = Pattern.compile("\\$\\(.*?\\)");

	@Override
	public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
		// nothing to do for an empty body
		if (response.getBody() == null || response.getBody().length == 0) {
			LOG.debug("skipping transformation of empty response body.");
			return response;
		}
		// nothing to do for content type other than application/json
		if (!CONTENT_TYPE_APPLICATION_JSON.equals(request.contentTypeHeader().mimeTypePart())) {
			LOG.warn("skipping transformation of unhandled content type '{}'",
					request.contentTypeHeader().mimeTypePart());
			return response;
		}

		// read all JSON path definitions from response and find counterparts in request
		String responseBody = response.getBodyAsString();
		Map<String, Object> responseJsonPaths = readResponseJsonPaths(responseBody);
		mapRequestJsonPaths(responseJsonPaths, request.getBodyAsString());
		String transformedResponseBody = transformJsonResponse(responseJsonPaths, responseBody);

		return Response.Builder.like(response).but().body(transformedResponseBody).build();
	}

	@Override
	public String getName() {
		return "json-body-transformer";
	}

	@Override
	public boolean applyGlobally() {
		return false;
	}

	private String transformJsonResponse(Map<String, Object> map, String responseBody) {
		String result = responseBody;
		for (Entry<String, Object> entry : map.entrySet()) {
			String value = Json.write(entry.getValue());
			String target = "\"" + entry.getKey() + "\"";
			result = result.replace(target, value);
		}
		return result;
	}

	private Map<String, Object> readResponseJsonPaths(String responseBody) {
		Map<String, Object> result = new LinkedHashMap<>();
		Matcher matcher = interpolationPattern.matcher(responseBody);
		while (matcher.find()) {
			String group = matcher.group();
			if (result.containsKey(group)) {
				LOG.warn("warning: '{}' already contained in map", group);
			} else {
				result.put(group, null);
			}
		}
		return result;
	}

	private Map<String, Object> mapRequestJsonPaths(Map<String, Object> map, String requestBody) {
		if (map.isEmpty()) {
			return map;
		}
		DocumentContext requestJsonPath =
				JsonPath.using(Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL)
						.options(Option.SUPPRESS_EXCEPTIONS).build()).parse(requestBody);
		for (Entry<String, Object> entry : map.entrySet()) {
			if ("$(!Random)".equals(entry.getKey())) {
				entry.setValue(new Random().nextInt(Integer.MAX_VALUE));
			} else if ("$(!Instant)".equals(entry.getKey())) {
				entry.setValue(Instant.now().toString());
			} else if ("$(!UUID)".equals(entry.getKey())) {
				entry.setValue(UUID.randomUUID().toString());
			} else {
				// convert JSON replacement pattern to JsonPath expression
				String path = entry.getKey().replaceFirst("\\$\\(", "\\$\\."); // change $( to // $.
				path = path.substring(0, path.length() - 1); // remove trailing )
				Object value = requestJsonPath.read(path);
				LOG.debug("value for path '{}' is '{}' of type '{}'", path, value,
						(value == null) ? null : value.getClass());
				entry.setValue(value);
			}
		}
		return map;
	}
}
