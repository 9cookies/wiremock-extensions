package com.ninecookies.wiremock.extensions;

import java.io.File;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.SnsCallbackHandler.SnsCallback;

/**
 * Extends the {@link AbstractCallbackHandler} and uses {@link SnsMessagePublisher} to publish an
 * SNS topic message according to the callback definition.
 */
public class SnsCallbackHandler extends AbstractCallbackHandler<SnsCallback> {

    public static class SnsCallback extends AbstractCallbackHandler.AbstractCallback {
        /**
         * The destination topic to send the data to after delay has elapsed.
         */
        public String topic;
    }

    private SnsCallbackHandler(File callbackFile) {
        super(callbackFile, SnsCallback.class);
    }

    private static SnsMessagePublisher publisher = new SnsMessagePublisher();

    public static Runnable of(File callbackFile) {
        return new SnsCallbackHandler(callbackFile);
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
            publisher.sendMessage(callback.topic, messageJson);
            getLog().info("message published to '{}'", callback.topic);
        } catch (Exception e) {
            throw new CallbackException(e);
        }
    }
}
