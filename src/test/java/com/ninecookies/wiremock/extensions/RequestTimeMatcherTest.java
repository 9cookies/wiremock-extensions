package com.ninecookies.wiremock.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.jayway.restassured.RestAssured.when;

import org.joda.time.DateTimeFieldType;
import org.joda.time.Instant;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.github.tomakehurst.wiremock.extension.Parameters;

public class RequestTimeMatcherTest extends AbstractExtensionTest {

    private static final String URL = "/request/match/time";

    @BeforeMethod
    public void beforeMethod() {
        // reset mappings
        reset();
        // reset requests
        resetAllRequests();

        // by default return 400 Bad request for the request URL
        stubFor(any(urlEqualTo(URL))
                .willReturn(aResponse().withStatus(400)));
    }

    @Test
    public void testRequestTimeMatcherMatchesMinute() {
        String minute = String.valueOf(Instant.now().get(DateTimeFieldType.minuteOfHour()));
        if (minute.length() > 1) {
            minute = minute.substring(0, 1);
        } else {
            minute = "0";
        }
        String pattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:" + minute + "\\d{1}:\\d{2}\\..*";
        stubFor(any(urlEqualTo(URL))
                .atPriority(3)
                .andMatching("request-time-matcher", Parameters.one("pattern", pattern))
                .willReturn(aResponse().withStatus(200)));
        when().get(URL).then().statusCode(200);
    }

    @Test
    public void testRequestTimeMatcherNotMatchesMinute() {
        int minute = Instant.now().get(DateTimeFieldType.minuteOfHour());
        minute += 10;
        if (minute > 59) {
            minute = 1;
        }
        String sminute = String.valueOf(minute);
        if (sminute.length() > 1) {
            sminute = sminute.substring(0, 1);
        } else {
            sminute = "0";
        }

        String pattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:" + sminute + "\\d{1}:\\d{2}\\..*";
        stubFor(any(urlEqualTo(URL))
                .atPriority(3)
                .andMatching("request-time-matcher", Parameters.one("pattern", pattern))
                .willReturn(aResponse().withStatus(200)));
        when().get(URL).then().statusCode(400);
    }

    @Test
    public void testRequestTimeMatcherEmptyParamsDoesNotMatch() {
        stubFor(any(urlEqualTo(URL))
                .atPriority(3)
                .andMatching("request-time-matcher", Parameters.empty())
                .willReturn(aResponse().withStatus(200)));
        when().get(URL).then().statusCode(400);
    }

    @Test
    public void testRequestTimeMatcherEmptyValueDoesNotMatch() {
        stubFor(any(urlEqualTo(URL))
                .atPriority(3)
                .andMatching("request-time-matcher", Parameters.one("pattern", ""))
                .willReturn(aResponse().withStatus(200)));
        when().get(URL).then().statusCode(400);
    }

    @Test
    public void testRequestTimeMatcherNullValueDoesNotMatch() {
        Parameters parameters = new Parameters();
        parameters.put("pattern", null);
        stubFor(any(urlEqualTo(URL))
                .atPriority(3)
                .andMatching("request-time-matcher", parameters)
                .willReturn(aResponse().withStatus(200)));
        when().get(URL).then().statusCode(400);
    }

    @Test
    public void testRequestTimeMatcherArbitraryValueDoesNotMatch() {
        stubFor(any(urlEqualTo(URL))
                .atPriority(3)
                .andMatching("request-time-matcher", Parameters.one("pattern", "does-not-match"))
                .willReturn(aResponse().withStatus(200)));
        when().get(URL).then().statusCode(400);
    }
}
