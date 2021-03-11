package com.ninecookies.wiremock.extensions;

import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Implements the environment configuration for the callback processing.
 * <p>
 * Read configuration properties
 * <ul>
 * <li>{@code SCHEDULED_THREAD_POOL_SIZE} default 50
 * <li>{@code RETRY_BACKOFF} default 5_000
 * <li>{@code MAX_RETRIES} default 0 (means disabled)
 * <li>{@code AWS_REGION} the AWS region for SQS messaging (default empty means SQS messaging disabled).
 * <li>{@code AWS_SQS_ENDPOINT} the SQS endpoint to use for testing with localstack or elasticmq (default empty means
 * AWS SQS messaging is used).
 * </ul>
 *
 * @author M.Scheepers
 * @since 0.2.0
 */
public class CallbackConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(CallbackConfiguration.class);
    private static final int MIN_CORE_POOL_SIZE = 50;
    private static final int DEFAULT_CORE_POOL_SIZE = 50;
    private static final int DEFAULT_RETRY_BACKOFF = 5_000;
    private static final int DEFAULT_MAX_RETRIES = 0;

    private static CallbackConfiguration instance;

    private int corePoolSize;
    private int retryBackoff;
    private int maxRetries;
    private String region;
    private String endpoint;
    private AmazonSQSClientBuilder clientBuilder;
    private SQSConnectionFactory connectionFactory;

    private CallbackConfiguration() {
        corePoolSize = parseEnvironmentSetting("SCHEDULED_THREAD_POOL_SIZE", DEFAULT_CORE_POOL_SIZE);
        // ensure minimum core thread pool size
        if (corePoolSize < MIN_CORE_POOL_SIZE) {
            corePoolSize = MIN_CORE_POOL_SIZE;
        }
        retryBackoff = parseEnvironmentSetting("RETRY_BACKOFF", DEFAULT_RETRY_BACKOFF);
        maxRetries = parseEnvironmentSetting("MAX_RETRIES", DEFAULT_MAX_RETRIES);
        region = System.getenv("AWS_REGION");
        endpoint = System.getenv("AWS_SQS_ENDPOINT");

        if (!Strings.isNullOrEmpty(region)) {
            clientBuilder = AmazonSQSClientBuilder.standard()
                    .withCredentials(new DefaultAWSCredentialsProviderChain());
            if (Strings.isNullOrEmpty(endpoint)) {
                LOG.debug("amazonSQS with region '{}'", region);
                clientBuilder.withRegion(region);
            } else {
                LOG.warn("amazonSQS with region '{}' and endpoint '{}'", region, endpoint);
                clientBuilder.setEndpointConfiguration(new EndpointConfiguration(endpoint, region));
            }
        } else {
            LOG.info("AWS SQS messaging callbacks disabled");
            clientBuilder = null;
        }
    }

    private int parseEnvironmentSetting(String name, int defaultValue) {
        int result = defaultValue;
        try {
            String poolSizeEnv = System.getenv(name);
            if (poolSizeEnv != null) {
                result = Integer.parseInt(poolSizeEnv);
            }
        } catch (Exception e) {
            LOG.error("unable to read environment variable '{}'", name, e);
        }
        return result;
    }

    /**
     * Gets the corePoolSize.
     *
     * @return the corePoolSize.
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Gets the retryBackoff.
     *
     * @return the retryBackoff.
     */
    public int getRetryBackoff() {
        return retryBackoff;
    }

    /**
     * Gets the maxRetries.
     *
     * @return the maxRetries.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Indicates whether SQS messaging is enabled.
     *
     * @return {@code true} if SQS callback messaging can be used; otherwise {@code false}.
     */
    public boolean isSqsMessagingEnabled() {
        return clientBuilder != null;
    }

    /**
     * Creates a new connection factory instance.
     *
     * @return a new {@link SQSConnectionFactory} ready to use or {@code null} if {@link #isSqsMessagingEnabled()} is
     *         {@code false}.
     */
    public SQSConnectionFactory createConnectionFactory() {
        if (!isSqsMessagingEnabled()) {
            return null;
        }
        return new SQSConnectionFactory(
                new ProviderConfiguration(),
                clientBuilder.build());
    }

    /**
     * Creates a new SQS connection ready to use.
     *
     * @return a new {@link SQSConnection} ready to use or {@code null} if {@link #isSqsMessagingEnabled()} is
     *         {@code false}.
     * @throws JMSException if a connection couldn't be established.
     */
    public SQSConnection createConnection() throws JMSException {
        if (!isSqsMessagingEnabled()) {
            return null;
        }
        if (connectionFactory == null) {
            Object lock = new Object();
            synchronized (lock) {
                if (connectionFactory == null) {
                    connectionFactory = createConnectionFactory();
                }
            }
        }
        return connectionFactory.createConnection();
    }

    /**
     * Gets the callback configuration instance.
     *
     * @return the {@link CallbackConfiguration} instance.
     */
    public static CallbackConfiguration getInstance() {
        if (instance == null) {
            Object lock = new Object();
            synchronized (lock) {
                if (instance == null) {
                    instance = new CallbackConfiguration();
                }
            }
        }
        return instance;
    }
}
