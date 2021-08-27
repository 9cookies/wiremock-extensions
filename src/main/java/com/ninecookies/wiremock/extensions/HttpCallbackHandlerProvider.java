package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import com.ninecookies.wiremock.extensions.HttpCallbackHandler.HttpCallback;
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
public class HttpCallbackHandlerProvider extends AbstractCallbackHandlerProvider<HttpCallback> {

    /**
     * Initialize a new instance of the {@link HttpCallbackHandlerProvider} with the specified arguments.
     *
     * @param executor the {@link ScheduledExecutorService} that runs the created handler.
     */
    public HttpCallbackHandlerProvider(ScheduledExecutorService executor) {
        super(HttpCallback.class, executor);
    }

    @Override
    public boolean supports(Callback callback) {
        return !Strings.isNullOrEmpty(callback.url);
    }

    @Override
    public Runnable get(Callback callback, Map<String, Object> placeholders) {
        try {
            HttpCallback data = convert(callback, placeholders);
            data.url = Placeholders.transformValue(placeholders, callback.url, true);
            if ("null".equals(data.url)) {
                getLog().warn("unresolvable callback URL '{}' - ignore task with delay '{}' and data '{}'",
                        callback.url, data.delay, data.data);
                return null;
            }

            if (callback.authentication != null) {
                data.authentication = Authentication.of(
                        Placeholders.transformValue(callback.authentication.getUsername()),
                        Placeholders.transformValue(callback.authentication.getPassword()));
            }
            data.traceId = (callback.traceId != null) ? callback.traceId
                    : UUID.randomUUID().toString().replace("-", "");

            File callbackDefinition = persistCallback(data);
            return HttpCallbackHandler.of(getExecutorService(), callbackDefinition);
        } catch (ReflectiveOperationException e) {
            getLog().error("unable to create HttpCallback instance for '{}'", callback, e);
            return null;
        }
    }
}
