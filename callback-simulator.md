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

### SQS Callbacks

The configuration for the AWS SQS client requires the `AWS_REGION` environment variable to be provided. For testing purposes it is possible to specify the `MESSAGING_SQS_ENDPOINT`. 

The only additional property for SQS callbacks is the `queue` property to provide the queue name to publish messages to. The queue property may contain placeholders like request and response references or an [environment variable](keywords.md#environment-variable-key-word).

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

> :bulb: see [ElasticMQ](https://github.com/softwaremill/elasticmq/blob/master/README.md#starting-an-embedded-elasticmq-server-with-an-sqs-interface) and [localstack](https://github.com/localstack/localstack#localstack---a-fully-functional-local-aws-cloud-stack) for details on testing AWS SQS messaging.

### HTTP Callbacks

HTTP provides some more properties compared to SQS. Beside the obvious required `url` also the optional support for `authentication` and request identification using a `traceId` is implemented. Similar to the queue property for SQS callbacks the url, username and password properties may contain placeholders and keywords.

#### Retry handling

Enabling retry handling for HTTP callbacks which may be useful during load testing depending on the service under test is as simple as specifying `MAX_RETRIES` with some positive value depending on the number of desired retries that should be performed. The retry handling uses a back off period of 5 seconds by default that can be configured by specifying `RETRY_BACKOFF` (default 5_000 milliseconds). This value is multiplied with the invocation count to reschedule the callback.
So with `MAX_RETRIES` set to `3` retries will happen after 5, 10 and 15 seconds thus the callback will be retried for 30 seconds in total.  

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
    "data": {
        "json representation": "of MyCallbackPayload"
    }
}
```

## Stubbing

Instantiating the WireMock server with `CallbackSimulator` [extension](http://wiremock.org/docs/extending-wiremock/) instance

```java
new WireMockServer(wireMockConfig().extensions(new CallbackSimulator()));
```

The examples assume that you are familiar with WireMocks [stubbing](http://wiremock.org/docs/stubbing/) technique.

### 1. Example - mixed callbacks

Specify the callback simulator with two callbacks. One is an HTTP request the other an SQS message callback.

```java
// arbitrary JSON object that represents the payload to POST
MyCallbackPayload callbackData = new MyCallbackPayload();
String callbackUrl = "http://localhost:8080/my/listening/callback/url";
int httpCallbackDelay = 10000;
// arbitrary JSON object that represents the message to sent
MyCallbackMessage callbackMessage = new MyCallbackMessage();
String queueName = "callback-queue-name";
int sqsCallbackDelay = 11000;

// Note the usage of the com.ninecookies.wiremock.extensions.api.Callbacks and Callback classes
wireMockServer.stubFor(post(urlEqualTo("/url/to/post/to"))
        .withPostServeAction("callback-simulator", Callbacks.of(
            Callback.of(httpCallbackDelay, callbackUrl, callbackData),
            Callback.ofQueueMessage(sqsCallbackDelay, queueName, callbackMessage)
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
                        "json representation": "of MyCallbackMessage"
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
