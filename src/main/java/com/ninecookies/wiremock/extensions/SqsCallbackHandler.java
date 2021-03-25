package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import com.github.tomakehurst.wiremock.common.Json;
import com.ninecookies.wiremock.extensions.SqsCallbackHandler.SqsCallback;

/**
 * Extends the {@link AbstractCallbackHandler} and uses {@link SqsMessagePublisher} to publish an
 * SQS queue message according to the callback definition.
 */
public class SqsCallbackHandler extends AbstractCallbackHandler<SqsCallback> {

    public static class SqsCallback extends AbstractCallbackHandler.AbstractCallback {
        /**
         * The destination queue to send the data to after delay has elapsed.
         */
        public String queue;
    }

    private SqsCallbackHandler(ScheduledExecutorService executor, File callbackFile) {
        super(executor, callbackFile, SqsCallback.class);
    }

    public static Runnable of(ScheduledExecutorService executor, File callbackFile) {
        return new SqsCallbackHandler(executor, callbackFile);
    }

    @Override
    public void handle(SqsCallback callback) throws CallbackException {
        try (SqsMessagePublisher publisher = new SqsMessagePublisher()) {
            String message;
            if (callback.data instanceof String) {
                message = (String) callback.data;
            } else {
                message = Json.write(callback.data);
            }
            publisher.sendMessage(callback.queue, message);
            getLog().info("message published to '{}'", callback.queue);
        } catch (Exception e) {
            throw new CallbackException(e);
        }
    }
}
