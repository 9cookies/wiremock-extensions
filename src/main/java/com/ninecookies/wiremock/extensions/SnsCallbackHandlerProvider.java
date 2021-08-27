package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.SnsCallbackHandler.SnsCallback;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.util.Objects;
import com.ninecookies.wiremock.extensions.util.Placeholders;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Implements the {@link CallbackHandlerProvider} for the {@link SnsCallbackHandler}.
 *
 * @author M.Scheepers
 * @since 0.3.1
 */
public class SnsCallbackHandlerProvider extends AbstractCallbackHandlerProvider<SnsCallback> {

    /**
     * Initialize a new instance of the {@link SnsCallbackHandlerProvider} with the specified arguments.
     *
     * @param executor the {@link ScheduledExecutorService} that runs the created handler.
     */
    public SnsCallbackHandlerProvider(ScheduledExecutorService executor) {
        super(SnsCallback.class, executor);
    }

    @Override
    public boolean supports(Callback callback) {
        if (!isMessagingEnabled()) {
            getLog().warn("sns callbacks disabled - ignore task to: '{}' with delay '{}' and data '{}'",
                    callback.topic, callback.delay, callback.data);
        }
        return !Strings.isNullOrEmpty(callback.topic) && isMessagingEnabled();
    }

    @Override
    public Runnable get(Callback callback, Map<String, Object> placeholders) {
        SnsCallback data = Objects.convert(callback, SnsCallback.class);
        String topic = data.topic;
        data.topic = Placeholders.transformValue(placeholders, topic, false);
        if ("null".equals(data.topic)) {
            getLog().warn("unresolvable SNS topic '{}' - ignore task to: '{}' with delay '{}' and data '{}'",
                    topic, data.topic, data.delay, data.data);
            return null;
        }
        data.data = Placeholders.transformJson(placeholders, Json.write(data.data));
        File callbackDefinition = persistCallback(data);
        return SnsCallbackHandler.of(getExecutorService(), callbackDefinition);
    }
}
