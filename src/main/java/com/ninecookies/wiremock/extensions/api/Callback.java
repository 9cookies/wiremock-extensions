package com.ninecookies.wiremock.extensions.api;

import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Represents a single callback definition for the callback simulator post serve action to conveniently compose callback
 * definitions programmatically for WireMock stubbing.
 *
 * @author M.Scheepers
 * @since 0.0.1
 * @see Callbacks
 */
public class Callback {

    /**
     * The period of time in milliseconds to wait before the callback {@link #data} is POSTed to the {@link #url}.
     */
    public long delay;
    /**
     * The destination URL to POST the {@link #data} to after {@link #delay} has elapsed.
     */
    public String url;

    /**
     * The authentication to use for the callback.
     */
    public Authentication authentication;

    /**
     * The object representing arbitrary callback data.
     */
    public Object data;

    /**
     * Creates a new instance for a {@link Callback} definition.
     *
     * @param delay the period of time in milliseconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callback's POST data.
     * @param data the callback data to POST.
     * @return a new {@link Callback} instance ready to use.
     */
    public static Callback of(int delay, String url, Object data) {
        return of(delay, url, null, null, data);
    }

    public static Callback of(int delay, String url, String username, String password, Object data) {
        Callback result = new Callback();
        result.delay = delay;
        result.url = url;
        result.data = data;
        if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
            result.authentication = Authentication.of(username, password);
        }
        return result;
    }
}
