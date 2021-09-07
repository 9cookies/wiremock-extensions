package com.ninecookies.wiremock.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.jayway.restassured.RestAssured.given;
import static com.ninecookies.wiremock.extensions.util.Maps.entry;
import static com.ninecookies.wiremock.extensions.util.Maps.mapOf;
import static org.testng.Assert.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.api.Callbacks;

public class CallbackSimulatorTest extends AbstractExtensionTest {

    private static final class CallbackData {
        @SuppressWarnings("unused")
        public String id;
        public String value;
        public String timestamp;

        private static CallbackData of(String value) {
            CallbackData result = new CallbackData();
            result.value = value;
            result.id = "$(response.id)";
            result.timestamp = Instant.now().toString();
            return result;
        }
    }

    private static final int DELAY = 100;
    private static final int SLEEP = 500;
    private static final int SERVER_PORT = 9090;

    private static final String CALLBACK_POST_DATA_FORMAT = "{\"code\":\"%s\"}";
    private static final String EXPECTED_CALLBACK_JSON_FORMAT = "{\"response_id\":\"%s\",\"request_code\":\"%s\"," +
            "\"url_parts_1\":\"callback\",\"defined_value\":\"from-mapping-file\"}";

    private static final String QUEUE_NAME = "test-queue-name";

    @BeforeMethod
    public void beforeMethod() {
        resetAllRequests();

        sqsClient.purgeQueue(new PurgeQueueRequest(sqsClient.getQueueUrl(QUEUE_NAME).getQueueUrl()));
    }

    @Test
    public void testEnvAuthenticatedCallbackWithMapping() throws InterruptedException {
        String code = "b63868c0";
        String requestUrl = "/env-authenticated/callback";
        String requestBody = String.format(CALLBACK_POST_DATA_FORMAT, code);
        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();
        String id = Json.node(responseJson).get("id").textValue();
        String json = String.format(EXPECTED_CALLBACK_JSON_FORMAT, id, code);
        sleep();
        verify(1, postRequestedFor(urlEqualTo("/callbacks"))
                .withBasicAuth(new BasicCredentials("callback-user", "callback-pass"))
                .withRequestBody(equalToJson(json)));
    }

    @Test
    public void testAuthenticatedCallbackWithMapping() throws InterruptedException {
        String code = "b63868c0";
        String requestUrl = "/authenticated/callback";
        String requestBody = String.format(CALLBACK_POST_DATA_FORMAT, code);
        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();
        String id = Json.node(responseJson).get("id").textValue();
        String json = String.format(EXPECTED_CALLBACK_JSON_FORMAT, id, code);
        sleep();
        verify(1, postRequestedFor(urlEqualTo("/callbacks"))
                .withBasicAuth(new BasicCredentials("user", "pass"))
                .withRequestBody(equalToJson(json)));
    }

    @Test
    public void testUnauthenticatedCallbackWithMapping() throws InterruptedException {
        String code = "f4625223";
        String requestUrl = "/unauthenticated/callback";
        String requestBody = String.format(CALLBACK_POST_DATA_FORMAT, code);
        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();
        String id = Json.node(responseJson).get("id").textValue();
        String json = String.format(EXPECTED_CALLBACK_JSON_FORMAT, id, code);
        sleep();
        verify(1, postRequestedFor(urlEqualTo("/callbacks"))
                .withoutHeader("Authorization")
                .withRequestBody(equalToJson(json)));
    }

    @Test
    public void testCallbackWithStubbing() throws InterruptedException {
        String requestUrl = "/request";
        String callbackPath = "/callback";

        String requestBody = "{\"code\":\"b63868c0\","
                + "\"callbacks\":{\"order_dispatched\":\"http://localhost:" + SERVER_PORT + callbackPath + "\"},"
                + "\"promised_delivery_at\":\"2019-03-14T16:09:19.748Z\","
                + "\"preparation_time\":10,\"preparation_buffer\":2}";
        String responseBody = "{\"id\":\"$(!UUID)\"}";
        String callbackUrl = "$(request.callbacks.order_dispatched)";
        CallbackData callbackData = CallbackData.of("aritrary-data");

        stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", Callbacks.of(DELAY, callbackUrl, callbackData))
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        stubFor(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String id = Json.node(responseJson).get("id").textValue();
        sleep();
        verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.value == '" + callbackData.value + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp == '" + callbackData.timestamp + "')]")));
    }

    @Test
    public void testCallbackWithPartialUrlReplacement() throws InterruptedException {
        String postUrl = "/request/partial";
        String callbackUrl = "http://localhost:$(response.port)/callbacks";

        String postData = "{\"name\":\"test\"}";
        String responseData = "{\"id\":\"$(!UUID)\",\"name\":\"$(name)\",\"port\":" + SERVER_PORT + "}";

        stubFor(post(urlEqualTo(postUrl))
                .withPostServeAction("callback-simulator",
                        Callbacks.of(DELAY, callbackUrl, CallbackData.of("arbitrary-data")))
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseData)
                        .withTransformers("json-body-transformer")
                        .withStatus(200)));

