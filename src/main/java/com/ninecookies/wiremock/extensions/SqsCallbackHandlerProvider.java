package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.SqsCallbackHandler.SqsCallback;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.util.Objects;
import com.ninecookies.wiremock.extensions.util.Placeholders;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Implements the {@link CallbackHandlerProvider} for the {@link SqsCallbackHandler}.
 *
 * @author michael.scheepers
 * @since 0.0.1
 *
 */
public class SqsCallbackHandlerProvider extends AbstractCallbackHandlerProvider<SqsCallback> {

    /**
     * Initialize a new instance of the {@link SqsCallbackHandlerProvider} with the specified arguments.
     *
     * @param executor the {@link ScheduledExecutorService} that runs the created handler.
     */
    public SqsCallbackHandlerProvider(ScheduledExecutorService executor) {
        super(SqsCallback.class, executor);
    }

    @Override
    public boolean supports(Callback callback) {
        if (!isMessagingEnabled()) {
            getLog().warn("sqs callbacks disabled - ignore task to: '{}' with delay '{}' and data '{}'",
                    callback.queue, callback.delay, callback.data);
        }
        return !Strings.isNullOrEmpty(callback.queue);
    }

    @Override
    public Runnable get(Callback callback, Map<String, Object> placeholders) {

        SqsCallback data = Objects.convert(callback, SqsCallback.class);
        // normalize callback
        String queue = data.queue;
        data.queue = Placeholders.transformValue(placeholders, data.queue, false);
        // check for queue name String.valueOf((Object) null) as a result of transformValue()
        if ("null".equals(data.queue)) {
            getLog().warn("unresolvable SQS queue '{}' - ignore task to: '{}' with delay '{}' and data '{}'",
                    queue, callback.queue, callback.delay, callback.data);
            return null;
        }
        data.data = Placeholders.transformJson(placeholders, Json.write(callback.data));
        File callbackDefinition = persistCallback(data);
        return SqsCallbackHandler.of(getExecutorService(), callbackDefinition);
    }
}
