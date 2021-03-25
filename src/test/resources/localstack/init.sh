#!/bin/bash

echo "creating topic"
topic=$(createTopic test-topic-name)
echo "    $topic"
echo "creating queue"
queue=$(createQueue test-queue-name)
echo "    $queue"
echo "create subscription"
echo "    $(createSubscription $queue $topic rawMessageDelivery)"
echo "initialization finished successfully"
exit 0
