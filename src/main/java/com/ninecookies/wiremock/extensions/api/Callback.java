package com.ninecookies.wiremock.extensions.api;

import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Represents a single callback definition for the callback simulator post serve action to conveniently compose callback
 * definitions programmatically for WireMock stubbing.
 *
 * @author M.Scheepers
 * @since 0.0.6
 * @see Callbacks
 * @see Authentication
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
     * The destination QUEUE to send the {@link #data} to after {@link #delay} has elapsed.
     */
    public String queue;
    /**
     * The destination TOPIC to send the {@link #data} to after {@link #delay} has elapsed.
     */
    public String topic;
    /**
     * The authentication to use for the callback.
     */
    public Authentication authentication;
    /**
     * The request id to use for the callback.
     */
    public String traceId;
    /**
     * The JSON object representing arbitrary callback data.
     */
    public Object data;
    /**
     * The expected HTTP response status for the callback.
     * If omitted all 2xx HTTP status results are considered successful.
     */
    public Integer expectedHttpStatus;

    /**
     * Create a new instance for an SQS message {@link Callback} definition.
     *
     * @param delay the period of time in milliseconds to wait before the callback data published.
     * @param queue the destination queue name to send the message to.
     * @param message an arbitrary JSON object representing the message to send.
     * @return a new {@link Callback} instance ready to use.
     */
    public static Callback ofQueueMessage(int delay, String queue, Object message) {
        Callback result = new Callback();
        result.delay = delay;
        result.queue = queue;
        result.data = message;
        return result;
    }

    /**
     * Create a new instance for an SNS message {@link Callback} definition.
     *
     * @param delay the period of time in milliseconds to wait before the callback data published.
     * @param topic the destination topic name to send the message to.
     * @param message an arbitrary JSON object representing the message to send.
     * @return a new {@link Callback} instance ready to use.
     */
    public static Callback ofTopicMessage(int delay, String topic, Object message) {
        Callback result = new Callback();
        result.delay = delay;
        result.topic = topic;
        result.data = message;
        return result;
    }

    /**
     * Creates a new instance for a {@link Callback} definition.
     *
     * @param delay the period of time in milliseconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callback's POST data.
     * @param data an arbitrary JSON object representing the callback data to POST.
     * @return a new {@link Callback} instance ready to use.
     */
    public static Callback of(int delay, String url, Object data) {
        return of(delay, url, null, null, data);
    }

    /**
     * Creates a new instance for a {@link Callback} definition with Basic authentication.
     *
     * @param delay the period of time in milliseconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callback's POST data.
     * @param username the user name for the callback authentication.
     * @param password the password for the callback authentication.
     * @param data an arbitrary JSON object representing the callback data to POST.
     * @return a new {@link Callback} instance ready to use.
     */
    public static Callback of(int delay, String url, String username, String password, Object data) {
        return of(delay, url, username, password, null, data);
    }

    /**
     * Creates a new instance for a {@link Callback} definition with Basic authentication.
     *
     * @param delay the period of time in milliseconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callback's POST data.
     * @param username the user name for the callback authentication.
     * @param password the password for the callback authentication.
     * @param traceId the trace / request identifier to use for the callback.
     * @param data an arbitrary JSON object representing the callback data to POST.
     * @return a new {@link Callback} instance ready to use.
     */
    public static Callback of(int delay, String url, String username, String password, String traceId, Object data) {
        return of(delay, url, username, password, traceId, data, null);
    }

    /**
     * Creates a new instance for a {@link Callback} definition with Basic authentication.
     *
     * @param delay the period of time in milliseconds to wait before the callback data is POSTed.
     * @param url the destination URL for the callback's POST data.
     * @param username the user name for the callback authentication.
     * @param password the password for the callback authentication.
     * @param traceId the trace / request identifier to use for the callback.
     * @param data an arbitrary JSON object representing the callback data to POST.
     * @param expectedHttpStatus the expected HTTP status code for the callback POST request (defaults to all 2xx).
     * @return a new {@link Callback} instance ready to use.
     */
    public static Callback of(int delay, String url, String username, String password, String traceId, Object data,
            Integer expectedHttpStatus) {
        Callback result = new Callback();
        result.delay = delay;
        result.url = url;
        result.data = data;
        result.traceId = traceId;
        result.expectedHttpStatus = expectedHttpStatus;
        if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
            result.authentication = Authentication.of(username, password);
        }
        return result;
    }
}
