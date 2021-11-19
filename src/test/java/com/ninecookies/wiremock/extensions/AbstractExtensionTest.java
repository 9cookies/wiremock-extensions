package com.ninecookies.wiremock.extensions;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.restassured.RestAssured;
import com.ninecookies.wiremock.extensions.util.SystemUtil;

public class AbstractExtensionTest {

    private static final int SQS_PORT = 9060;
    private SQSRestServer sqsServer;
    protected AmazonSQS sqsClient;

    private static final int SERVER_PORT = 9090;
    private WireMockServer wireMockServer;

    @BeforeClass
    public void beforeClass() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.log.com.ninecookies.wiremock.extensions", "debug");
        SystemUtil.setenv("CBUSER", "callback-user");
        SystemUtil.setenv("CBPASS", "callback-pass");
        SystemUtil.setenv("CBTOKEN", "callback-token");
        SystemUtil.setenv("CALLBACK_QUEUE", "test-queue-name");
        SystemUtil.setenv("AWS_SQS_ENDPOINT", "http://localhost:" + SQS_PORT);
        SystemUtil.setenv("AWS_REGION", "us-east-1");

        sqsServer = SQSRestServerBuilder
                .withInterface("localhost").withPort(SQS_PORT)
                .start();

        sqsClient = AmazonSQSClientBuilder.standard()
                .withEndpointConfiguration(new EndpointConfiguration("http://localhost:" + SQS_PORT, "us-east-1"))
                .build();
        sqsClient.createQueue("test-queue-name");

        wireMockServer = new WireMockServer(wireMockConfig()
                .port(SERVER_PORT)
                .extensions(new CallbackSimulator(), new JsonBodyTransformer(), new RequestTimeMatcher()));
        wireMockServer.start();

        RestAssured.port = SERVER_PORT;

        WireMock.configureFor(SERVER_PORT);
    }

    @AfterClass(alwaysRun = true)
    public void afterClass() {
        wireMockServer.stop();
        sqsServer.stopAndWait();
    }
}
