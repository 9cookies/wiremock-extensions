package com.ninecookies.wiremock.extensions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.Topic;
import com.google.common.base.Strings;

public class SnsMessagePublisher {

    private static final Logger LOG = LoggerFactory.getLogger(SnsMessagePublisher.class);

    private static final String UNRESOLVABLE_TOPIC = UUID.randomUUID().toString();
    private Map<String, String> resolvedTopics;
    private AmazonSNS client;

    public SnsMessagePublisher() {
        CallbackConfiguration configuration = CallbackConfiguration.getInstance();
        if (!configuration.isMessagingEnabled()) {
            throw new IllegalStateException("AWS SNS messaging is disabled due to lacking configuration.");
        }
        this.client = configuration.createSnsClient();
        this.resolvedTopics = new ConcurrentHashMap<>();
    }

    public void sendMessage(String topicName, String messageJson) {
        String topicArn = resolveTopicArn(topicName);
        client.publish(topicArn, messageJson);
        LOG.debug("message '{}' published to '{}'", messageJson, topicName);
    }

    private String resolveTopicArn(String topicName) {
        String result = resolvedTopics.computeIfAbsent(topicName, tn -> {
            ListTopicsResult list = null;
            do {
                list = (list == null) ? client.listTopics() : client.listTopics(list.getNextToken());
                for (Topic topic : list.getTopics()) {
                    LOG.debug("{}.endsWith({}) => {}", topic.getTopicArn(), topicName,
                            topic.getTopicArn().endsWith(topicName));
                    if (topic.getTopicArn().endsWith(topicName)) {
                        return topic.getTopicArn();
                    }
                }
            } while (!Strings.isNullOrEmpty(list.getNextToken()));
            return UNRESOLVABLE_TOPIC;
        });
        if (UNRESOLVABLE_TOPIC.equals(result)) {
            throw new IllegalStateException("The arn for topic '" + topicName + "' could not be resolved.");
        }
        return result;
    }
}
