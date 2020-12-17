package com.ninecookies.wiremock.extensions;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.restassured.RestAssured;
import com.ninecookies.wiremock.extensions.util.SystemUtil;

public class AbstractExtensionTest {

    private static final int SERVER_PORT = 9090;
    private WireMockServer wireMockServer;

    @BeforeClass
    public void beforeClass() {
        System.out.println("beforeClass()");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.log.com.ninecookies.wiremock.extensions", "debug");
        SystemUtil.setenv("CBUSER", "callback-user");
        SystemUtil.setenv("CBPASS", "callback-pass");

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
        System.out.println("afterClass()");
    }
}
