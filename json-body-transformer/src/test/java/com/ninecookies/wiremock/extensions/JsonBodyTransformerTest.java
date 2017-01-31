package com.ninecookies.wiremock.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.module.jsv.JsonSchemaValidator;
import com.jayway.restassured.response.ExtractableResponse;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.response.ValidatableResponse;

public class JsonBodyTransformerTest {
	private static final int SERVER_PORT = 9090;

	private static final String REQUEST_URL = "/post/it";
	private static final String BODY_TRANSFORMER = "json-body-transformer";
	private static final String CONTENT_TYPE = "application/json";
	private static final String REQUEST_BODY = "{\"string\": \"value\", " + "\"boolean\": true, "
			+ "\"datetime\": \"2016-09-26T14:30:22.447Z\", " + "\"number\": 12345}";

	private static final String COMPLEX_REQUEST_BODY = "{ \"complex\": {\"string\": \"value\", " + "\"boolean\": true, "
			+ "\"datetime\": \"2016-09-26T14:30:22.447Z\", " + "\"number\": 12345} }";

	private static final String MORE_COMPLEX_REQUEST_BODY = "{ \"complex\": {\"string\": \"value\", "
			+ "\"boolean\": true, " + "\"datetime\": \"2016-09-26T14:30:22.447Z\", " + "\"number\": 12345, "
			+ "\"more\": { \"string\": \"value\", \"boolean\": true, \"number\": 12345 } } }";

	private static final String INSTANT_RESPONSE_SCHEMA = "{\"$schema\": \"http://json-schema.org/draft-03/schema#\","
			+ "\"type\":\"object\",\"properties\":{\"instant\":{\"type\":\"string\",\"format\":\"date-time\","
			+ "\"required\":true}}}";

	private static final String TIMESTAMP_RESPONSE_SCHEMA = "{\"$schema\": \"http://json-schema.org/draft-03/schema#\","
			+ "\"type\":\"object\",\"properties\":{\"timestamp\":{\"type\":\"number\",\"required\":true}}}";

	private WireMockServer wireMockServer;

	@BeforeClass
	public void beforeClass() {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
		System.setProperty("org.slf4j.simpleLogger.log.com.ninecookies.wiremock.extensions", "debug");

		wireMockServer = new WireMockServer(wireMockConfig().port(SERVER_PORT).extensions(new JsonBodyTransformer()));
		wireMockServer.start();

		RestAssured.port = SERVER_PORT;
	}

	@AfterClass(alwaysRun = true)
	public void afterClass() {
		wireMockServer.stop();
	}

	@BeforeMethod
	public void beforeMethod() {
		wireMockServer.resetRequests();
	}

	@Test
	public void testTransformArray() {
		String url = "/stub/array";
		String requestBody = "{\"list\": [{\"item\":\"item-0\"},{\"item\":\"item-1\"}]}";
		String responseBody = "{\"list\": \"$(list)\"}";

		wireMockServer.stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(requestBody).when().post(url);
		response.then().statusCode(200).body("list[0].item", equalTo("item-0"))
				.body("list[1].item", equalTo("item-1"));
	}

