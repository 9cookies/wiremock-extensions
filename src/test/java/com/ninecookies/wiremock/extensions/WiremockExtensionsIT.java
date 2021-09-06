package com.ninecookies.wiremock.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.jayway.restassured.RestAssured.given;
import static com.ninecookies.wiremock.extensions.util.Maps.entry;
import static com.ninecookies.wiremock.extensions.util.Maps.mapOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazonaws.services.sns.AmazonSNS;
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

    private static final Logger LOG = LoggerFactory.getLogger(WiremockExtensionsIT.class);
    private static final int SERVER_PORT = 1062;
    private static final String QUEUE_NAME = "test-queue-name";
    private static final String TOPIC_NAME = "test-topic-name";
    private static final int LOCALSTACK_STARTUP_TIMEOUT = 30_000;

    private SQSConnection connection;
    private QueueMessageVerifier queueMonitor;

    @BeforeTest
    protected void beforeTest() {
        // configuration for the queue message verifier
        SystemUtil.setenv("AWS_REGION", "us-east-1");
        SystemUtil.setenv("AWS_SNS_ENDPOINT", "http://localhost:1060");
        SystemUtil.setenv("AWS_SQS_ENDPOINT", "http://localhost:1061");
        SystemUtil.setenv("AWS_ACCESS_KEY_ID", "X");
        SystemUtil.setenv("AWS_SECRET_ACCESS_KEY", "X");
    }

    @BeforeClass
    protected void beforeClass() throws JMSException {
        if (!CallbackConfiguration.getInstance().isMessagingEnabled()) {
            throw new IllegalStateException("AWS SQS messaging is disabled due to lacking configuration.");
        }

        // due to the fact that docker-maven-plugin wait for log message fails most likely with
        // [ERROR] DOCKER> IO Error while requesting logs: java.io.IOException: Bad file descriptor <THREAD_NAME>
        // we wait in this it for SNS and SQS do become ready
        waitForLocalstack();

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
        String messageId = UUID.randomUUID().toString();
        String requestUrl = "/request/sqs/callback";
        String requestBody = "{\"code\":\"request-code\",\"messageId\":\"" + messageId + "\"}";
        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String responseId = Json.node(responseJson).get("id").textValue();

        String messageText = queueMonitor.waitForMessage(messageId);

        JsonNode message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("url_parts_1").textValue(), "sqs");
        assertEquals(message.get("defined_value").textValue(), "from-mapping-file");
    }

    @Test
    public void testSnsMessageCallback() {
        String messageId = UUID.randomUUID().toString();
        String requestUrl = "/request/sns/callback";
        String requestBody = "{\"code\":\"request-code\",\"messageId\":\"" + messageId + "\"}";
        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201)
                .extract().asString();

        String responseId = Json.node(responseJson).get("id").textValue();

        String messageText = queueMonitor.waitForMessage(messageId);

        JsonNode message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("url_parts_1").textValue(), "sns");
        assertEquals(message.get("defined_value").textValue(), "from-mapping-file");
    }

    @Test
    public void testSqsComplexMessageCallback() {
        String requestUrl = "/request/sqs-complex/callback";
        String messageId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        String requestBody =
                "{\"code\":\"request-code\", \"id\": \"" + requestId + "\", \"messageId\":\"" + messageId + "\"}";
        given().body(requestBody).contentType("application/json")
                .when().post(requestUrl)
                .then().statusCode(201);

        String messageText = queueMonitor.waitForMessage(messageId);
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
        String messageId = UUID.randomUUID().toString();

        Map<String, Object> sqsCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "sqs-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"),
                entry("messageId", messageId));

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
        String messageText = queueMonitor.waitForMessage(messageId);
        JsonNode message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("data").textValue(), "sqs-data");
    }

    @Test
    public void testCombinedCallbacks() {
        String requestUrl = "/request/combined/callback";
        String requestBody = "{\"code\":\"request-code\"}";

        String responseBody = "{\"id\":\"$(!UUID)\"}";

        String callbackPath = "/callback/combined";
        String callbackUrl = "http://host.docker.internal:" + SERVER_PORT + callbackPath;

        Map<String, Object> httpCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "url-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"));

        String sqsMessageId = UUID.randomUUID().toString();
        Map<String, Object> sqsCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "sqs-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"),
                entry("messageId", sqsMessageId));

        String snsMessageId = UUID.randomUUID().toString();
        Map<String, Object> snsCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "sns-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"),
                entry("messageId", snsMessageId));

        Callbacks callbacks = Callbacks.of(
                Callback.of(100, callbackUrl, httpCallbackData),
                Callback.ofQueueMessage(100, QUEUE_NAME, sqsCallbackData),
                Callback.ofTopicMessage(100, TOPIC_NAME, snsCallbackData));

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

        String messageText = queueMonitor.waitForMessage(sqsMessageId);
        JsonNode message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("data").textValue(), "sqs-data");

        messageText = queueMonitor.waitForMessage(snsMessageId);
        message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("data").textValue(), "sns-data");

        verify(1, postRequestedFor(urlEqualTo(callbackPath))
                .withRequestBody(matchingJsonPath("$.[?(@.response_id == '" + responseId + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.request_code == 'request-code')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.data == 'url-data')]")));
    }

    @Test
    public void testSqsMessageCallbackWithRequestAndEnvQueueName() {
        String requestUrl = "/request/sqs-env-queue/callback";
        String requestBody = "{\"code\":\"request-code\",\"partial\":\"test\"}";
        String responseBody = "{\"id\":\"$(!UUID)\"}";
        String messageId = UUID.randomUUID().toString();

        Map<String, Object> sqsCallbackData = mapOf(entry("response_id", "$(response.id)"),
                entry("data", "sqs-data"),
                entry("request_code", "$(request.code)"),
                entry("timestamp", "$(!Timestamp)"),
                entry("messageId", messageId));

        Callbacks callbacks = Callbacks.of(
                Callback.ofQueueMessage(100, "$(request.partial)-$(!ENV[PARTIAL_QUEUE_NAME])", sqsCallbackData));

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
        String messageText = queueMonitor.waitForMessage(messageId);
        JsonNode message = Json.node(messageText);
        assertEquals(message.get("response_id").textValue(), responseId);
        assertEquals(message.get("request_code").textValue(), "request-code");
        assertEquals(message.get("data").textValue(), "sqs-data");
    }

    @Test
    public void testSqsCommonContext() {
        String requestPath = "/request/common/context";
        String responseBody = "{\"id\":\"$(!UUID)\"}";
        String requestBody = "{\"code\":\"request-code\"}";
        String sqsMessage1Id = UUID.randomUUID().toString();
        String sqsMessage2Id = UUID.randomUUID().toString();

        Callbacks callbacks = Callbacks.of(
                Callback.ofQueueMessage(100, QUEUE_NAME, mapOf(entry("response_id", "$(response.id)"),
                        entry("data", "sqs-data-1"),
                        entry("request_code", "$(request.code)"),
                        entry("timestamp", "$(!Instant.data-1)"),
                        entry("common_timestamp", "$(!OffsetDateTime)"),
                        entry("common_uuid", "$(!UUID.common)"),
                        entry("common_name", "named $(!UUID.common)"),
                        entry("messageId", sqsMessage1Id))),
                Callback.ofQueueMessage(200, QUEUE_NAME, mapOf(entry("response_id", "$(response.id)"),
                        entry("data", "sqs-data-2"),
                        entry("request_code", "$(request.code)"),
                        entry("timestamp", "$(!Instant.data-2)"),
                        entry("common_timestamp", "$(!OffsetDateTime)"),
                        entry("common_uuid", "$(!UUID.common)"),
                        entry("common_name", "named $(!UUID.common)"),
                        entry("messageId", sqsMessage2Id))));

        stubFor(post(urlEqualTo(requestPath))
                .withPostServeAction("callback-simulator", callbacks)
                .willReturn(aResponse()
                        .withHeader("content-type", "application/json")
                        .withBody(responseBody)
                        .withTransformers("json-body-transformer")
                        .withStatus(201)));

        String responseJson = given().body(requestBody).contentType("application/json")
                .when().post(requestPath)
                .then().statusCode(201)
                .extract().asString();
        String responseId = Json.node(responseJson).get("id").textValue();

        String messageText = queueMonitor.waitForMessage(sqsMessage1Id);
        JsonNode message1 = Json.node(messageText);
        assertEquals(message1.get("response_id").textValue(), responseId);
        assertEquals(message1.get("request_code").textValue(), "request-code");
        assertEquals(message1.get("data").textValue(), "sqs-data-1");

        messageText = queueMonitor.waitForMessage(sqsMessage2Id);
        JsonNode message2 = Json.node(messageText);
        assertEquals(message2.get("response_id").textValue(), responseId);
        assertEquals(message2.get("request_code").textValue(), "request-code");
        assertEquals(message2.get("data").textValue(), "sqs-data-2");
        assertEquals(message2.get("common_uuid").textValue(), message1.get("common_uuid").textValue());
        assertEquals(message2.get("common_name").textValue(), message1.get("common_name").textValue());
        assertEquals(message2.get("common_timestamp").textValue(), message1.get("common_timestamp").textValue());
        assertNotEquals(message2.get("timestamp").textValue(), message1.get("timestamp").textValue());
    }

    @Test
    public void testCallbackWithVerify() throws InterruptedException {

        String postUrl = "/callback/with/verification";
        String postData = "{\"name\":\"john doe\"}";

        String responseData = "{\"id\":\"$(!UUID)\",\"name\":\"$(name)\"}";

        String callbackPath = "/result/verification";
        String callbackResponseData = "{\"error_code\":\"my-fancy-error\"}";
        Integer callbackResponseStatus = 409;

        String callbackUrl = "http://host.docker.internal:" + SERVER_PORT + callbackPath;

        Callback callback = Callback.of(100, callbackUrl, mapOf(entry("data", "arbitrary-data")));
        callback.expectedHttpStatus = callbackResponseStatus;
        String sqsMessageId = UUID.randomUUID().toString();

        stubFor(post(urlEqualTo(postUrl))
                .withPostServeAction("callback-simulator", Callbacks.of(
                        callback,
                        Callback.ofQueueMessage(100, QUEUE_NAME, mapOf(entry("messageId", sqsMessageId)))))
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

        queueMonitor.waitForMessage(sqsMessageId);

        verify(1, postRequestedFor(urlPathEqualTo(callbackPath)));
        verify(1, postRequestedFor(urlPathEqualTo("/callback/result"))
                .withRequestBody(matchingJsonPath("$.[?(@.target == '" + callbackUrl + "')]"))
                .withRequestBody(matchingJsonPath("$.[?(@.response.status == " + callbackResponseStatus + ")]"))
                .withRequestBody(matchingJsonPath("$.[?(@.response.body == '" + callbackResponseData + "')]")));
    }

    /*
     * privates below
     */

    private boolean isRunning(Runnable runnable) {
        try {
            runnable.run();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void waitForService(Runnable service, long waitUntil) {
        while (System.currentTimeMillis() < waitUntil) {
            if (!isRunning(service)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                return;
            }
        }
        throw new IllegalStateException("waited for " + LOCALSTACK_STARTUP_TIMEOUT + " millis for local stack");
    }

    private void waitForLocalstack() {
        long start = System.currentTimeMillis();
        long waitUntil = System.currentTimeMillis() + LOCALSTACK_STARTUP_TIMEOUT;

        AmazonSNS sns = CallbackConfiguration.getInstance().createSnsClient();
        waitForService(sns::listTopics, waitUntil);
        sns.shutdown();

        AmazonSQS sqs = CallbackConfiguration.getInstance().createSqsClient();
        waitForService(() -> sqs.getQueueUrl(QUEUE_NAME), waitUntil);
        sqs.shutdown();

        LOG.info("waited for {} millis for local stack!", System.currentTimeMillis() - start);
    }
}
