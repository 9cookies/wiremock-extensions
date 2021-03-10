package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.Json;

/**
 * Implements {@link Runnable} and uses {@link MessagePublisher} to publish an SQS queue message according to the
 * callback definition.
 */
public class SqsCallbackHandler implements Runnable {

    public static class SqsCallback {
        /**
         * The period of time in milliseconds to wait before the callback {@link #data} is send to the {@link #queue}.
         */
        public long delay;
        /**
         * The destination queue to send the {@link #data} to after {@link #delay} has elapsed.
         */
        public String queue;
        /**
         * The object representing arbitrary callback data.
         */
        public Object data;
    }

    private static final Logger LOG = LoggerFactory.getLogger(SqsCallbackHandler.class);

    private File callbackFile;

    public static Runnable of(File callbackFile) {
        SqsCallbackHandler result = new SqsCallbackHandler();
        result.callbackFile = callbackFile;
        return result;
    }

    @Override
    public void run() {
        try (MessagePublisher publisher = MessagePublisher.standard().build()) {
            SqsCallback callback = readCallback();

            String message;
            if (callback.data instanceof String) {
                message = (String) callback.data;
            } else {
                message = Json.write(callback.data);
            }
            publisher.sendMessage(callback.queue, message);
            LOG.info("message published to '{}'", callback.queue);
        } catch (Exception e) {
            LOG.error("unable to publish sqs message", e);
        } finally {
            try {
                Files.deleteIfExists(callbackFile.toPath());
            } catch (IOException e) {
                LOG.error("unable to delete callback definition file", e);
            }
        }
    }

    private SqsCallback readCallback() {
        try {
            String jsonContent = new String(Files.readAllBytes(callbackFile.toPath()), StandardCharsets.UTF_8);
            return Json.read(jsonContent, SqsCallback.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read callback content from file system", e);
        }
    }
}
