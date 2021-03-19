package com.ninecookies.wiremock.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.jayway.restassured.RestAssured.given;
import static com.ninecookies.wiremock.extensions.util.Maps.entry;
import static com.ninecookies.wiremock.extensions.util.Maps.mapOf;
import static org.testng.Assert.assertEquals;

import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Json;
import com.jayway.restassured.RestAssured;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.api.Callbacks;
import com.ninecookies.wiremock.extensions.test.QueueMessageVerifier;
import com.ninecookies.wiremock.extensions.util.SystemUtil;

public class WiremockExtensionsIT {

    private static final int SERVER_PORT = 9090;
    private static final String QUEUE_NAME = "test-queue-name";

    private SQSConnection connection;
    private QueueMessageVerifier queueMonitor;

    @BeforeTest
    protected void beforeTest() {
        SystemUtil.setenv("AWS_REGION", "us-east-1");
        SystemUtil.setenv("AWS_SQS_ENDPOINT", "http://localhost:9324");
        SystemUtil.setenv("AWS_ACCESS_KEY_ID", "X");
        SystemUtil.setenv("AWS_SECRET_ACCESS_KEY", "X");
    }

    @BeforeClass
    protected void beforeClass() throws JMSException {
        if (!CallbackConfiguration.getInstance().isSqsMessagingEnabled()) {
            throw new IllegalStateException("AWS SQS messaging is disabled due to lacking configuration.");
        }
        connection = CallbackConfiguration.getInstance().createConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));

        queueMonitor = new QueueMessageVerifier();
        consumer.setMessageListener(queueMonitor);

        connection.start();

        RestAssured.port = SERVER_PORT;
        WireMock.configureFor(SERVER_PORT);
    }

    @AfterClass(alwaysRun = true)
    protected void afterClass() {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }
    }

    @BeforeMethod
    protected void beforeMethod() {
        AmazonSQS client = connection.getAmazonSQSClient();
        client.purgeQueue(new PurgeQueueRequest(client.getQueueUrl(QUEUE_NAME).getQueueUrl()));
        queueMonitor.reset();

        reset();
        resetAllRequests();
    }

    @Test
    public void testSqsMessageCallback() {
        String requestUrl = "/request/sqs/callback";
        String requestBody = "{\"code\":\"request-code\"}";
        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String responseId = Json.node(responseJson).get("id").textValue();

        String messageText = queueMonitor.waitForMessage();

        JsonNode message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("url_parts_1").textValue(), "sqs");
        assertEquals(message.get("defined_value").textValue(), "from-mapping-file");
    }

    @Test
    public void testSqsComplexMessageCallback() {
        String requestUrl = "/request/sqs-complex/callback";
        String requestId = UUID.randomUUID().toString();
        String requestBody = "{\"code\":\"request-code\", \"id\": \"" + requestId + "\"}";
        given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201);

        String messageText = queueMonitor.waitForMessage();
        JsonNode message = Json.node(messageText);
        JsonNode metadata = message.get("metadata");
        assertEquals(metadata.get("type").asText(), "metadata-type");
        JsonNode content = message.get("content");
        assertEquals(content.get("requestId").asText(), requestId);
        // content.payload is escaped JSON string
        JsonNode payload = Json.node(content.get("payload").asText());
        assertEquals(payload.get("event").textValue(), "some-event");
        assertEquals(payload.get("order").get("code").asText(), "mWcanDjW");
    }

    @Test
    public void testSqsMessageCallbackWithEnvQueueName() {
        String requestUrl = "/request/sqs-env-queue/callback";
        String requestBody = "{\"code\":\"request-code\"}";
        String responseBody = "{\"id\":\"$(!UUID)\"}";

        Map<String, Object> sqsCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "sqs-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"));

        Callbacks callbacks = Callbacks.of(
                Callback.ofQueueMessage(100, "$(!ENV[CALLBACK_QUEUE])", sqsCallbackData));

        stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String responseId = Json.node(responseJson).get("id").textValue();
        String messageText = queueMonitor.waitForMessage();
        JsonNode message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("data").textValue(), "sqs-data");
    }

    @Test
    public void testSqsAndHttpCombinedCallbacks() {
        String requestUrl = "/request/sqs-and-http/callback";
        String requestBody = "{\"code\":\"request-code\"}";

        String responseBody = "{\"id\":\"$(!UUID)\"}";

        String callbackPath = "/callback/sqs-and-http";
        String callbackUrl = "http://localhost:" + SERVER_PORT + callbackPath;

        Map<String, Object> httpCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "url-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"));

        Map<String, Object> sqsCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "sqs-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"));

        Callbacks callbacks = Callbacks.of(
                Callback.of(100, callbackUrl, httpCallbackData),
                Callback.ofQueueMessage(100, QUEUE_NAME, sqsCallbackData));

        stubFor(post(urlEqualTo(requestUrl))
                .withPostServeAction("callback-simulator", callbacks)
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

        String responseId = Json.node(responseJson).get("id").textValue();

        String messageText = queueMonitor.waitForMessage();
        JsonNode message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("data").textValue(), "sqs-data");

        verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.response_id == '" + responseId + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.request_code == 'request-code')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.data == 'url-data')]")));
    }
}