        given().body(postData).contentType("application/json")
                .when().post(postUrl)
                .then().statusCode(200);

        sleep();
        verify(1, postRequestedFor(urlEqualTo("/callbacks")));
    }

    @Test
    public void testCallbackWithMultipleUrlReplacements() throws InterruptedException {
        String postUrl = "/request/multi/partial";
        String callbackPath = "/multi/callbacks";
        String callbackUrl = "http://localhost:$(response.port)" + callbackPath + "?name=$(request.name)";

        String postData = "{\"name\":\"john doe\"}";
        String responseData = "{\"id\":\"$(!UUID)\",\"name\":\"$(name)\",\"port\":" + SERVER_PORT + "}";

        stubFor(post(urlEqualTo(postUrl))
                .withPostServeAction("callback-simulator",
                        Callbacks.of(DELAY, callbackUrl, CallbackData.of("arbitrary-data")))
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseData)
                        .withTransformers("json-body-transformer")
                        .withStatus(200)));

        stubFor(post(urlPathEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        given().body(postData).contentType("application/json")
                .when().post(postUrl)
                .then().statusCode(200);

        sleep();
        verify(1, postRequestedFor(urlPathEqualTo(callbackPath)).withQueryParam("name", equalTo("john doe")));
    }

    @Test
    public void testMultipleCallbacksWithStubbing() throws InterruptedException {
        String requestUrl = "/request/multiple";
        String callbackPath = "/multiple/callbacks";

        String requestBody = "{\"code\":\"b63868c0\"}";
        String responseBody = "{\"id\":\"$(!UUID)\"}";
        String callbackUrl = "http://localhost:" + SERVER_PORT + "/multiple/callbacks";

        Object data = mapOf(
                entry("id", "$(!Random.id)"),
                entry("user", "$(!Random.user)"),
                entry("name", "name $(!Random.id)"));

        Object callbackData1 = mapOf(
                entry("name", "callback-data-1"),
                entry("code", "$(request.code)"),
                entry("uuid", "$(response.id)"),
                entry("timestamp", "$(!Instant.cb1)"),
                entry("data", data));

        Object callbackData2 = mapOf(
                entry("name", "callback-data-2"),
                entry("code", "$(request.code)"),
                entry("uuid", "$(response.id)"),
                entry("timestamp", "$(!Instant.cb2)"),
                entry("data", data));

        Callbacks arguments = Callbacks.of(
                Callback.of(100, callbackUrl, callbackData1),
                Callback.of(500, callbackUrl, callbackData2));

        stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", arguments)
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        stubFor(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String id = Json.node(responseJson).get("id").textValue();
        Thread.sleep(250);
        verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.uuid == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.name == 'callback-data-1')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.code == 'b63868c0')]")));

        Thread.sleep(450);
        verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.uuid == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.name == 'callback-data-2')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.code == 'b63868c0')]")));
    }

    @Test
    public void testCallbackWithFileResponse() throws InterruptedException {
        // tests callback data containing request _and_ response data as well as generated values
        String callbackUrl = "http://localhost:" + SERVER_PORT + "/callbacks";
        String postUrl = "/request/with/file/and/callback";
        String postData = "{\"name\":\"request-name\",\"url\":\"" + callbackUrl + "\"}";

        String responseJson = given().body(postData).contentType("application/json")
                .when().post(postUrl)
                .then().statusCode(201)
                .extract().asString();

        String id = Json.node(responseJson).get("id").textValue();
        String datetime = Json.node(responseJson).get("datetime").textValue();

        sleep();

        verify(1, postRequestedFor(urlEqualTo("/callbacks"))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.datetime == '" + datetime + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.random)]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp)]"))
                .withRequestBody(matchingJsonPath("$.[?(@.nested.instant)]"))
                .withRequestBody(matchingJsonPath("$.[?(@.nested.instant != '$(!Instant.plus[m10])')]")));

    }

    @Test
    public void testCallbackWithGetMethod() {

        String callbackPath = "/get/callbacks";
        String callbackUrl = "http://localhost:" + SERVER_PORT + callbackPath;
        stubFor(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        Map<String, Object> data = new HashMap<>();
        data.put("id", "$(response.id)");
        data.put("event", "callback-defined-event");
        data.put("name", "$(response.name)");
        data.put("timestamp", "$(!Timestamp)");
        Callbacks callbacks = Callbacks.of(100, callbackUrl, data);

        String getUrl = "/get/with/callback";
        String responseData = "{\"id\":\"$(!UUID)\", \"name\":\"response-name\"}";
        stubFor(get(getUrl)
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("content-type", "application/json")
                        .withBody(responseData)
                        .withTransformers("json-body-transformer")));

        String responseJson = given().accept("application/json")
                .get(getUrl).then().statusCode(200).extract().asString();

        JsonNode response = Json.node(responseJson);
        String id = response.get("id").textValue();
        String name = response.get("name").textValue();
        sleep();

        verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.event == 'callback-defined-event')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.name == '" + name + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp)]")));
    }

    @Test
    public void testCallbackWithGetMethodPathPart() {

        String callbackPath = "/get/callbacks";
        String callbackUrl = "http://localhost:" + SERVER_PORT + callbackPath;
        stubFor(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        Map<String, Object> data = new HashMap<>();
        data.put("id", "$(response.id)");
        data.put("event", "callback-defined-event");
        data.put("name", "$(response.name)");
        data.put("timestamp", "$(!Timestamp)");
        data.put("part", "$(urlParts[3])");
        Callbacks callbacks = Callbacks.of(100, callbackUrl, data);

        String getUrl = "/get/with/callback/path/part";
        String responseData = "{\"id\":\"$(!UUID)\", \"name\":\"response-name\"}";
        stubFor(get(getUrl)
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("content-type", "application/json")
                        .withBody(responseData)
                        .withTransformers("json-body-transformer")));

        String responseJson = given().accept("application/json")
                .get(getUrl).then().statusCode(200).extract().asString();

        JsonNode response = Json.node(responseJson);
        String id = response.get("id").textValue();
        String name = response.get("name").textValue();
        sleep();

        verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.event == 'callback-defined-event')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.name == '" + name + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.part == 'path')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp)]")));
    }

    @Test
    public void testCallbackWithProcessingException() {

        String callbackUrl = "http://www.example.com/callbacks";

        Map<String, Object> data = mapOf(entry("id", "$(response.id)"),
                entry("event", "callback-defined-event"),
                entry("name", "$(response.name)"),
                entry("timestamp", "$(!Timestamp)"));

        Map<String, Object> callback = mapOf(entry("delay", 100), entry("url", callbackUrl), entry("data", data));
        Map<String, Object> callbacks = mapOf(entry("callbacks", Arrays.asList(callback)));

        String getUrl = "/get/with/erroneous/callback";
        String responseData = "{\"id\":\"$(!UUID)\", \"name\":\"response-name\"}";
        stubFor(get(getUrl)
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("content-type", "application/json")
                        .withBody(responseData)
                        .withTransformers("json-body-transformer")));

        given().accept("application/json")
                .get(getUrl).then().statusCode(200).extract().asString();

        sleep();
        verify(0, postRequestedFor(urlEqualTo("/arbitrary/url")));
    }

    @Test
    public void testCallbackWithProcessingFailure() {

        String callbackUrl = "http://non.existing.host/callbacks";

        Map<String, Object> data = mapOf(entry("id", "$(response.id)"),
                entry("event", "callback-defined-event"),
                entry("name", "$(response.name)"),
                entry("timestamp", "$(!Timestamp)"));

        Map<String, Object> callback = mapOf(entry("delay", 100), entry("url", callbackUrl), entry("data", data));
        Map<String, Object> callbacks = mapOf(entry("callbacks", Arrays.asList(callback)));

        String getUrl = "/get/with/erroneous/callback";
        String responseData = "{\"id\":\"$(!UUID)\", \"name\":\"response-name\"}";
        stubFor(get(getUrl)
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("content-type", "application/json")
                        .withBody(responseData)
                        .withTransformers("json-body-transformer")));

        given().accept("application/json")
                .get(getUrl).then().statusCode(200).extract().asString();

        sleep();
        verify(0, postRequestedFor(urlEqualTo("/arbitrary/url")));
    }

    @Test
    public void testCallbackWithTraceId() throws InterruptedException {
        String requestUrl = "/request";
        String callbackPath = "/callback";

        String requestBody = "{\"code\":\"b63868c0\","
                + "\"callbacks\":{\"order_dispatched\":\"http://localhost:" + SERVER_PORT + callbackPath + "\"},"
                + "\"promised_delivery_at\":\"2019-03-14T16:09:19.748Z\","
                + "\"preparation_time\":10,\"preparation_buffer\":2}";
        String responseBody = "{\"id\":\"$(!UUID)\"}";
        String callbackUrl = "$(request.callbacks.order_dispatched)";
        CallbackData callbackData = CallbackData.of("aritrary-data");

        Callback callback = Callback.of(DELAY, callbackUrl, callbackData);
        callback.traceId = "my-fancy-trace-id";

        stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", Callbacks.of(callback))
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        stubFor(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String id = Json.node(responseJson).get("id").textValue();
        sleep();
        verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withHeader("X-Rps-TraceId", equalTo("my-fancy-trace-id"))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.value == '" + callbackData.value + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp == '" + callbackData.timestamp + "')]")));
    }

    @Test
    public void testSqsMessageCallbackInvalidEnvQueueName() {
        String requestUrl = "/request/sqs-env-queue/callback";
        String requestBody = "{\"code\":\"request-code\"}";

        String responseBody = "{\"id\":\"$(!UUID)\"}";

        Map<String, Object> sqsCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "sqs-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"));

        Callbacks callbacks = Callbacks.of(
                Callback.ofQueueMessage(100, "$(!ENV[INVALID_CALLBACK_QUEUE])", sqsCallbackData));

        stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201);
        sleep();
        List<Message> messages = sqsClient
                .receiveMessage(sqsClient.getQueueUrl(QUEUE_NAME).getQueueUrl())
                .getMessages();
        assertTrue(messages.isEmpty(), "unexpected messages received");
    }

    @Test
    public void testInvalidCallbackWithoutQueueAndUrl() {
        String requestUrl = "/request/sqs/invalid";
        String requestBody = "{\"code\":\"request-code\"}";
        String responseBody = "{\"id\":\"$(!UUID)\"}";

        Map<String, Object> sqsCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "sqs-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"));

        Map<String, Object> sqsCallback = mapOf(
                entry("delay", 100),
                entry("data", sqsCallbackData));

        Map<String, Object> callbacks = mapOf(entry("callbacks", Arrays.asList(sqsCallback)));

        stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201);

        sleep();

        List<Message> messages = sqsClient
                .receiveMessage(sqsClient.getQueueUrl(QUEUE_NAME).getQueueUrl())
                .getMessages();
        assertTrue(messages.isEmpty());
    }

    @Test
    public void testCallbackWithVerify() throws InterruptedException {

        String postUrl = "/callback/with/verification";
        String postData = "{\"name\":\"john doe\"}";

        String responseData = "{\"id\":\"$(!UUID)\",\"name\":\"$(name)\"}";

        String callbackPath = "/result/verification";
        String callbackResponseData = "{\"error_code\":\"my-fancy-error\"}";
        Integer callbackResponseStatus = 409;

        String callbackUrl = "http://localhost:" + SERVER_PORT + callbackPath;
        Callback callback = Callback.of(DELAY, callbackUrl, CallbackData.of("arbitrary-data"));
        callback.expectedHttpStatus = callbackResponseStatus;

        stubFor(post(urlEqualTo(postUrl))
                .withPostServeAction("callback-simulator", Callbacks.of(callback))
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseData)
                        .withTransformers("json-body-transformer")
                        .withStatus(200)));

        stubFor(post(urlPathEqualTo(callbackPath)).willReturn(aResponse()
                .withHeader("content-type", "application/json")
                .withBody(callbackResponseData)
                .withTransformers("json-body-transformer")
                .withStatus(callbackResponseStatus)));

        given().body(postData).contentType("application/json")
                .when().post(postUrl)
                .then().statusCode(200);

        sleep();
        verify(1, postRequestedFor(urlPathEqualTo(callbackPath)));
        verify(1, postRequestedFor(urlPathEqualTo("/callback/result"))
                .withRequestBody(matchingJsonPath("$.[?(@.target == '" + callbackUrl + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.response.status == " + callbackResponseStatus + ")]"))
                .withRequestBody(matchingJsonPath("$.[?(@.response.body == '" + callbackResponseData + "')]")));
    }

    private void sleep() {
        sleep(SLEEP);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
