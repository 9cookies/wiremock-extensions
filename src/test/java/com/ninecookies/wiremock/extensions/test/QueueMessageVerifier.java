package com.ninecookies.wiremock.extensions.test;

import static org.testng.Assert.fail;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueMessageVerifier implements MessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(QueueMessageVerifier.class);

    private EventMonitor waitingThreadLock = null;
    private String messageText = null;

    @Override
    public void onMessage(Message message) {
        try {
            messageText = ((TextMessage) message).getText();
            if (waitingThreadLock != null) {
                waitingThreadLock.resume();
                waitingThreadLock = null;
            }
        } catch (Exception e) {
            LOG.error("unable to handle message", e);
        }
    }

    public void reset() {
        messageText = null;
    }

    public String waitForMessage() {
        if (messageText == null) {
            waitingThreadLock = new EventMonitor();
            waitingThreadLock.suspend();
        }
        return messageText;
    }

    private static final class EventMonitor {
        private final long timeout = 10_000;
        private volatile boolean waiting = true;

        private synchronized void resume() {
            if (!waiting) {
                return;
            }
            waiting = false;
            notifyAll();
        }

        private synchronized void suspend() {
            while (waiting) {
                try {
                    wait(timeout);
                    if (waiting) {
                        waiting = false;
                        String message = "timeout " + timeout + " elapsed before a message was received";
                        fail(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error("thread interrupted whilst waiting for message", e);
                }
            }
        }
    }
}
