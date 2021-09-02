package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;

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

import com.ninecookies.wiremock.extensions.HttpCallbackHandler.HttpCallbackDefinition;
import com.ninecookies.wiremock.extensions.api.Authentication;

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
        try {
            URI uri = URI.create(callback.target);
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
                    getLog().info("post to '{}' succeeded: response: {}", uri, response.getStatusLine());
                } else {
                    throw new RetryCallbackException(String.format(
                            "post to '%s' failed: response: %s\n%s",
                            uri, response.getStatusLine(), readEntity(response.getEntity())));
                }
            } catch (RetryCallbackException e) {
                throw e;
            } catch (Exception e) {
                throw new RetryCallbackException(String.format("post to '%s' errored\ncontent %s",
                        uri, readEntity(content)), e);
            }
        } catch (RetryCallbackException e) {
            throw e;
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

    public static Runnable of(ScheduledExecutorService executor, File callbackFile) {
        return new HttpCallbackHandler(executor, callbackFile);
    }
}
