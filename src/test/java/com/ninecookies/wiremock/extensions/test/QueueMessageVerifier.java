package com.ninecookies.wiremock.extensions.test;

import static org.testng.Assert.fail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.common.Json;

public class QueueMessageVerifier implements MessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(QueueMessageVerifier.class);

    private Map<String, EventMonitor> waitingThreadLocks = new ConcurrentHashMap<>();
    private Map<String, String> capturedMessages = new ConcurrentHashMap<>();

    @Override
    public void onMessage(Message message) {
        try {
            String messageText = ((TextMessage) message).getText();
            JsonNode node = Json.node(messageText);
            String messageId = node.get("messageId").textValue();

            capturedMessages.put(messageId, messageText);
            if (waitingThreadLocks.containsKey(messageId)) {
                EventMonitor lock = waitingThreadLocks.remove(messageId);
                lock.resume();
            }
        } catch (Exception e) {
            LOG.error("unable to handle message", e);
        }
    }

    public void reset() {
        capturedMessages.clear();
    }

    public String waitForMessage(String messageId) {
        if (!capturedMessages.containsKey(messageId)) {
            EventMonitor lock = waitingThreadLocks.computeIfAbsent(messageId, key -> new EventMonitor(key));
            lock.suspend();
        }
        return capturedMessages.get(messageId);
    }

    private static final class EventMonitor {
        private final String key;
        private final long timeout = 10_000;
        private volatile boolean waiting = true;

        private EventMonitor(String key) {
            this.key = key;
        }

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
                        String message = "timeout " + timeout + " elapsed before '" + key + "' was received";
                        fail(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error("thread interrupted whilst waiting for '{}'", key, e);
                }
            }
        }
    }
}
