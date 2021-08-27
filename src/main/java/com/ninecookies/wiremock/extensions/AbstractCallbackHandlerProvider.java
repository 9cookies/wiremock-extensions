package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.AbstractCallbackHandler.AbstractCallbackDefinition;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.util.Placeholders;

/**
 * Provides common methods to simplify and unify the creation of callback definitions and their related handlers.
 *
 * @author M.Scheepers
 * @since 0.3.1
 *
 * @param <T> the concrete callback type to provide a callback handler for.
 */
public abstract class AbstractCallbackHandlerProvider<T extends AbstractCallbackDefinition>
        implements CallbackHandlerProvider {

    private final boolean messagingEnbabled;
    private final Class<T> callbackType;
    private final Logger log;
    private final ScheduledExecutorService executor;

    /**
     * Initialize a new instance of the {@link AbstractCallbackHandlerProvider} with the specified arguments.
     *
     * @param callbackType the {@link Class} of type {@code <T>} that extends {@link AbstractCallbackDefinition}.
     * @param executor the {@link ScheduledExecutorService} that runs the created handler.
     */
    protected AbstractCallbackHandlerProvider(Class<T> callbackType, ScheduledExecutorService executor) {
        log = LoggerFactory.getLogger(getClass());
        messagingEnbabled = CallbackConfiguration.getInstance().isMessagingEnabled();
        this.callbackType = callbackType;
        this.executor = executor;
    }

    /**
     * Gets the logger to be use by extending classes.
     *
     * @return the {@link Logger} instance.
     */
    protected Logger getLog() {
        return log;
    }

    /**
     * Gets the scheduled executor service that executes the provided callback handler.
     *
     * @return the {@link ScheduledExecutorService} instance.
     */
    protected ScheduledExecutorService getExecutorService() {
        return executor;
    }

    /**
     * Indicates whether SNS/SQS messaging is enabled by configuration.
     *
     * @return {@code true} if SNS/SQS messaging is enabled; otherwise {@code false}.
     */
    protected boolean isMessagingEnabled() {
        return messagingEnbabled;
    }

    /**
     * Creates a new instance of a callback definition of type {@code <T>} and populates the delay and transformed JSON
     * data properties according to the provided public API {@link Callback} model information.
     *
     * @param callback the public API {@link Callback} model information.
     * @param placeholders the context for placeholder substitution.
     * @return a new instance of type {@code <T>}.
     * @throws ReflectiveOperationException - rethrown but should not occur.
     */
    protected T convert(Callback callback, Map<String, Object> placeholders) throws ReflectiveOperationException {
        T result = callbackType.newInstance();
        result.delay = callback.delay;
        result.data = Placeholders.transformJson(placeholders, Json.write(callback.data));
        // TODO: simplify callback definition
        // result.target = Placeholders.transformValue(placeholders, getTarget(callback), isUrlTarget());
        return result;
    }
    // TODO: simplify callback definition
    // /**
    // * Gets the callback target, either one of HTTP URL, SNS topic or SQS queue.
    // *
    // * @return the callback target.
    // */
    // protected abstract String getTarget(Callback callback);
    //
    // protected abstract boolean isUrlTarget();

    /**
     * Persists the specified {@code callbackDefinition} as temporary file in the file system to be picked up by the
     * scheduled {@link Runnable} callback handler when due to reduce the memory footprint during callback handling.
     *
     * @param callbackDefinition the {@link Callback} to persist.
     * @return the temporary {@link File} containing the normalized callback definition.
     */
    protected File persistCallback(T callbackDefinition) {
        try {
            File result = File.createTempFile("callback-json-", ".tmp");
            getLog().debug("callback-json file: {}", result);
            String jsonContent = Json.write(callbackDefinition);
            getLog().debug("callback-json content: {}", jsonContent);
            Files.write(result.toPath(), jsonContent.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("unable to persist callback data", e);
        }
    }
}
