package com.ninecookies.wiremock.extensions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.core.Admin;
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
public class HttpCallbackHandlerProvider extends AbstractCallbackHandlerProvider {

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
    protected HttpCallbackDefinition convert(Callback callback, Map<String, Object> placeholders, Admin admin) {
        HttpCallbackDefinition callbackDefinition = new HttpCallbackDefinition();
        callbackDefinition.localWiremockPort = admin.getOptions().portNumber();
        callbackDefinition.skipResultReport = admin.getOptions().requestJournalDisabled();
        callbackDefinition.expectedHttpStatus = callback.expectedHttpStatus;
        callbackDefinition.delay = callback.delay;
        callbackDefinition.target = Placeholders.transformValue(placeholders, callback.url, true);
        callbackDefinition.data = Placeholders.transformJson(placeholders, Json.write(callback.data));
        callbackDefinition.authentication = transformAuthentication(callback.authentication);
        callbackDefinition.traceId = (callback.traceId != null) ? callback.traceId
                : UUID.randomUUID().toString().replace("-", "");
        return callbackDefinition;
    }

    private Authentication transformAuthentication(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        switch (authentication.getType()) {
            case BASIC:
                return Authentication.of(
                        Placeholders.transformValue(authentication.getUsername()),
                        Placeholders.transformValue(authentication.getPassword()));
            case BEARER:
                return Authentication.of(
                        Placeholders.transformValue(authentication.getToken()));
            default:
                throw new IllegalStateException("invalid authentication type: " + authentication.getType());
        }
    }
}
