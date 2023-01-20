package com.ninecookies.wiremock.extensions;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.core.Admin;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.util.Placeholders;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Implements the {@link CallbackHandlerProvider} for the {@link SqsCallbackHandler}.
 *
 * @author michael.scheepers
 * @since 0.0.1
 *
 */
public class SqsCallbackHandlerProvider extends AbstractCallbackHandlerProvider {

    /**
     * Initialize a new instance of the {@link SqsCallbackHandlerProvider} with the specified arguments.
     *
     * @param executor the {@link ScheduledExecutorService} that runs the created handler.
     */
    public SqsCallbackHandlerProvider(ScheduledExecutorService executor) {
        super(SqsCallbackHandler::of, executor);
    }

    @Override
    public boolean supports(Callback callback) {
        // if the callback doesn't configure the the queue it's not an SQS callback
        if (Strings.isNullOrEmpty(callback.queue)) {
            return false;
        }
        if (!isMessagingEnabled()) {
            getLog().warn("sqs callbacks disabled - ignore task to: '{}' with delay '{}' and data '{}'",
                    callback.queue, callback.delay, callback.data);
        }
        return isMessagingEnabled();
    }

    @Override
    protected CallbackDefinition convert(Callback callback, Map<String, Object> placeholders, Admin admin) {
        CallbackDefinition callbackDefinition = new CallbackDefinition();
        callbackDefinition.target = Placeholders.transformValue(placeholders, callback.queue, false);
        callbackDefinition.delay = callback.delay;
        callbackDefinition.data = Placeholders.transformJson(placeholders, Json.write(callback.data));
        return callbackDefinition;
    }
}
