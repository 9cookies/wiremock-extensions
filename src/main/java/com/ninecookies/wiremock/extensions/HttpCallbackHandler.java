package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.api.Authentication;

/**
 * Implements {@link Runnable} and uses {@link HttpPost} in combination with {@link HttpEntity} and
 * {@link HttpContext} to emit a POST request according to the referenced callback definition.
 */
public class HttpCallbackHandler implements Runnable {

    public static class HttpCallback {
        /**
         * The period of time in milliseconds to wait before the callback {@link #data} is POSTed to the {@link #url}.
         */
        public long delay;
        /**
         * The destination URL to POST the {@link #data} to after {@link #delay} has elapsed.
         */
        public String url;
        /**
         * The authentication to use for the callback.
         */
        public Authentication authentication;
        /**
         * The request id to use for the callback.
         */
        public String traceId;
        /**
         * The object representing arbitrary callback data.
         */
        public Object data;
    }

    private static final Logger LOG = LoggerFactory.getLogger(HttpCallbackHandler.class);
    private static final String RPS_TRACEID_HEADER = "X-Rps-TraceId";
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setSocketTimeout(2_000)
            .setConnectTimeout(3_000)
            .setConnectionRequestTimeout(5_000)
            .build();

    private ScheduledExecutorService executor;
    private int invocation;
    private int maxRetries;
    private int retryBackoff;
    private File callbackFile;

    @Override
    public void run() {
        LOG.debug("CallbackHandler.run()");
        boolean delete = false;
        try {
            HttpCallback callback = readCallback();
            URI uri = URI.create(callback.url);
            HttpContext context = createHttpContext(uri, callback.authentication);
            HttpEntity content = new StringEntity((String) callback.data, ContentType.APPLICATION_JSON);
            HttpPost post = new HttpPost(uri);
            post.setConfig(REQUEST_CONFIG);
            post.addHeader(RPS_TRACEID_HEADER, callback.traceId);
            post.setEntity(content);

            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                HttpResponse response = client.execute(post, context);
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    // in case of success, just print the status line
                    LOG.info("post to '{}' succeeded: response: {}", uri, response.getStatusLine());
                    delete = true;
                } else {
                    delete = rescheduleIfApplicable();
                    if (delete) {
                        String retryInfo = "";
                        if (maxRetries > 0) {
                            retryInfo = " after " + maxRetries + " attempts";
                        }
                        LOG.warn("post to '{}' failed{}: response: {}\n{}",
                                uri, retryInfo, response.getStatusLine(), readEntity(response.getEntity()));
                    } else {
                        LOG.info("post to '{}' will be retried : response: {}\n{}",
                                uri, response.getStatusLine(), readEntity(response.getEntity()));
                    }
                }
            } catch (Exception e) {
                // in failure case print request body and exception
                delete = rescheduleIfApplicable();
                if (delete) {
                    String retryInfo = "";
                    if (invocation > 1) {
                        retryInfo = " after " + invocation + " attempts";
                    }
                    LOG.error("post to '{}' errored{}\ncontent {}\n{}", uri, retryInfo, content,
                            readEntity(content),
                            e);
                } else {
                    LOG.warn("post to '{}' will be retried\ncontent {}\n{}", uri, content, readEntity(content), e);
                }

            }
        } catch (Exception e) {
            delete = true;
            LOG.error("unable to create http post", e);
        } finally {
            if (delete) {
                try {
                    Files.deleteIfExists(callbackFile.toPath());
                } catch (IOException e) {
                    LOG.error("unable to delete callback definition file", e);
                }
            }
        }
    }

    private boolean rescheduleIfApplicable() {
        invocation++;
        if (invocation <= maxRetries) {
            executor.schedule(this, retryBackoff * invocation, TimeUnit.MILLISECONDS);
            return false;
        }
        return true;
    }

    private HttpCallback readCallback() {
        try {
            String jsonContent = new String(Files.readAllBytes(callbackFile.toPath()), StandardCharsets.UTF_8);
            return Json.read(jsonContent, HttpCallback.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read callback content from file system", e);
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

    private HttpContext createHttpContext(URI uri, Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        CredentialsProvider credentialsProvider = null;
        switch (authentication.getType()) {
            case BASIC:
                credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(
                        authentication.getUsername(), authentication.getPassword()));
                break;
            default:
                throw new IllegalStateException("Unsupported type '" + authentication.getType() + "'");
        }
        HttpHost host = URIUtils.extractHost(uri);
        AuthCache authCache = new BasicAuthCache();
        authCache.put(host, new BasicScheme());
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credentialsProvider);
        context.setAuthCache(authCache);
        return context;
    }

    public static Runnable of(ScheduledExecutorService executor, int maxRetries, int retryBackoff,
            File callbackFile) {
        HttpCallbackHandler result = new HttpCallbackHandler();
        result.maxRetries = maxRetries;
        result.retryBackoff = retryBackoff;
        result.executor = executor;
        result.callbackFile = callbackFile;
        return result;
    }
}
