package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.io.IOException;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.jayway.jsonpath.DocumentContext;
import com.ninecookies.wiremock.extensions.HttpCallbackHandler.HttpCallback;
import com.ninecookies.wiremock.extensions.SnsCallbackHandler.SnsCallback;
import com.ninecookies.wiremock.extensions.SqsCallbackHandler.SqsCallback;
import com.ninecookies.wiremock.extensions.api.Authentication;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.api.Callbacks;
import com.ninecookies.wiremock.extensions.util.Objects;
import com.ninecookies.wiremock.extensions.util.Placeholders;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Implements the {@link PostServeAction} interface and provides the ability to specify callback invocations for request
 * mappings.
 * <p>
 * This class utilizes the {@link ScheduledExecutorService} and configures it to use a {@link ThreadFactory} that
 * produces daemon {@link Thread}s.
 *
 * @author M.Scheepers
 * @since 0.0.6
 * @see CallbackConfiguration
 */
public class CallbackSimulator extends PostServeAction {

    private static final Logger LOG = LoggerFactory.getLogger(CallbackSimulator.class);
    private static int instances = 0;
    private final long instance = ++instances;
    private final boolean messagingEnabled;

    private final ScheduledExecutorService executor;

    public CallbackSimulator() {
        CallbackConfiguration config = CallbackConfiguration.getInstance();
        int corePoolSize = config.getCorePoolSize();
        messagingEnabled = config.isMessagingEnabled();
        LOG.info("instance: {} - using SCHEDULED_THREAD_POOL_SIZE {} - RETRY_BACKOFF {} - MAX_RETRIES {}",
                instance, corePoolSize, config.getRetryBackoff(), config.getMaxRetries());
        executor = Executors.newScheduledThreadPool(corePoolSize, new DaemonThreadFactory());
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
            if (!Strings.isNullOrEmpty(callback.url)) {
                scheduleHttpCallback(servedJson, Objects.convert(callback, HttpCallback.class));
            } else if (!Strings.isNullOrEmpty(callback.queue)) {
                scheduleSqsCallback(servedJson, Objects.convert(callback, SqsCallback.class));
            } else if (!Strings.isNullOrEmpty(callback.topic)) {
                scheduleSnsCallback(servedJson, Objects.convert(callback, SnsCallback.class));
            } else {
                throw new IllegalStateException("Unknown callback type - "
                        + "either 'queue', 'topic' or 'url' must be specified.");
            }
        }
    }

    private void scheduleSnsCallback(DocumentContext servedJson, SnsCallback callback) {
        if (!messagingEnabled) {
            LOG.warn("instance {} - sns callbacks disabled - ignore task to: '{}' with delay '{}' and data '{}'",
                    instance, callback.topic, callback.delay, callback.data);
            return;
        }
        String topic = callback.topic;
        callback.topic = Placeholders.transformValue(servedJson, topic);
        callback.data = Placeholders.transformJson(servedJson, Json.write(callback.data));
        if ("null".equals(callback.topic)) {
            LOG.warn("instance {} - unresolvable SNS topic '{}' - ignore task to: '{}' with delay '{}' and data '{}'",
                    instance, topic, callback.topic, callback.delay, callback.data);
            return;
        }
        File callbackDefinition = persistCallback(callback);
        LOG.info("instance {} - scheduling callback task to: '{}' with delay '{}' and data '{}'",
                instance, callback.topic, callback.delay, callback.data);
        Runnable callbackHandler = SnsCallbackHandler.of(callbackDefinition);
        executor.schedule(callbackHandler, callback.delay, TimeUnit.MILLISECONDS);
    }

    private void scheduleSqsCallback(DocumentContext servedJson, SqsCallback callback) {
        if (!messagingEnabled) {
            LOG.warn("instance {} - sqs callbacks disabled - ignore task to: '{}' with delay '{}' and data '{}'",
                    instance, callback.queue, callback.delay, callback.data);
            return;
        }
        // normalize callback
        String queue = callback.queue;
        callback.queue = Placeholders.transformValue(servedJson, callback.queue);
        callback.data = Placeholders.transformJson(servedJson, Json.write(callback.data));
        // check for queue name String.valueOf((Object) null) as a result of transformValue()
        if ("null".equals(callback.queue)) {
            LOG.warn("instance {} - unresolvable SQS queue '{}' - ignore task to: '{}' with delay '{}' and data '{}'",
                    instance, queue, callback.queue, callback.delay, callback.data);
            return;
        }
        File callbackDefinition = persistCallback(callback);
        LOG.info("instance {} - scheduling callback task to: '{}' with delay '{}' and data '{}'",
                instance, callback.queue, callback.delay, callback.data);
        Runnable callbackHandler = SqsCallbackHandler.of(callbackDefinition);
        executor.schedule(callbackHandler, callback.delay, TimeUnit.MILLISECONDS);
    }

    private void scheduleHttpCallback(DocumentContext servedJson, HttpCallback callback) {
        HttpCallback normalizedCallback = normalizeHttpCallback(servedJson, callback);
        File callbackDefinition = persistCallback(normalizedCallback);
        LOG.info("instance {} - scheduling callback task to: '{}' with delay '{}' and data '{}'",
                instance, callback.url, callback.delay, callback.data);
        Runnable callbackHandler = HttpCallbackHandler.of(executor, callbackDefinition);
        executor.schedule(callbackHandler, callback.delay, TimeUnit.MILLISECONDS);
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
    private HttpCallback normalizeHttpCallback(DocumentContext servedJson, HttpCallback callback) {
        LOG.debug("url: {} data: {}", callback.url, Objects.describe(callback.data));
        callback.data = Placeholders.transformJson(servedJson, Json.write(callback.data));
        callback.url = Placeholders.transformUrl(servedJson, callback.url);
        if (callback.authentication != null) {
            callback.authentication = Authentication.of(
                    Placeholders.transformValue(callback.authentication.getUsername()),
                    Placeholders.transformValue(callback.authentication.getPassword()));
        }
        if (callback.traceId == null) {
            callback.traceId = UUID.randomUUID().toString().replace("-", "");
        }
        LOG.debug("final url: {} data: {}", callback.url, callback.data);
        return callback;
    }

    /**
     * Persists the specified {@code callback} as temporary file in the file system to be picked up by the
     * scheduled {@link HttpCallbackHandler} when due to reduce the memory footprint during callback handling.
     *
     * @param callback the {@link Callback} to persist.
     * @return the temporary {@link File} containing the normalized callback definition.
     */
    private File persistCallback(Object callback) {
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
}
