package com.ninecookies.wiremock.extensions;

import java.util.List;
import java.util.Map;
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
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.api.Callbacks;
import com.ninecookies.wiremock.extensions.util.Lists;
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

    private final ScheduledExecutorService executor;
    private final List<CallbackHandlerProvider> providers;

    public CallbackSimulator() {
        CallbackConfiguration config = CallbackConfiguration.getInstance();
        int corePoolSize = config.getCorePoolSize();
        LOG.info("instance: {} - using SCHEDULED_THREAD_POOL_SIZE {} - RETRY_BACKOFF {} - MAX_RETRIES {}",
                instance, corePoolSize, config.getRetryBackoff(), config.getMaxRetries());
        executor = Executors.newScheduledThreadPool(corePoolSize, new DaemonThreadFactory());
        providers = Lists.listOf(
                new HttpCallbackHandlerProvider(executor),
                new SnsCallbackHandlerProvider(executor),
                new SqsCallbackHandlerProvider(executor));
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

        Map<String, Object> placeholders = Placeholders.parsePlaceholders(Json.write(parameters), servedJson);

        Callbacks callbacks = parameters.as(Callbacks.class);

        for (Callback callback : callbacks.callbacks) {
            if (Strings.isNullOrEmpty(callback.url)
                    && Strings.isNullOrEmpty(callback.queue)
                    && Strings.isNullOrEmpty(callback.topic)) {
                throw new IllegalStateException("Unknown callback type - "
                        + "either 'queue' (SQS), 'topic' (SNS) or 'url' (HTTP) must be specified.");
            }

            for (CallbackHandlerProvider provider : providers) {
                if (!provider.supports(callback)) {
                    continue;
                }
                Runnable handler = provider.get(callback, placeholders);
                if (handler != null) {
                    LOG.info("instance {} - scheduling callback task to: '{}' with delay '{}' and data '{}'",
                            instance, callback.url, callback.delay, callback.data);
                    executor.schedule(handler, callback.delay, TimeUnit.MILLISECONDS);
                }
            }
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
