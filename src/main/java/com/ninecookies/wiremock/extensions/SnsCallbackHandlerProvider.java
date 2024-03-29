package com.ninecookies.wiremock.extensions;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.core.Admin;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.util.Placeholders;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Implements the {@link CallbackHandlerProvider} for the {@link SnsCallbackHandler}.
 *
 * @author M.Scheepers
 * @since 0.3.1
 */
public class SnsCallbackHandlerProvider extends AbstractCallbackHandlerProvider {

    /**
     * Initialize a new instance of the {@link SnsCallbackHandlerProvider} with the specified arguments.
     *
     * @param executor the {@link ScheduledExecutorService} that runs the created handler.
     */
    public SnsCallbackHandlerProvider(ScheduledExecutorService executor) {
        super(SnsCallbackHandler::of, executor);
    }

    @Override
    public boolean supports(Callback callback) {
        // if the callback doesn't configure the topic it's not an SNS callback
        if (Strings.isNullOrEmpty(callback.topic)) {
            return false;
        }
        if (!isMessagingEnabled()) {
            getLog().warn("sns callbacks disabled - ignore task to: '{}' with delay '{}' and data '{}'",
                    callback.topic, callback.delay, callback.data);
        }
        return !Strings.isNullOrEmpty(callback.topic) && isMessagingEnabled();
    }

    @Override
    protected CallbackDefinition convert(Callback callback, Map<String, Object> placeholders, Admin admin) {
        CallbackDefinition callbackDefinition = new CallbackDefinition();
        callbackDefinition.target = Placeholders.transformValue(placeholders, callback.topic, false);
        callbackDefinition.delay = callback.delay;
        callbackDefinition.data = Placeholders.transformJson(placeholders, Json.write(callback.data));
        return callbackDefinition;
    }
}
