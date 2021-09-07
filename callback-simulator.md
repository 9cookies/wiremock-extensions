# Callback-Simulator

## Request and response value referencing

The callback data acts as a template where match patterns may be defined and which will be replaced by matching JsonPaths from the initial request and response. The general syntax follows the same rules as described in [json-body-transformer](json-body-transformer.md) and [keywords](keywords.md) documentation.

Keywords can be used exactly in the way as described in the documentation, but in contrast to the body transformer references to JSON of the initial request/response have to be prefixed.

Imagine following JSON request

```JSON
{
    "name": "John Doe",
    "age": 35,
    "appeared": "2016-11-23T11:10:00Z"
}
```

and the related JSON response 

```JSON
{
    "id": "$(UUID)",
    "found_age": "$(age)",
    "composed_string": "$(name) is $(age) years old."
}
```

To reference the `name` of the request and the `id` of the response in a callback definition one has to prefix the match patterns in the callback data JSON like so

```JSON
{
  "reference_id": "$(response.id)",
  "referenced_name": "$(request.name)",
  "timestamp": "$(!Instant)"
}
```

Note the additional `timestamp` property which uses the `!Instant` [keyword](keywords.md) to populate the related value. 

The data type handling is the same as describe for the [json-body-transformer](json-body-transformer.md#data-type-handling).

Also for the callback URL request and response values may be referenced. If the replacement value is contained in a query string part it will be URL encoded.

## Callback processing

Internally the callback simulator utilizes Java's `ScheduledExecutorService` with thread pool size of 50 to perform the callback requests. The thread pool size can be customized by specifying `SCHEDULED_THREAD_POOL_SIZE` environment variable with the desired size. Note that if the value is less than the default of 50 the default is used.

Callback requests errors will be logged but note that retry handling is disabled by default. If a callback fails it fails...

### Retry handling

Enabling retry handling for callbacks which may be useful during load testing depending on the service under test is as simple as specifying `MAX_RETRIES` with some positive value depending on the number of desired retries that should be performed. The retry handling uses a back off period of 5 seconds by default that can be configured by specifying `RETRY_BACKOFF` (default 5_000 milliseconds). This value is multiplied with the invocation count to reschedule the callback.
So with `MAX_RETRIES` set to `3` retries will happen after 5, 10 and 15 seconds thus the callback will be retried for 30 seconds in total.

### Common Callback Model

The properties shared by all callbacks are the `delay` and the `data` where the delay defines the wait time in seconds the callback will scheduled for when the URL defined in the mapping stub was requested.

```json
{
    "delay": 1000,
    "data": {
        "json_representation": "of MyCallbackPayload"
    }
}
```

### SNS/SQS Callbacks

The configuration for the AWS SNS and SQS clients requires the `AWS_REGION` environment variable to be provided. For testing purposes it is possible to specify the `AWS_SQS_ENDPOINT` and `AWS_SNS_ENDPOINT` but depending of the test stack (elasticMQ / localstack) access id and secret key might be required as well.

>:warning: elasticMQ doesn't support SNS messaging

When running as a kubernetes pod in a larger test environment with real AWS queues the endpoint should be empty so that the default AWS endpoint is used. The credentials should be set up through the container.

>:warning: if the configured AWS account is not authorized to perform: SNS:ListTopics a full qualified SNS topic arn must be configured

The only additional property for SQS callbacks is the `queue` and for SNS callbacks is the `topic` property to provide the queue or topic name to publish messages to. The queue or topic property may contain placeholders like request and response references or an [environment variable](keywords.md#environment-variable-key-word).

#### SQS Callback example JSON

```json
{
    "queue": "my-sqs-queue-name",
    "delay": 1000,
    "data": {
        "json_representation": "of MyCallbackPayload"
    }
}
```

```json
{
    "queue": "$(!ENV[SQS_QUEUE_NAME])",
    "delay": 1000,
    "data": {
        "json_representation": "of MyCallbackPayload"
    }
}
```

#### SNS Callback example JSON

```json
{
    "topic": "my-sns-topic-name",
    "delay": 1000,
    "data": {
        "json_representation": "of MyCallbackPayload"
    }
}
```

```json
{
    "queue": "$(!ENV[SNS_TOPIC_NAME])",
    "delay": 1000,
    "data": {
        "json_representation": "of MyCallbackPayload"
    }
}
```


> :bulb: see [ElasticMQ](https://github.com/softwaremill/elasticmq/blob/master/README.md#starting-an-embedded-elasticmq-server-with-an-sqs-interface) and [localstack](https://github.com/localstack/localstack#localstack---a-fully-functional-local-aws-cloud-stack) for details on testing AWS SNS / SQS messaging.

### HTTP Callbacks

HTTP provides some more properties compared to SQS. Beside the obvious required `url` also the optional support for `authentication` and request identification using a `traceId` is implemented. Similar to the topic or queue property for AWS SNS/SQS callbacks the `url`, `username` and `password` properties may contain placeholders and keywords. 

#### Request identification

The callback requests emitted by the callback-simulator will contain the `X-Rps-TraceId` header populated with a random value. Services which evaluate this header may add this identifier to their logging context. It is possible to specify a custom value as trace id as outlined below.

#### HTTP callback example

```json
{
    "delay": 10000,
    "url": "http://localhost:8080/my/listening/callback/url",
    "authentication":  {
        "username": "user",
        "password": "pass"
    },
    "traceId": "my-trace-identifier",
    "data": {
        "json representation": "of MyCallbackPayload"
    }
}
```

```json
{
    "delay": 10000,
    "url": "$(request.callbackUrl)",
    "authentication":  {
        "username": "$(!ENV[CALLBACK_USER])",
        "password": "$(!ENV[CALLBACK_PASSWORD])"
    },
    "traceId": "my-trace-identifier",
    "expectedHttpStatus": 400,
    "data": {
        "json representation": "of invalid MyCallbackPayload"
    }
}
```

#### Verification

In contrast to SNS/SQS callbacks HTTP implementation get's a synchronous response status. By default a 2xx HTTP status result is considered successful for a callback request, but for use case specific expectations, e.g. duplicate callback request to the same resource, it is possible to specify the optional `expectedHttpStatus` to define the HTTP status value to indicates success.

Successful execution of a callback is recorded in the WireMock journal with URL `/callback/result` and the report payload  provides the absolute callback request URL as well as response status and body as shown by the example:

```json
{
  "result" : "success",
  "target" : "http://localhost:8080/my/listening/callback/url",
  "response" : {
    "status" : 204,
    "body" : "null"
  }
}
```

```json
{
  "result" : "success",
  "target" : "http://localhost:8080/my/listening/callback/url",
  "response" : {
    "status" : 400,
    "body" : "{\"error_code\":\"my-fancy-error\"}"
  }
}
```

Using WireMocks built-in [verification mechanism](http://wiremock.org/docs/verifying/) a successful HTTP callback results can be verified as follows:

```java
verify(1, postRequestedFor(urlPathEqualTo("/callback/result"))
        .withRequestBody(matchingJsonPath("$.[?(@.target == 'http://localhost:8080/my/listening/callback/url')]"))
        .withRequestBody(matchingJsonPath("$.[?(@.response.status == 400)]"))
        .withRequestBody(matchingJsonPath("$.[?(@.response.body =~ /.*my-fancy-error.*/i)]")));
```

## Stubbing

Instantiating the WireMock server with `CallbackSimulator` [extension](http://wiremock.org/docs/extending-wiremock/) instance

```java
new WireMockServer(wireMockConfig().extensions(new CallbackSimulator()));
```

The examples assume that you are familiar with WireMocks [stubbing](http://wiremock.org/docs/stubbing/) technique.

### 1. Example - mixed callbacks

Specify the callback simulator with three callbacks. One is an HTTP request the second an SQS message and the last an SNS message callback.

```java
// arbitrary JSON object that represents the payload to POST
MyCallbackPayload callbackData = new MyCallbackPayload();
String callbackUrl = "http://localhost:8080/my/listening/callback/url";
int httpCallbackDelay = 10000;
// arbitrary JSON object that represents the SQS message to sent
MyCallbackSqsMessage callbackSqsMessage = new MyCallbackSqsMessage();
String queueName = "callback-queue-name";
int sqsCallbackDelay = 11000;
// arbitrary JSON object that represents the SNS message to sent
MyCallbackSnsMessage callbackSqsMessage = new MyCallbackSnsMessage();
String topicName = "callback-topic-name";
int snsCallbackDelay = 12000;

// Note the usage of the com.ninecookies.wiremock.extensions.api.Callbacks and Callback classes
wireMockServer.stubFor(post(urlEqualTo("/url/to/post/to"))
        .withPostServeAction("callback-simulator", Callbacks.of(
            Callback.of(httpCallbackDelay, callbackUrl, callbackData),
            Callback.ofQueueMessage(sqsCallbackDelay, queueName, callbackSqsMessage),
            Callback.ofTopicMessage(snsCallbackDelay, topicName, callbackSnsMessage)
        ))
        .willReturn(aResponse()
                .withHeader("content-type", "application/json")
                .withBody("{\"id\":\"$(!UUID)\"}")
                .withTransformers("json-body-transformer")
                .withStatus(201)));
```

Similar in JSON

```JSON
{
    "request": {
        "url": "/url/to/post/to",
        "method": "POST"
    },
    "response": {
        "status": 201,
        "body": "{\"id\":\"$(!UUID)\"}",
        "headers": {
            "content-type": "application/json"
        },
        "transformers": [
            "json-body-transformer"
        ]
    },
    "postServeActions": {
        "callback-simulator": {
            "callbacks": [
                {
                    "delay": 10000,
                    "url": "http://localhost:8080/my/listening/callback/url",
                    "data": {
                        "json representation": "of MyCallbackPayload"
                    }
                },
                {
                    "delay": 11000,
                    "queue": "callback-queue-name",
                    "data": {
                        "json representation": "of MyCallbackSqsMessage"
                    }
                }
                {
                    "delay": 12000,
                    "queue": "callback-topic-name",
                    "data": {
                        "json representation": "of MyCallbackSnsMessage"
                    }
                }
            ]
        }
    }
}

```

### 2. Example - authenticated HTTP callback

Convenient usage of `Callbacks.of()` overload with Basic authentication and custom trace identifier for a single callback. Same method is provided by `Callback`.

```java
// arbitrary JSON object that represents the payload to POST
MyCallbackPayload callbackData = new MyCallbackPayload();
String callbackUrl = "http://localhost:8080/my/listening/callback/url"
int callbackDelay = 10000;

// Note the usage of the com.ninecookies.wiremock.extensions.api.Callbacks class
wireMockServer.stubFor(post(urlEqualTo("/url/to/post/to"))
        .withPostServeAction("callback-simulator", Callbacks.of(callbackDelay, callbackUrl, "user", "pass", "my-trace-identifier", callbackData))
        .willReturn(aResponse()
                .withHeader("content-type", "application/json")
                .withBody("{\"id\":\"$(!UUID)\"}")
                .withTransformers("json-body-transformer")
                .withStatus(201)));
```

Similar in JSON

```JSON
{
    "request": {
        "url": "/url/to/post/to",
        "method": "POST"
    },
    "response": {
        "status": 201,
        "body": "{\"id\":\"$(!UUID)\"}",
        "headers": {
            "content-type": "application/json"
        },
        "transformers": [
            "json-body-transformer"
        ]
    },
    "postServeActions": {
        "callback-simulator": {
            "callbacks": [
                {
                    "delay": 10000,
                    "url": "http://localhost:8080/my/listening/callback/url",
                    "authentication":  {
                      "username": "user",
                      "password": "pass"
                    },
                    "traceId": "my-trace-identifier",
                    "data": {
                        "json representation": "of MyCallbackPayload"
                    }
                }
            ]
        }
    }
}
```

### 3. Example - environment variables

Callback configuration based on environment variables instead of having confidential information like username and password in a JSON mapping file.

```java
// arbitrary JSON object that represents the payload to POST
MyCallbackPayload callbackData = new MyCallbackPayload();
int httpCallbackDelay = 10000;
// arbitrary JSON object that represents the message to sent
MyCallbackMessage callbackMessage = new MyCallbackMessage();
int sqsCallbackDelay = 11000;

// Note the usage of the com.ninecookies.wiremock.extensions.api.Callbacks and Callback classes
wireMockServer.stubFor(post(urlEqualTo("/url/to/post/to"))
        .withPostServeAction("callback-simulator", Callbacks.of(
            Callback.of(httpCallbackDelay, "$(!ENV[CBURL])", "$(!ENV[CBUSER])", "$(!ENV[CBPASS])", callbackData),
            Callback.ofQueueMessage(sqsCallbackDelay, "$(!ENV[CBQUEUE]), callbackMessage)
        ))
        .willReturn(aResponse()
                .withHeader("content-type", "application/json")
                .withBody("{\"id\":\"$(!UUID)\"}")
                .withTransformers("json-body-transformer")
                .withStatus(201)));
```

Similar in JSON

```JSON
{
    "request": {
        "url": "/url/to/post/to",
        "method": "POST"
    },
    "response": {
        "status": 201,
        "body": "{\"id\":\"$(!UUID)\"}",
        "headers": {
            "content-type": "application/json"
        },
        "transformers": [
            "json-body-transformer"
        ]
    },
    "postServeActions": {
        "callback-simulator": {
            "callbacks": [
                {
                    "delay": 10000,
                    "url": "$(!ENV[CBURL])",
                    "authentication":  {
                      "username": "$(!ENV[CBUSER])",
                      "password": "$(!ENV[CBPASS])"
                    },
                    "data": {
                        "json representation": "of MyCallbackPayload"
                    }
                },
                {
                    "delay": 11000,
                    "queue": "$(!ENV[CBQUEUE])",
                    "data": {
                        "json representation": "of MyCallbackMessage"
                    }
                }
            ]
        }
    }
}
```
