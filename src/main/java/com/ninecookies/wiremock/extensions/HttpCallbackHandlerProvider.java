package com.ninecookies.wiremock.extensions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.HttpCallbackHandler.HttpCallbackDefinition;
import com.ninecookies.wiremock.extensions.api.Authentication;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.util.Placeholders;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Implements the {@link CallbackHandlerProvider} for the {@link HttpCallbackHandler}.
 *
 * @author M.Scheepers
 * @since 0.3.1
 */
public class HttpCallbackHandlerProvider extends AbstractCallbackHandlerProvider<HttpCallbackDefinition> {

    /**
     * Initialize a new instance of the {@link HttpCallbackHandlerProvider} with the specified arguments.
     *
     * @param executor the {@link ScheduledExecutorService} that runs the created handler.
     */
    public HttpCallbackHandlerProvider(ScheduledExecutorService executor) {
        super(HttpCallbackHandler::of, executor);
    }

    @Override
    public boolean supports(Callback callback) {
        return !Strings.isNullOrEmpty(callback.url);
    }

    @Override
    protected HttpCallbackDefinition convert(Callback callback, Map<String, Object> placeholders) {
        HttpCallbackDefinition callbackDefinition = new HttpCallbackDefinition();
        callbackDefinition.target = Placeholders.transformValue(placeholders, callback.url, true);
        callbackDefinition.delay = callback.delay;
        callbackDefinition.data = Placeholders.transformJson(placeholders, Json.write(callback.data));
        if (callback.authentication != null) {
            callbackDefinition.authentication = Authentication.of(
                    Placeholders.transformValue(callback.authentication.getUsername()),
                    Placeholders.transformValue(callback.authentication.getPassword()));
        }
        callbackDefinition.traceId = (callback.traceId != null) ? callback.traceId
                : UUID.randomUUID().toString().replace("-", "");
        return callbackDefinition;
    }
}
