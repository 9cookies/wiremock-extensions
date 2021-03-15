package com.ninecookies.wiremock.extensions;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements {@link AutoCloseable} and provides the {@link #sendMessage(String, String)} method to publish messages to
 * an SQS queue.
 * <p>
 * Example utilizing the convenient {@link AutoCloseable} interface.
 *
 * <pre>
 * <code>
 * try (MessagePublisher publisher = new MessagePublisher()) {
 *     String messageJson = "JSON message string";
 *     String queueName = "queue-name";
 *     publisher.sendMessage(queueName, messageJson)
 *     LOG.info("message published to '{}'", queueName);
 * } catch (Exception e) {
 *     LOG.error("unable to publish SQS message", e);
 * }
 * </code>
 * </pre>
 */
public class MessagePublisher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MessagePublisher.class);

    private final Connection connection;
    private Session session;

    /**
     * Initialize a new instance of the {@link MessagePublisher} with the specified arguments.
     *
     * @throws IllegalStateException - if AWS SQS messaging is disabled due to lacking configuration.
     * @throws JMSException - if a connection couldn't be established.
     */
    public MessagePublisher() throws JMSException {
        CallbackConfiguration configuration = CallbackConfiguration.getInstance();
        if (!configuration.isSqsMessagingEnabled()) {
            throw new IllegalStateException("AWS SQS messaging is disabled due to lacking configuration.");
        }
        this.connection = configuration.createConnection();
    }

    /**
     * Publishes the specified {@code messageJson} to the specified {@code queueName}.
     *
     * @param queueName the name of the queue to publish the message to.
     * @param messageJson the JSON message string to publish.
     * @throws JMSException if publishing fails
     */
    public void sendMessage(String queueName, String messageJson) throws JMSException {
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(queueName);

        MessageProducer producer = session.createProducer(queue);
        TextMessage message = session.createTextMessage(messageJson);
        producer.send(message);
        LOG.debug("message '{}' published to '{}'", message, queue);
    }

    @Override
    public void close() throws Exception {
        if (session != null) {
            try {
                session.close();
                LOG.debug("session closed");
            } catch (JMSException e) {
                LOG.error("unable to close JMS session", e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
                LOG.debug("connection closed");
            } catch (JMSException e) {
                LOG.error("unable to close JMS connection", e);
            }
        }
    }
}
