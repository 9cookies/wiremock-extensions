package com.ninecookies.wiremock.extensions.api;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents one or more callback definitions for the callback simulator post serve action to conveniently compose
 * callback definitions programmatically for Wiremock stubbing.
 *
 * @author M.Scheepers
 * @since 0.0.1
 * @see Callback
 */
public class Callbacks {

    public Set<Callback> callbacks = new LinkedHashSet<>();

    /**
     * Creates a callback definition for one callback to simulate.
     *
     * @param delay the period of time in seconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callbacks POST data.
     * @param data the callback data to POST.
     * @return a new {@link Callbacks} instance ready to use.
     */
    public static Callbacks of(int delay, String url, Object data) {
        return Callbacks.of(Callback.of(delay, url, data));
    }

    public static Callbacks of(int delay, String url, String username, String password, Object data) {
        return Callbacks.of(Callback.of(delay, url, username, password, data));
    }

    /**
     * Creates a callback definition for the specified {@code callbacks}.
     *
     * @param callbacks a list of callback definitions.
     * @return a new {@link Callbacks} instance ready to use.
     */
    public static Callbacks of(Callback... callbacks) {
        Callbacks result = new Callbacks();
        result.callbacks.addAll(Arrays.asList(callbacks));
        return result;
    }
}
