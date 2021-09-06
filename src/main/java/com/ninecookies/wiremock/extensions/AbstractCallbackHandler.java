package com.ninecookies.wiremock.extensions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.Json;

/**
 * Represents the base class for callback handlers.
 *
 * @author M.Scheepers
 * @since 0.3.0
 */
public abstract class AbstractCallbackHandler<T extends CallbackDefinition> implements Runnable {

    /**
     * To be thrown by {@link AbstractCallbackHandler#handle(CallbackDefinition)} method if callback must not be
     * retried.
     */
    public static class CallbackException extends Exception {
        private static final long serialVersionUID = -1287146336431043241L;

        protected CallbackException(String message) {
            super(message);
        }

        public CallbackException(String message, Throwable cause) {
            super(message, cause);
        }

        public CallbackException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * To be thrown by {@link AbstractCallbackHandler#handle(CallbackDefinition)} method if callback should be
     * retried.
     */
    public static class RetryCallbackException extends CallbackException {
        private static final long serialVersionUID = -4687718028554157142L;

        public RetryCallbackException(String message) {
            super(message);
        }

        public RetryCallbackException(String message, Throwable cause) {
            super(message, cause);
        }

        public RetryCallbackException(Throwable cause) {
            super(cause);
        }
    }

    private final Class<T> type;
    private final File callbackFile;
    private final ScheduledExecutorService executor;
    private final Logger log;
    private final int maxRetries;
    private final int retryBackoff;
    private int invocation;

    @Override
    public final void run() {
        boolean cleanup = true;
        try {
            handle(readCallback());
        } catch (CallbackException e) {
            if (e instanceof RetryCallbackException) {
                cleanup = rescheduleIfApplicable();
            }

            if (cleanup) {
                String retryInfo = "";
                if (invocation > 1) {
                    retryInfo = " after " + invocation + " attempts";
                }
                log.warn("unable to publish '{}' message{}", type.getSimpleName(), retryInfo, e);
            } else {
                log.info("publishing of {} will be retried", type.getSimpleName(), e);
            }
        } catch (Exception e) {
            log.error("error during callback handling", e);
        } finally {
            if (cleanup) {
                deleteCallback();
            }
        }
    }

    /**
     * Implements the concrete callback handling.
     *
     * @param callback the callback definition to handle.
     * @return {@code true} if handling was successful and the callback file should be cleaned up; otherwise
     *         {@code false}.
     */
    protected abstract void handle(T callback) throws CallbackException;

    /**
     * Gets the logger to be use by extending classes.
     *
     * @return the {@link Logger} instance.
     */
    protected Logger getLog() {
        return log;
    }

    /**
     * Initialize a new instance of the {@link AbstractCallbackHandler} with the specified arguments.
     *
     * @param executor the {@link ScheduledExecutorService} to reschedule the callback handler.
     * @param callbackFile the {@link File} reference to the callback definition.
     * @param type the {@link Class} type of the callback.
     */
    protected AbstractCallbackHandler(ScheduledExecutorService executor, File callbackFile, Class<T> type) {
        this.executor = executor;
        this.type = type;
        this.callbackFile = callbackFile;
        this.log = LoggerFactory.getLogger(getClass());
        CallbackConfiguration config = CallbackConfiguration.getInstance();
        this.maxRetries = config.getMaxRetries();
        this.retryBackoff = config.getRetryBackoff();
    }

    private boolean rescheduleIfApplicable() {
        invocation++;
        if (executor != null && invocation <= maxRetries) {
            executor.schedule(this, retryBackoff * invocation, TimeUnit.MILLISECONDS);
            return false;
        }
        return true;
    }

    private T readCallback() {
        try {
            String jsonContent = new String(Files.readAllBytes(callbackFile.toPath()), StandardCharsets.UTF_8);
            return Json.read(jsonContent, type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read callback content from file system", e);
        }
    }

    private void deleteCallback() {
        try {
            Files.deleteIfExists(callbackFile.toPath());
        } catch (IOException e) {
            log.error("unable to delete callback definition file", e);
        }
    }
}
