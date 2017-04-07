package com.ninecookies.wiremock.extensions;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
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
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.Response;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class JsonBodyTransformer extends ResponseTransformer {

	private static final Logger LOG = LoggerFactory.getLogger(JsonBodyTransformer.class);

	private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
	private static final Set<RequestMethod> METHODS_WITH_CONTENT = new HashSet<>(
			Arrays.asList(RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH));

	private final Random random = new Random();
	private final Pattern uuidRandomPattern = Pattern.compile("\\$\\(!(UUID|Random).*\\)");
	private final Pattern instantPlusPattern = Pattern
			.compile("\\$\\(!(Instant|Timestamp)\\.plus\\[([HhMmSs]{1})([0-9\\-]+)\\]\\)");

	private final Pattern responsePattern = Pattern.compile("\\$\\(.*?\\)");

	@Override
	public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
		// nothing to do for an empty body
		if (response.getBody() == null || response.getBody().length == 0) {
			LOG.debug("skip transformation of empty response");
			return response;
		}

		// nothing to do for response content type other than application/json
		if (!response.getHeaders().getContentTypeHeader().isPresent()
				|| !CONTENT_TYPE_APPLICATION_JSON.equals(response.getHeaders().getContentTypeHeader().mimeTypePart())) {
			LOG.debug("skip transformation of unknown response (headers: '{}')",
					response.getHeaders().toString().trim());
			return response;
		}

		// get request body if applicable
		String requestBody = null;
		if (METHODS_WITH_CONTENT.contains(request.getMethod())) {
			if (!request.contentTypeHeader().isPresent()
					|| !CONTENT_TYPE_APPLICATION_JSON.equals(request.contentTypeHeader().mimeTypePart())) {
				LOG.debug("skip request parsing due to content type '{}'", request.contentTypeHeader());
			} else {
				requestBody = request.getBodyAsString();
			}
		} else {
			LOG.debug("skip request parsing due to method '{}'", request.getMethod());
		}

		// read all transformation patterns from response, populate values and transform the response
		String responseBody = response.getBodyAsString();
		Map<String, Object> responseJsonPaths = readResponsePatterns(responseBody);
		mapRequestJsonPaths(responseJsonPaths, requestBody);
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
			// replace all occurrences of "$(property.path)" with according JSON value
			String target = "\"" + entry.getKey() + "\"";
			String value = Json.write(entry.getValue());
			result = result.replace(target, value);
			// check for in string replacements like "some arbitrary text $(property.path) with embedded property"
			if (result.contains(entry.getKey())) {
				result = result.replace(entry.getKey(), String.valueOf(entry.getValue()));
			}
		}
		return result;
	}

	private Map<String, Object> readResponsePatterns(String responseBody) {
		Map<String, Object> result = new LinkedHashMap<>();
		Matcher matcher = responsePattern.matcher(responseBody);
		while (matcher.find()) {
			String group = matcher.group();
			if (result.containsKey(group)) {
				LOG.debug("ignoring redundant response pattern '{}'", group);
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
		DocumentContext requestJsonPath = null;
		if (requestBody != null) {
			requestJsonPath = JsonPath.using(Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL)
					.options(Option.SUPPRESS_EXCEPTIONS).build()).parse(requestBody);
		}
		for (Entry<String, Object> entry : map.entrySet()) {
			if ("$(!Instant)".equals(entry.getKey())) {
				entry.setValue(Instant.now().toString());
			} else if ("$(!Timestamp)".equals(entry.getKey())) {
				entry.setValue(Instant.now().toEpochMilli());
			} else {
				// check for $(!UUID) and $(!Random)
				Matcher uuidRandomMatcher = uuidRandomPattern.matcher(entry.getKey());
				if (uuidRandomMatcher.matches()) {
					// group 1 is the desired format (Random = random integer, UUID = random UUID)
					String format = uuidRandomMatcher.group(1);
					if ("UUID".equals(format)) {
						entry.setValue(UUID.randomUUID().toString());
					} else {
						entry.setValue(random.nextInt(Integer.MAX_VALUE));
					}
					continue;
				}
				// check for time stamp computation
				Matcher instantPlusMatcher = instantPlusPattern.matcher(entry.getKey());
				if (instantPlusMatcher.matches()) {
					// group 1 is the desired format (Instant = ISO 8601, Timestamp = Unix epoch millis)
					// group 2 is the unit (H(our)|M(inute)|S(econd))
					// group 3 is the amount (negative value is allowed)
					String format = instantPlusMatcher.group(1);
					String unit = instantPlusMatcher.group(2);
					String amount = instantPlusMatcher.group(3);
					Duration duration = Duration.of(Long.parseLong(amount), stringToChronoUnit(unit));
					LOG.debug("unit for '{}' is '{}' with amount '{}' and format '{}' yields to {}",
							entry.getKey(), unit, amount, format, duration);
					if ("Instant".equals(format)) {
						entry.setValue(Instant.now().plus(duration).toString());
					} else {
						entry.setValue(Instant.now().plus(duration).toEpochMilli());
					}
					continue;
				}
				// convert JSON replacement pattern to JsonPath expression and read value from request
				if (requestJsonPath != null) {
					String path = entry.getKey().replaceFirst("\\$\\(", "\\$\\."); // change $( to // $.
					path = path.substring(0, path.length() - 1); // remove trailing )
					Object value = requestJsonPath.read(path);
					LOG.debug("value for path '{}' is '{}' of type '{}'", path, value,
							(value == null) ? null : value.getClass());
					entry.setValue(value);
				} else {
					LOG.warn("ignoring pattern {} due to missing request body", entry.getKey());
				}
			}
		}
		return map;
	}

	private ChronoUnit stringToChronoUnit(String unit) {
		switch (unit) {
		case "H":
		case "h":
			return ChronoUnit.HOURS;
		case "M":
		case "m":
			return ChronoUnit.MINUTES;
		case "S":
		case "s":
			return ChronoUnit.SECONDS;
		default:
			throw new IllegalArgumentException("Invalid unit for duration '" + unit + "'.");
		}
	}
}
