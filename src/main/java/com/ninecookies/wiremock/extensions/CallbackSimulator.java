package com.ninecookies.wiremock.extensions;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.jayway.jsonpath.DocumentContext;
import com.ninecookies.wiremock.extensions.api.Authentication;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.api.Callbacks;
import com.ninecookies.wiremock.extensions.util.Objects;
import com.ninecookies.wiremock.extensions.util.Placeholder;
import com.ninecookies.wiremock.extensions.util.Placeholders;

/**
 * Implements the {@link PostServeAction} interface and provides the ability to specify callback invocations for request
 * mappings.
 * <p>
 * This class utilizes the {@link ScheduledExecutorService} and configures it with a core pool size of
 * {@value #CORE_POOL_SIZE} and a {@link ThreadFactory} that produces daemon {@link Thread}s.
 *
 * @author M.Scheepers
 * @since 0.0.6
 */
public class CallbackSimulator extends PostServeAction {

    private static final Logger LOG = LoggerFactory.getLogger(CallbackSimulator.class);
    private static final int CORE_POOL_SIZE = 50;
    private static int instances = 0;
    private final long instance = ++instances;

    private final ScheduledExecutorService executor = Executors
            .newScheduledThreadPool(CORE_POOL_SIZE, new DaemonThreadFactory());

    @Override
    public String getName() {
        return "callback-simulator";
    }

    @Override
    public void doAction(ServeEvent serveEvent, Admin admin, Parameters parameters) {
        LOG.debug("doAction[{}](serveEvent: {}, admin: {}, parameters: {})", instance, serveEvent, admin, parameters);

        List<String> urlParts = Placeholders.splitUrl(serveEvent.getRequest().getUrl());

        // compose JSON path parsable request/response/path json
        DocumentContext servedJson = Placeholders.documentContextOf("{\"request\":"
                + serveEvent.getRequest().getBodyAsString() + ", \"response\":"
                + serveEvent.getResponse().getBodyAsString() + ", \"urlParts\":"
                + Json.write(urlParts) + "}");

        Callbacks callbacks = parameters.as(Callbacks.class);

        for (Callback callback : callbacks.callbacks) {
            LOG.debug("callback.data: {}", Objects.describe(callback.data));
            String dataJson = Placeholders.transformJson(servedJson, Json.write(callback.data));
            LOG.debug("final data: {}", dataJson);
            Object url = Placeholder.containsPattern(callback.url)
                    ? Placeholder.of(callback.url).getSubstitute(servedJson)
                    : callback.url;
            LOG.info("scheduling callback task to: '{}' with delay '{}' and data '{}'", url, callback.delay, dataJson);

            executor.schedule(
                    PostTask.of(url.toString(), callback.authentication, dataJson),
                    callback.delay,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Implements {@link ThreadFactory} producing daemon threads ({@link Thread#isDaemon()} is {@code true}) to use
     * with {@link ScheduledExecutorService} to avoid that {@link CallbackSimulator} blocks WireMock shutdown.
     */
    private static final class DaemonThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String name;

        private DaemonThreadFactory() {
            name = "callback-timer-" + POOL_NUMBER.getAndIncrement() + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread result = new Thread(r, name + threadNumber.getAndIncrement());
            if (!result.isDaemon()) {
                result.setDaemon(true);
            }
            if (result.getPriority() != Thread.NORM_PRIORITY) {
                result.setPriority(Thread.NORM_PRIORITY);
            }
            return result;
        }
    }

    /**
     * Implements {@link Runnable} and uses {@link HttpPost} in combination with {@link HttpEntity} and
     * {@link HttpContext} to POST the specified callback JSON payload to the specified callback URL.
     */
    private static final class PostTask implements Runnable {

        private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
                .setSocketTimeout(2_000)
                .setConnectTimeout(3_000)
                .setConnectionRequestTimeout(5_000)
                .build();

        private URI uri;
        private HttpEntity content;
        private HttpContext context;

        @Override
        public void run() {
            HttpPost post = new HttpPost(uri);
            post.setConfig(REQUEST_CONFIG);
            post.setEntity(content);
            post.addHeader("X-Rps-TraceId", UUID.randomUUID().toString().replace("-", ""));

            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                HttpResponse response = client.execute(post, context);
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    // in case of success, just print the status line
                    LOG.info("post to '{}' succeeded: response: {}", uri, response.getStatusLine());
                } else {
                    // in error case also print response body if available
                    LOG.warn("post to '{}' failed: response: {}\n{}",
                            uri, response.getStatusLine(), readEntity(response.getEntity()));
                }
            } catch (Exception e) {
                // in failure case print request body and exception
                LOG.error("post to '{}' errored\ncontent\n{}\n{}",
                        uri, content, readEntity(content), e);
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

        private static Runnable of(String uri, Authentication authentication, String jsonContent) {
            PostTask result = new PostTask();
            result.uri = URI.create(uri);
            result.content = new StringEntity(jsonContent, ContentType.APPLICATION_JSON);
            result.context = createHttpContext(result.uri, authentication);
            return result;
        }

        private static HttpContext createHttpContext(URI uri, Authentication authentication) {
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
    }
}
