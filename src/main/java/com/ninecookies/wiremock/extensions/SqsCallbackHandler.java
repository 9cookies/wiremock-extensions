package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import javax.jms.JMSException;

import com.github.tomakehurst.wiremock.common.Json;

/**
 * Extends the {@link AbstractCallbackHandler} and uses {@link SqsMessagePublisher} to publish an
 * SQS queue message according to the callback definition.
 */
public class SqsCallbackHandler extends AbstractCallbackHandler<CallbackDefinition> {

    private SqsCallbackHandler(ScheduledExecutorService executor, File callbackFile) {
        super(executor, callbackFile, CallbackDefinition.class);
    }

    public static Runnable of(ScheduledExecutorService executor, File callbackFile) {
        return new SqsCallbackHandler(executor, callbackFile);
    }

    @Override
    public void handle(CallbackDefinition callback) throws CallbackException {
        try (SqsMessagePublisher publisher = new SqsMessagePublisher()) {
            String message;
            if (callback.data instanceof String) {
                message = (String) callback.data;
            } else {
                message = Json.write(callback.data);
            }
            try {
                publisher.sendMessage(callback.target, message);
                getLog().info("message published to '{}'", callback.target);
            } catch (JMSException e) {
                throw new RetryCallbackException(e);
            }
        } catch (Exception e) {
            throw new CallbackException(e);
        }
    }
}
