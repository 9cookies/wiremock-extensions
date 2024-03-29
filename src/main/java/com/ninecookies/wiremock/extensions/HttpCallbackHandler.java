package com.ninecookies.wiremock.extensions;

import static com.ninecookies.wiremock.extensions.util.Maps.entry;
import static com.ninecookies.wiremock.extensions.util.Maps.mapOf;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.HttpCallbackHandler.HttpCallbackDefinition;
import com.ninecookies.wiremock.extensions.api.Authentication;
import com.ninecookies.wiremock.extensions.api.Authentication.Type;

/**
 * Implements {@link Runnable} and uses {@link HttpPost} in combination with {@link HttpEntity} and
 * {@link HttpContext} to emit a POST request according to the referenced callback definition.
 */
public class HttpCallbackHandler extends AbstractCallbackHandler<HttpCallbackDefinition> {

    public static class HttpCallbackDefinition extends CallbackDefinition {
        /**
         * The authentication to use for the callback.
         */
        public Authentication authentication;
        /**
         * The request id to use for the callback.
         */
        public String traceId;
        /**
         * The expected HTTP response status for the callback.
         * If omitted all 2xx HTTP status results are considered successful.
         */
        public Integer expectedHttpStatus;
        /**
         * Port to record success events for verification purposes.
         */
        public Integer localWiremockPort;
        /**
         * Ability to skip callback result reporting if journal is disabled.
         */
        public boolean skipResultReport;
    }

    private static class HttpStatusRange {
        private final int minStatus;
        private final int maxStatus;

        public HttpStatusRange(Integer expectedStatus) {
            if (expectedStatus != null) {
                minStatus = expectedStatus;
                maxStatus = minStatus + 1;
            } else {
                minStatus = 200;
                maxStatus = 300;
            }
        }

        public boolean matches(int status) {
            return status >= minStatus && status < maxStatus;
        }
    }

    private static class CallbackResponse {
        private String statusLine;
        private int statusCode;
        private String entityString;

        private static CallbackResponse of(String statusLine, int statusCode, String entityString) {
            CallbackResponse response = new CallbackResponse();
            response.statusLine = statusLine;
            response.statusCode = statusCode;
            response.entityString = entityString;
            return response;
        }
    }

    private static final String RPS_TRACEID_HEADER = "X-Rps-TraceId";
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setSocketTimeout(2_000)
            .setConnectTimeout(3_000)
            .setConnectionRequestTimeout(5_000)
            .build();

    private HttpCallbackHandler(ScheduledExecutorService executor, File callbackFile) {
        super(executor, callbackFile, HttpCallbackDefinition.class);
    }

    @Override
    public void handle(HttpCallbackDefinition callback) throws CallbackException {
        getLog().debug("CallbackHandler.run()");

        URI uri = createURI(callback.target);
        HttpPost post = createPostRequest(uri, (String) callback.data);
        post.addHeader(RPS_TRACEID_HEADER, callback.traceId);
        post.addHeader(authenticationToHeader(callback.authentication));

        CallbackResponse response = performRequest(post);

        HttpStatusRange expectedStatus = new HttpStatusRange(callback.expectedHttpStatus);
        if (expectedStatus.matches(response.statusCode)) {
            // in case of success, just print the status line
            getLog().info("post to '{}' succeeded: response: {}", uri, response.statusLine);
            recordSuccess(callback, response);
            return;
        }
        throw new RetryCallbackException(String.format(
                "post to '%s' failed: response: %s\n%s",
                uri, response.statusLine, response.entityString));
    }

    private void recordSuccess(HttpCallbackDefinition callback, CallbackResponse response) throws CallbackException {
        if (callback.skipResultReport) {
            getLog().debug("journal disabled - skip callback result report for '{}'", callback.target);
            return;
        }

        URI uri = createURI(String.format("http://localhost:%s/callback/result", callback.localWiremockPort));

        Map<String, Object> data = mapOf(
                entry("result", "success"),
                entry("target", callback.target),
                entry("response", mapOf(
                        entry("status", response.statusCode),
                        entry("body", String.valueOf(response.entityString)))));
        String bodyData = Json.write(data);
        HttpPost post = createPostRequest(uri, bodyData);
        try {
            CallbackResponse reportResponse = performRequest(post);
            getLog().debug("report post \n{}\n\tto '{}' succeeded: response: {}",
                    bodyData, uri, reportResponse.statusLine);
        } catch (RetryCallbackException e) {
            throw new CallbackException("unable to record callback success result", e);
        }
    }

    private URI createURI(String url) throws CallbackException {
        try {
            return URI.create(url);
        } catch (Exception e) {
            throw new CallbackException("Unable to create URI of '" + url + "'.", e);
        }
    }

    private CallbackResponse performRequest(HttpPost request) throws RetryCallbackException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpResponse response = client.execute(request);
            return CallbackResponse.of(response.getStatusLine().toString(),
                    response.getStatusLine().getStatusCode(),
                    readEntity(response.getEntity()));
        } catch (IOException e) {
            throw new RetryCallbackException(String.format("post to '%s' errored\ncontent %s",
                    request.getURI(), readEntity(request.getEntity())), e);
        }
    }

    private HttpPost createPostRequest(URI uri, String body) throws CallbackException {
        try {
            HttpPost post = new HttpPost(uri);
            post.setConfig(REQUEST_CONFIG);
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            return post;
        } catch (Exception e) {
            throw new CallbackException("unable to create http post", e);
        }
    }

    private String readEntity(HttpEntity entity) {
        String result = null;
        if (entity != null) {
            try {
                result = EntityUtils.toString(entity);
            } catch (ParseException | IOException e) {
                /* ignored */
            }
        }
        return result;
    }

    private Header authenticationToHeader(Authentication authentication) throws CallbackException {
        if (authentication == null) {
            return null;
        }
        if (Type.BASIC == authentication.getType()) {
            String userPass = String.format("%s:%s", authentication.getUsername(), authentication.getPassword());
            String base64 = Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8));
            return new BasicHeader(HttpHeaders.AUTHORIZATION, "Basic " + base64);
        }
        if (Type.BEARER == authentication.getType()) {
            return new BasicHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken());
        }
        throw new CallbackException("Unsupported authentication type '" + authentication.getType() + "'");
    }

    public static Runnable of(ScheduledExecutorService executor, File callbackFile) {
        return new HttpCallbackHandler(executor, callbackFile);
    }
}
