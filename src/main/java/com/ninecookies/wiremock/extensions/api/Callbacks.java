package com.ninecookies.wiremock.extensions.api;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents one or more callback definitions for the callback simulator post serve action to conveniently compose
 * callback definitions programmatically for Wiremock stubbing.
 *
 * @author M.Scheepers
 * @since 0.0.6
 * @see Callback
 * @see Authentication
 */
public class Callbacks {

    public Set<Callback> callbacks = new LinkedHashSet<>();

    /**
     * Creates callback definition for one SQS callback to simulate.
     *
     * @param delay the period of time in milliseconds to wait before the callback data published.
     * @param queue the destination queue name to send the message to.
     * @param message an arbitrary JSON object representing the message to send.
     * @return a new {@link Callback} instance ready to use.
     */
    public static Callbacks ofQueueMessage(int delay, String queue, Object message) {
        return Callbacks.of(Callback.ofQueueMessage(delay, queue, message));
    }

    /**
     * Creates a callback definition for one callback to simulate.
     *
     * @param delay the period of time in milliseconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callbacks POST data.
     * @param data an arbitrary JSON object representing the callback data to POST.
     * @return a new {@link Callbacks} instance ready to use.
     */
    public static Callbacks of(int delay, String url, Object data) {
        return Callbacks.of(Callback.of(delay, url, data));
    }

    /**
     * Creates a callback definition for one callback with Basic authentication to simulate.
     *
     * @param delay the period of time in milliseconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callbacks POST data.
     * @param username the user name for the callback authentication.
     * @param password the password for the callback authentication.
     * @param data an arbitrary JSON object representing the callback data to POST.
     * @return a new {@link Callbacks} instance ready to use.
     */
    public static Callbacks of(int delay, String url, String username, String password, Object data) {
        return Callbacks.of(Callback.of(delay, url, username, password, data));
    }

    /**
     * Creates a callback definition for one callback with Basic authentication to simulate.
     *
     * @param delay the period of time in milliseconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callbacks POST data.
     * @param username the user name for the callback authentication.
     * @param password the password for the callback authentication.
     * @param data an arbitrary JSON object representing the callback data to POST.
     * @param traceId the trace / request identifier to use for the callback.
     * @return a new {@link Callbacks} instance ready to use.
     */
    public static Callbacks of(int delay, String url, String username, String password, String traceId, Object data) {
        return Callbacks.of(Callback.of(delay, url, username, password, traceId, data));
    }

    /**
     * Creates a callback definition for the specified {@code callbacks}.
     *
     * @param callbacks a list of {@link Callback} definitions.
     * @return a new {@link Callbacks} instance ready to use.
     */
    public static Callbacks of(Callback... callbacks) {
        Callbacks result = new Callbacks();
        result.callbacks.addAll(Arrays.asList(callbacks));
        return result;
    }
}
