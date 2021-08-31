package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.SnsCallbackHandler.SnsCallback;

/**
 * Extends the {@link AbstractCallbackHandler} and uses {@link SnsMessagePublisher} to publish an
 * SNS topic message according to the callback definition.
 */
public class SnsCallbackHandler extends AbstractCallbackHandler<SnsCallback> {

    public static class SnsCallback extends AbstractCallbackHandler.AbstractCallbackDefinition {
        /**
         * The destination topic to send the data to after delay has elapsed.
         */
        public String topic;
    }

    private SnsCallbackHandler(ScheduledExecutorService executor, File callbackFile) {
        super(executor, callbackFile, SnsCallback.class);
    }

    private static SnsMessagePublisher publisher = new SnsMessagePublisher();

    public static Runnable of(ScheduledExecutorService executor, File callbackFile) {
        return new SnsCallbackHandler(executor, callbackFile);
    }

    @Override
    public void handle(SnsCallback callback) throws CallbackException {
        try {
            String messageJson;
            if (callback.data instanceof String) {
                messageJson = (String) callback.data;
            } else {
                messageJson = Json.write(callback.data);
            }
            publisher.sendMessage(callback.target, messageJson);
            getLog().info("message published to '{}'", callback.target);
        } catch (Exception e) {
            throw new RetryCallbackException(e);
        }
    }
}
