package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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
    private static final int DEFAULT_RETRY_BACKOFF = 5_000;
    private static final int DEFAULT_MAX_RETRIES = 0;
    private static int instances = 0;
    private final long instance = ++instances;
    private final int retryBackoff;
    private final int maxRetries;

    private final ScheduledExecutorService executor;

    public CallbackSimulator() {
        int corePoolSize = parseEnvironmentSetting("SCHEDULED_THREAD_POOL_SIZE", CORE_POOL_SIZE);
        // ensure minimum default thread pool size
        if (corePoolSize < CORE_POOL_SIZE) {
            corePoolSize = CORE_POOL_SIZE;
        }
        retryBackoff = parseEnvironmentSetting("RETRY_BACKOFF", DEFAULT_RETRY_BACKOFF);
        maxRetries = parseEnvironmentSetting("MAX_RETRIES", DEFAULT_MAX_RETRIES);
        LOG.info("instance: {} - using SCHEDULED_THREAD_POOL_SIZE {} - RETRY_BACKOFF {} - MAX_RETRIES {}",
                instance, corePoolSize, retryBackoff, maxRetries);
        executor = Executors.newScheduledThreadPool(corePoolSize, new DaemonThreadFactory());
    }

    private int parseEnvironmentSetting(String name, int defaultValue) {
        int result = defaultValue;
        try {
            String poolSizeEnv = System.getenv(name);
            if (poolSizeEnv != null) {
                result = Integer.parseInt(poolSizeEnv);
            }
        } catch (Exception e) {
            LOG.error("unable to read environment variable '{}'", name, e);
        }
        return result;
    }

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
            Callback normalizedCallback = normalizeCallback(servedJson, callback);
            File callbackDefinition = persistCallback(normalizedCallback);
            LOG.info("instance {} - scheduling callback task to: '{}' with delay '{}' and data '{}'",
                    instance, callback.url, callback.delay, callback.data);
            Runnable callbackHandler = CallbackHandler.of(executor, maxRetries, retryBackoff, callbackDefinition);
            executor.schedule(callbackHandler, callback.delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Normalizes the specified {@code callback} according to the specified {@code servedJson} and replaces placeholder
     * patterns in {@link Callback#data} as well as in {@link Callback#url}.<br>
     * In addition it ensures that the {@link Callback#traceId} is present.
     *
     * @param servedJson a {@link DocumentContext} representing the request and response bodies as well as the request
     *            path.
     * @param callback the {@link Callback} to normalize.
     * @return the normalized {@link Callback} with replaced patterns and keywords according to the specified
     *         {@code servedJson}.
     */
    private Callback normalizeCallback(DocumentContext servedJson, Callback callback) {
        LOG.debug("url: {} data: {}", callback.url, Objects.describe(callback.data));
        callback.data = Placeholders.transformJson(servedJson, Json.write(callback.data));
        callback.url = Placeholders.transformUrl(servedJson, callback.url);
        if (callback.traceId == null) {
            callback.traceId = UUID.randomUUID().toString().replace("-", "");
        }
        LOG.debug("final url: {} data: {}", callback.url, callback.data);
        return callback;
    }

    /**
     * Persists the specified {@code callback} as temporary file in the file system to be picked up by the
     * scheduled {@link CallbackHandler} when due to reduce the memory footprint during callback handling.
     *
     * @param callback the {@link Callback} to persist.
     * @return the temporary {@link File} containing the normalized callback definition.
     */
    private File persistCallback(Callback callback) {
        try {
            File result = File.createTempFile("callback-json-", ".tmp");
            LOG.debug("callback-json file: {}", result);
            String jsonContent = Json.write(callback);
            LOG.debug("callback-json content: {}", jsonContent);
            Files.write(result.toPath(), jsonContent.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("unable to persist callback data", e);
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
     * {@link HttpContext} to emit a POST request according to the referenced callback definition.
     */
    private static final class CallbackHandler implements Runnable {

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
                Callback callback = readCallback();
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

        private Callback readCallback() {
            try {
                String jsonContent = new String(Files.readAllBytes(callbackFile.toPath()), StandardCharsets.UTF_8);
                return Json.read(jsonContent, Callback.class);
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

        private static Runnable of(ScheduledExecutorService executor, int maxRetries, int retryBackoff,
                File callbackFile) {
            CallbackHandler result = new CallbackHandler();
            result.maxRetries = maxRetries;
            result.retryBackoff = retryBackoff;
            result.executor = executor;
            result.callbackFile = callbackFile;
            return result;
        }
    }
}
