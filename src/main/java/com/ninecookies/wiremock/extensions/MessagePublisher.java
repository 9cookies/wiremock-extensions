package com.ninecookies.wiremock.extensions;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Implements {@link AutoCloseable} and provides the {@link #sendMessage(String, String)} method to publish messages to
 * an SQS queue.<br>
 * Instances can conveniently with the {@link #standard()} using the {@link AmazonSQSClient}.
 *
 * {@code
 *
 * }
 */
public class MessagePublisher implements AutoCloseable {

    /**
     * Implements the builder patter to create {@link MessagePublisher} instances ready to be used for SQS messaging.
     */
    public static final class SqsMessagePublisherBuilder {
        // only used during testing -
        private String endpoint = System.getenv("MESSAGING_SQS_ENDPOINT");
        private String region = System.getenv("AWS_REGION");
        private final SQSConnectionFactory connectionFactory;

        private SqsMessagePublisherBuilder() {
            AmazonSQSClientBuilder builder = AmazonSQSClientBuilder.standard()
                    .withCredentials(new DefaultAWSCredentialsProviderChain());
            if (Strings.isNullOrEmpty(endpoint)) {
                LOG.debug("amazonSQS with region '{}'", region);
                builder.withRegion(region);
            } else {
                LOG.warn("amazonSQS with region '{}' and endpoint '{}'", region, endpoint);
                builder.setEndpointConfiguration(new EndpointConfiguration(endpoint, region));
            }
            connectionFactory = new SQSConnectionFactory(
                    new ProviderConfiguration(),
                    builder.build());
        }

        /**
         * Creates a new SQS message publisher.
         *
         * @return a new {@link MessagePublisher} instance ready to be used.
         * @throws JMSException if no connection could be created.
         */
        public MessagePublisher build() throws JMSException {
            return new MessagePublisher(connectionFactory.createConnection());
        }
    }

    /**
     * Creates an SQS message publisher builder instance with standard configuration.
     *
     * @return an {@link SqsMessagePublisherBuilder}.
     */
    public static SqsMessagePublisherBuilder standard() {
        if (builder == null) {
            Object lock = new Object();
            synchronized (lock) {
                if (builder == null) {
                    builder = new SqsMessagePublisherBuilder();
                }
            }
        }
        return builder;
    }

    private static final Logger LOG = LoggerFactory.getLogger(MessagePublisher.class);
    private static SqsMessagePublisherBuilder builder;

    private final Connection connection;
    private Session session;

    private MessagePublisher(Connection connection) {
        this.connection = connection;
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