	@Test
	public void transformBodyStubbing() {
		String url = "/stub/response";

		String responseBody = "{\"string\": \"$(string)\", \"nullstring\": \"$(nullstring)\", "
				+ "\"boolean\": \"$(boolean)\", \"nullboolean\": \"$(nullboolean)\", "
				+ "\"datetime\": \"$(datetime)\", \"nulldatetime\": \"$(nulldatetime)\", "
				+ "\"number\": \"$(number)\", \"nullnumber\": \"$(nullnumber)\"}";

		wireMockServer.stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("string", equalTo("value")).body("nullstring", equalTo(null))
				.body("boolean", equalTo(true)).body("nullboolean", equalTo(null))
				.body("datetime", equalTo("2016-09-26T14:30:22.447Z")).body("nulldatetime", equalTo(null))
				.body("number", equalTo(12345)).body("nullnumber", equalTo(null));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformBodyInlineMapping() {
		String url = "/inline/response";

		Response response = given().contentType(CONTENT_TYPE).body(REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("string", equalTo("value")).body("nullstring", equalTo(null))
				.body("boolean", equalTo(true)).body("nullboolean", equalTo(null))
				.body("datetime", equalTo("2016-09-26T14:30:22.447Z")).body("nulldatetime", equalTo(null))
				.body("number", equalTo(12345)).body("nullnumber", equalTo(null));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformBodyFileMapping() {
		String url = "/file/response";

		Response response = given().contentType(CONTENT_TYPE).body(REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("string", equalTo("value")).body("nullstring", equalTo(null))
				.body("boolean", equalTo(true)).body("nullboolean", equalTo(null))
				.body("datetime", equalTo("2016-09-26T14:30:22.447Z")).body("nulldatetime", equalTo(null))
				.body("number", equalTo(12345)).body("nullnumber", equalTo(null));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformNestedBodyStubbing() {
		String url = "/stub/nested/response";

		String responseBody = "{ \"nested\": { \"string\": \"$(string)\", \"nullstring\": \"$(nullstring)\", "
				+ "\"boolean\": \"$(boolean)\", \"nullboolean\": \"$(nullboolean)\", "
				+ "\"datetime\": \"$(datetime)\", \"nulldatetime\": \"$(nulldatetime)\", "
				+ "\"number\": \"$(number)\", \"nullnumber\": \"$(nullnumber)\"} }";

		wireMockServer.stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("nested.string", equalTo("value")).body("nested.nullstring", equalTo(null))
				.body("nested.boolean", equalTo(true)).body("nested.nullboolean", equalTo(null))
				.body("nested.datetime", equalTo("2016-09-26T14:30:22.447Z")).body("nested.nulldatetime", equalTo(null))
				.body("nested.number", equalTo(12345)).body("nested.nullnumber", equalTo(null));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformNestedBodyInlineMapping() {
		String url = "/inline/nested/response";

		Response response = given().contentType(CONTENT_TYPE).body(REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("nested.string", equalTo("value")).body("nested.nullstring", equalTo(null))
				.body("nested.boolean", equalTo(true)).body("nested.nullboolean", equalTo(null))
				.body("nested.datetime", equalTo("2016-09-26T14:30:22.447Z")).body("nested.nulldatetime", equalTo(null))
				.body("nested.number", equalTo(12345)).body("nested.nullnumber", equalTo(null));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformNestedBodyFileMapping() {
		String url = "/file/nested/response";

		Response response = given().contentType(CONTENT_TYPE).body(REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("nested.string", equalTo("value")).body("nested.nullstring", equalTo(null))
				.body("nested.boolean", equalTo(true)).body("nested.nullboolean", equalTo(null))
				.body("nested.datetime", equalTo("2016-09-26T14:30:22.447Z")).body("nested.nulldatetime", equalTo(null))
				.body("nested.number", equalTo(12345)).body("nested.nullnumber", equalTo(null));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformComplexBodyStubbing() {
		String url = "/stub/complex/response";

		String responseBody = "{ \"complex\": \"$(complex)\"} }";
		wireMockServer.stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(COMPLEX_REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("complex.string", equalTo("value")).body("complex.boolean", equalTo(true))
				.body("complex.datetime", equalTo("2016-09-26T14:30:22.447Z")).body("complex.number", equalTo(12345));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformMoreComplexBodyStubbing() {
		String url = "/stub/more/complex/response";

		String responseBody = "{ \"complex\": \"$(complex)\" }";
		wireMockServer.stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(MORE_COMPLEX_REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("complex.string", equalTo("value")).body("complex.boolean", equalTo(true))
				.body("complex.datetime", equalTo("2016-09-26T14:30:22.447Z")).body("complex.number", equalTo(12345))
				.body("complex.more.string", equalTo("value")).body("complex.more.boolean", equalTo(true))
				.body("complex.more.number", equalTo(12345));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformComplexBodyInlineMapping() {
		String url = "/inline/complex/response";

		Response response = given().contentType(CONTENT_TYPE).body(COMPLEX_REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("complex.string", equalTo("value")).body("complex.boolean", equalTo(true))
				.body("complex.nullboolean", equalTo(null))
				.body("complex.datetime", equalTo("2016-09-26T14:30:22.447Z")).body("complex.number", equalTo(12345));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformComplexBodyFileMapping() {
		String url = "/file/complex/response";

		Response response = given().contentType(CONTENT_TYPE).body(COMPLEX_REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("complex.string", equalTo("value")).body("complex.boolean", equalTo(true))
				.body("complex.nullboolean", equalTo(null))
				.body("complex.datetime", equalTo("2016-09-26T14:30:22.447Z")).body("complex.number", equalTo(12345));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void transformWithMissingJsonPathStubbing() {
		String url = "/stub/response/missing/json/path";

		String responseBody = "{ \"string\": \"$(string)\",  \"data\": { \"name\": \"$(data.name)\" } }";

		wireMockServer.stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(REQUEST_BODY).when().post(url);

		response.then().statusCode(201).body("string", equalTo("value")).body("data.name", equalTo(null));

		wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
	}

	@Test
	public void replaceVariableHolder() throws Exception {
		String requestBody = "{\"name\":\"John Doe\", \"age\": 35}";
		String responseBody = "{\"name\":\"$(name)\", \"age\": \"$(age)\", \"got\":\"it\"}";
		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));
		given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL).then().statusCode(200)
				.body("name", equalTo("John Doe"))
				.body("age", equalTo(35))
				.body("got", equalTo("it"));

		wireMockServer.verify(postRequestedFor(urlEqualTo("/post/it")));
	}

	@Test
	public void useTransformerWithoutReplacementPatterns() {
		String requestBody = "{\"name\":\"John Doe\"}";
		String responseBody = "{\"name\":\"Jane Doe\", \"got\":\"it\"}";
		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));
		given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL).then().statusCode(200)
				.body("name", equalTo("Jane Doe")).body("got", equalTo("it"));

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void replaceNestedVariables() throws Exception {
		String requestBody = "{\"name\":\"John Doe\", \"nested\": {\"attr\": \"found\"}}}";
		String responseBody = "{\"name\":\"$(name)\", \"got\":\"it\", \"nested_attr\": \"$(nested.attr)\"}";

		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));
		Response response = given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL);

		response.then().statusCode(200).body("name", equalTo("John Doe")).body("got", equalTo("it")).body("nested_attr",
				equalTo("found"));
		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void nullVariableNotFound() throws Exception {
		String requestBody = "{\"something\":\"different\"}";
		String responseBody = "{\"name\":\"$(name)\"}";

		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL).then().statusCode(200).body("name",
				equalTo(null));

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void doesNotApplyGlobally() throws Exception {
		String requestBody = "{\"name\":\"John Doe\"}";
		String responseBody = "{\"name\":\"$(name)\"}";
		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(
				aResponse().withStatus(200).withHeader("content-type", CONTENT_TYPE).withBody(responseBody)));

		given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL).then().statusCode(200)
				.body(equalTo(responseBody));

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@DataProvider
	private Object[][] randomFormats() {
		return new Object[][] {
				{ "$(!Random)" },
				{ "$(!Random.1)" },
				{ "$(!Random.abc)" },
				{ "$(!Random.ABC)" },
		};
	}

	@Test(dataProvider = "randomFormats")
	public void injectRandom(String format) {
		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL))
				.willReturn(aResponse().withStatus(200).withHeader("content-type", CONTENT_TYPE)
						.withBody("{\"randomNumber\":\"" + format + "\", \"got\":\"it\"}")
						.withTransformers(BODY_TRANSFORMER)));

		given().contentType(CONTENT_TYPE).body("{\"var\":1111}").when().post(REQUEST_URL).then().statusCode(200)
				.body("randomNumber", isA(Integer.class)).body("got", equalTo("it"));

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void injectMultipleRandomsWithReuse() {
		String responseBody = "{\"id\": \"$(!Random.id)\", \"self\": \"$(!Random.id)\", \"other\": \"$(!Random)\"}";
		String requestBody = "{}";

		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL);

		ExtractableResponse<?> er = response.then().statusCode(200)
				.body("id", isA(Integer.class))
				.body("self", isA(Integer.class))
				.body("other", isA(Integer.class))
				.extract();
		Integer id = er.path("id");
		Integer self = er.path("self");
		Integer other = er.path("other");
		assertEquals(id, self);
		assertNotEquals(id, other);

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@DataProvider
	private Object[][] uuidFormats() {
		return new Object[][] {
				{ "$(!UUID)" },
				{ "$(!UUID.1)" },
				{ "$(!UUID.abc)" },
				{ "$(!UUID.ABC)" },
		};
	}

	@Test(dataProvider = "uuidFormats")
	public void injectUUID(String format) {
		String responseBody = "{\"id\": \"" + format + "\"}";
		String requestBody = "{}";

		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL);

		String uuid = response.then().statusCode(200).body("id", isA(String.class)).extract().path("id");
		try {
			UUID.fromString(uuid);
		} catch (IllegalArgumentException e) {
			fail(e.getMessage());
		}

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void injectMultipleUUIDsWithReuse() {
		String responseBody = "{\"id\": \"$(!UUID.id)\", \"self\": \"$(!UUID.id)\", \"other\": \"$(!UUID)\"}";
		String requestBody = "{}";

		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL);

		ExtractableResponse<?> er = response.then().statusCode(200)
				.body("id", isA(String.class))
				.body("self", isA(String.class))
				.body("other", isA(String.class))
				.extract();
		String id = er.path("id");
		String self = er.path("self");
		String other = er.path("other");
		assertEquals(id, self);
		assertNotEquals(id, other);
		try {
			UUID.fromString(id);
			UUID.fromString(self);
			UUID.fromString(other);
		} catch (IllegalArgumentException e) {
			fail(e.getMessage());
		}

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void injectInstant() {
		String responseBody = "{\"instant\":\"$(!Instant)\"}";
		String requestBody = "{}";
		Instant expectedInstant = Instant.now();
		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));
		Response response = given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL);
		Instant instant = Instant.parse(response.then().statusCode(200)
				.body(JsonSchemaValidator.matchesJsonSchema(INSTANT_RESPONSE_SCHEMA))
				.and().body("instant", isA(String.class)).extract().path("instant"));
		assertEquals(Duration.between(instant, expectedInstant).plus(Duration.ofMillis(999)).getSeconds(), 0);

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void injectTimestamp() {
		String responseBody = "{\"timestamp\":\"$(!Timestamp)\"}";
		String requestBody = "{}";
		Instant expectedInstant = Instant.now();
		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));
		Response response = given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL);
		Instant instant = Instant.ofEpochMilli(response.then().statusCode(200)
				.body(JsonSchemaValidator.matchesJsonSchema(TIMESTAMP_RESPONSE_SCHEMA))
				.and().body("timestamp", isA(Long.class)).extract().path("timestamp"));

		assertEquals(Duration.between(instant, expectedInstant).plus(Duration.ofMillis(999)).getSeconds(), 0);

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@DataProvider
	private Object[][] dateFormats() {
		return new Object[][] {
				{ "Instant" }, // ISO 8601
				{ "Timestamp" } // Unix epoch millis
		};
	}

	@Test(dataProvider = "dateFormats")
	public void injectInstantPlusOneSecond(String format) {
		Instant expectedInstant = Instant.now().plus(Duration.ofSeconds(1));
		String unitAmount = "s1";
		assertInstantComputeation(format, unitAmount, expectedInstant);

		expectedInstant = Instant.now().plus(Duration.ofSeconds(1));
		unitAmount = "S1";
		assertInstantComputeation(format, unitAmount, expectedInstant);
	}

	@Test(dataProvider = "dateFormats")
	public void injectInstantPlusOneMinute(String format) {
		Instant expectedInstant = Instant.now().plus(Duration.ofMinutes(1));
		String unitAmount = "m1";
		assertInstantComputeation(format, unitAmount, expectedInstant);

		expectedInstant = Instant.now().plus(Duration.ofMinutes(1));
		unitAmount = "M1";
		assertInstantComputeation(format, unitAmount, expectedInstant);
	}

	@Test(dataProvider = "dateFormats")
	public void injectInstantPlusOneHour(String format) {
		Instant expectedInstant = Instant.now().plus(Duration.ofHours(1));
		String unitAmount = "h1";
		assertInstantComputeation(format, unitAmount, expectedInstant);

		expectedInstant = Instant.now().plus(Duration.ofHours(1));
		unitAmount = "H1";
		assertInstantComputeation(format, unitAmount, expectedInstant);
	}

	@Test(dataProvider = "dateFormats")
	public void injectInstantMinusOneSecond(String format) {
		Instant expectedInstant = Instant.now().plus(Duration.ofSeconds(-1));
		String unitAmount = "s-1";
		assertInstantComputeation(format, unitAmount, expectedInstant);

		expectedInstant = Instant.now().plus(Duration.ofSeconds(-1));
		unitAmount = "S-1";
		assertInstantComputeation(format, unitAmount, expectedInstant);
	}

	@Test(dataProvider = "dateFormats")
	public void injectInstantMinusOneMinute(String format) {
		Instant expectedInstant = Instant.now().plus(Duration.ofMinutes(-1));
		String unitAmount = "m-1";
		assertInstantComputeation(format, unitAmount, expectedInstant);

		expectedInstant = Instant.now().plus(Duration.ofMinutes(-1));
		unitAmount = "M-1";
		assertInstantComputeation(format, unitAmount, expectedInstant);
	}

	@Test(dataProvider = "dateFormats")
	public void injectInstantMinusOneHour(String format) {
		Instant expectedInstant = Instant.now().plus(Duration.ofHours(-1));
		String unitAmount = "h-1";
		assertInstantComputeation(format, unitAmount, expectedInstant);

		expectedInstant = Instant.now().plus(Duration.ofHours(-1));
		unitAmount = "H-1";
		assertInstantComputeation(format, unitAmount, expectedInstant);
	}

	private void assertInstantComputeation(String format, String unitAmount, Instant expectedInstant) {
		String field = format.toLowerCase(Locale.ROOT);
		String responseSchema = ("Instant".equals(format)) ? INSTANT_RESPONSE_SCHEMA : TIMESTAMP_RESPONSE_SCHEMA;
		Class<?> responseType = ("Instant".equals(format)) ? String.class : Long.class;
		String responseBody = "{\"" + field + "\":\"$(!" + format + ".plus[" + unitAmount + "])\"}";
		String requestBody = "{}";

		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		Response response = given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL);

		ValidatableResponse vr = response.then().statusCode(200)
				.body(JsonSchemaValidator.matchesJsonSchema(responseSchema))
				.body(field, isA(responseType));

		Instant actualInstant = ("Instant".equals(format)) ? Instant.parse(vr.extract().path(field))
				: Instant.ofEpochMilli(vr.extract().path(field));

		assertEquals(Duration.between(actualInstant, expectedInstant).plus(Duration.ofMillis(999)).getSeconds(), 0);

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void injectInstantPlusInvalid() {
		String responseBody = "{\"instant\":\"$(!Instant.plus[a1])\"}";
		String requestBody = "{}";

		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL)
				.then().statusCode(500);

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

	@Test
	public void injectTimestampPlusInvalid() {
		String responseBody = "{\"timestamp\":\"$(!Timestamp.plus[a1])\"}";
		String requestBody = "{}";

		wireMockServer.stubFor(post(urlEqualTo(REQUEST_URL)).willReturn(aResponse().withStatus(200)
				.withHeader("content-type", CONTENT_TYPE).withBody(responseBody).withTransformers(BODY_TRANSFORMER)));

		given().contentType(CONTENT_TYPE).body(requestBody).when().post(REQUEST_URL)
				.then().statusCode(500);

		wireMockServer.verify(postRequestedFor(urlEqualTo(REQUEST_URL)));
	}

}
