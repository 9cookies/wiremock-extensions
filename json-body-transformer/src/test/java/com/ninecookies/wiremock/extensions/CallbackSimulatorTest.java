package com.ninecookies.wiremock.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.restassured.RestAssured.given;
import static com.ninecookies.wiremock.extensions.Maps.entry;
import static com.ninecookies.wiremock.extensions.Maps.mapOf;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Json;
import com.jayway.restassured.RestAssured;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.api.Callbacks;

public class CallbackSimulatorTest {

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
    private WireMockServer wireMockServer;

    @BeforeClass
    public void beforeClass() {
        System.out.println("beforeClass()");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.log.com.ninecookies.wiremock.extensions", "debug");

        wireMockServer = new WireMockServer(wireMockConfig()
                .port(SERVER_PORT)
                .extensions(new CallbackSimulator(), new JsonBodyTransformer()));
        wireMockServer.start();

        RestAssured.port = SERVER_PORT;
    }

    @AfterClass(alwaysRun = true)
    public void afterClass() {
        wireMockServer.stop();
        System.out.println("afterClass()");
    }

    @BeforeMethod
    public void beforeMethod() {
        wireMockServer.resetRequests();
    }

    @Test
    public void testCallbackWithMapping() throws InterruptedException {
        String requestUrl = "/request/with/callback";

        String requestBody = "{\"code\":\"b63868c0\","
                + "\"promised_delivery_at\":\"2019-03-14T16:09:19.748Z\","
                + "\"preparation_time\":10,\"preparation_buffer\":2}";

        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String id = Json.node(responseJson).get("id").textValue();
        sleep();
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/callbacks"))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.value == 'defined-in-mapping-file')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp == '2019-03-04T17:05:43.596Z')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.driver.id == 'driver-id')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.driver.name == 'driver-name')]")));
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

        wireMockServer.stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", Callbacks.of(DELAY, callbackUrl, callbackData))
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        wireMockServer.stubFor(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String id = Json.node(responseJson).get("id").textValue();
        sleep();
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.value == '" + callbackData.value + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp == '" + callbackData.timestamp + "')]")));
    }

    @Test
    public void testMultipleCallbacksWithStubbing() throws InterruptedException {
        String requestUrl = "/request/multiple";
        String callbackPath = "/multiple/callbacks";

        String requestBody = "{\"code\":\"b63868c0\"}";
        String responseBody = "{\"id\":\"$(!UUID)\"}";
        String callbackUrl = "http://localhost:" + SERVER_PORT + "/multiple/callbacks";

        CallbackData callbackData1 = CallbackData.of("arbitrary-data-1");
        CallbackData callbackData2 = CallbackData.of("arbitrary-data-2");
        Callbacks arguments = Callbacks.of(
                Callback.of(100, callbackUrl, callbackData1),
                Callback.of(500, callbackUrl, callbackData2));

        wireMockServer.stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", arguments)
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        wireMockServer.stubFor(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String id = Json.node(responseJson).get("id").textValue();
        Thread.sleep(200);
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.value == '" + callbackData1.value + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp == '" + callbackData1.timestamp + "')]")));

        Thread.sleep(400);
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.value == '" + callbackData2.value + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp == '" + callbackData2.timestamp + "')]")));
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

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/callbacks"))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.datetime == '" + datetime + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.random)]"))
                .withRequestBody(matchingJsonPath("$.[?(@.timestamp)]")));

    }

    @Test
    public void testCallbackWithGetMethod() {

        String callbackPath = "/get/callbacks";
        String callbackUrl = "http://localhost:" + SERVER_PORT + callbackPath;
        wireMockServer.stubFor(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(204)));

        Map<String, Object> data = new HashMap<>();
        data.put("id", "$(response.id)");
        data.put("event", "callback-defined-event");
        data.put("name", "$(response.name)");
        data.put("timestamp", "$(!Timestamp)");
        Callbacks callbacks = Callbacks.of(100, callbackUrl, data);

        String getUrl = "/get/with/callback";
        String responseData = "{\"id\":\"$(!UUID)\", \"name\":\"response-name\"}";
        wireMockServer.stubFor(get(getUrl)
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

        wireMockServer.verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.id == '" + id + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.event == 'callback-defined-event')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.name == '" + name + "')]"))
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
        wireMockServer.stubFor(get(getUrl)
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("content-type", "application/json")
                        .withBody(responseData)
                        .withTransformers("json-body-transformer")));

        given().accept("application/json")
                .get(getUrl).then().statusCode(200).extract().asString();

        sleep();
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/arbitrary/url")));
    }

    private void sleep() {
        try {
            Thread.sleep(SLEEP); // 60_000 * 10);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
