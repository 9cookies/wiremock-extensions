package com.ninecookies.wiremock.extensions;

/**
 * Represents the base class for all callback definitions.
 */
public class CallbackDefinition {
    /**
     * The period of time in milliseconds to wait before the callback {@link #data} is sent.
     */
    public long delay;
    /**
     * The object representing arbitrary callback data.
     */
    public Object data;
    /**
     * The callback target, either one of HTTP URL, SNS topic or SQS queue.
     */
    public String target;
}
