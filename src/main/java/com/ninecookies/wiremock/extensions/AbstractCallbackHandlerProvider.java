package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.util.Objects;

/**
 * Provides common methods to simplify and unify the creation of callback definitions and their related handlers.
 *
 * @author M.Scheepers
 * @since 0.3.1
 *
 * @param <T> the concrete callback type to provide a callback handler for.
 */
public abstract class AbstractCallbackHandlerProvider<T extends CallbackDefinition>
        implements CallbackHandlerProvider {

    private final boolean messagingEnbabled;
    private final Logger log;
    private final ScheduledExecutorService executor;
    private final BiFunction<ScheduledExecutorService, File, Runnable> handlerCreator;

    /**
     * Initialize a new instance of the {@link AbstractCallbackHandlerProvider} with the specified arguments.
     *
     * @param handlerCreator the method that creates a callback handler for a certain callback type.
     * @param executor the {@link ScheduledExecutorService} that runs the created handler.
     */
    protected AbstractCallbackHandlerProvider(BiFunction<ScheduledExecutorService, File, Runnable> handlerCreator,
            ScheduledExecutorService executor) {
        log = LoggerFactory.getLogger(getClass());
        messagingEnbabled = CallbackConfiguration.getInstance().isMessagingEnabled();
        this.executor = executor;
        this.handlerCreator = handlerCreator;
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
     * Indicates whether SNS/SQS messaging is enabled by configuration.
     *
     * @return {@code true} if SNS/SQS messaging is enabled; otherwise {@code false}.
     */
    protected boolean isMessagingEnabled() {
        return messagingEnbabled;
    }

    /**
     * Implementors have to return a {@link CallbackDefinition} implementation according to the provided
     * {@link Callback}.
     *
     * @param callback the public API {@link Callback} model information.
     * @param placeholders the context for placeholder substitution.
     * @return a concrete implementation of {@link CallbackDefinition}.
     */
    protected abstract T convert(Callback callback, Map<String, Object> placeholders);

    @Override
    public Runnable get(Callback callback, Map<String, Object> placeholders) {
        T callbackDefinition = convert(callback, placeholders);
        if ("null".equals(callbackDefinition.target)) {
            getLog().warn("unresolvable callback target '{}' - ignore {} task with delay '{}' and data '{}'",
                    Objects.coalesce(callback.url, Objects.coalesce(callback.queue, callback.topic)),
                    callbackDefinition.getClass().getSimpleName(), callbackDefinition.delay, callbackDefinition.data);
            return null;
        }
        File callbackDefinitionFile = persistCallback(callbackDefinition);
        return handlerCreator.apply(executor, callbackDefinitionFile);
    }

    /**
     * Persists the specified {@code callbackDefinition} as temporary file in the file system to be picked up by the
     * scheduled {@link Runnable} callback handler when due to reduce the memory footprint during callback handling.
     *
     * @param callbackDefinition the {@link Callback} to persist.
     * @return the temporary {@link File} containing the normalized callback definition.
     */
    private File persistCallback(T callbackDefinition) {
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
