# Callback-Simulator

### Request and response value referencing

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

### Callback processing

Internally the callback simulator utilizes Java's `ScheduledExecutorService` with thread pool size of 50 to perform the callback requests. The thread pool size can be customized by specifying `SCHEDULED_THREAD_POOL_SIZE` environment variable with the desired size. Note that if the value is less than the default of 50 the default is used.

Callback requests errors will be logged but note that retry handling is disabled by default. If a callback fails it fails...

Enabling retry handling for callbacks which may be useful during load testing depending on the service under test is as simple as specifying `MAX_RETRIES` with some positive value depending on the number of desired retries that should be performed. The retry handling uses a back off period of 5 seconds by default that can be configured by specifying `RETRY_BACKOFF` (default 5_000 milliseconds). This value is multiplied with the invocation count to reschedule the callback.
So with `MAX_RETRIES` set to `3` retries will happen after 5, 10 and 15 seconds thus the callback will be retried for 30 seconds in total.  

### Request identification
The callback requests emitted by the callback-simulator will contain the `X-Rps-TraceId` header populated with a random value so that services which evaluate this header may add this identifier to their logging context. It is possible to specify a custom value as trace id as shown below in the examples.

### Stubbing
Instantiating the WireMock server with `CallbackSimulator` [extension](http://wiremock.org/docs/extending-wiremock/) instance

```java
new WireMockServer(wireMockConfig().extensions(new CallbackSimulator()));
```

The examples assume that you are familiar with WireMocks [stubbing](http://wiremock.org/docs/stubbing/) technique.

Specifying the callback simulator with arbitrary JSON content

```java
// arbitrary JSON object that represents the payload to POST
MyCallbackPayload callbackData = new MyCallbackPayload();
String callbackUrl = "http://localhost:8080/my/listening/callback/url"
int callbackDelay = 10000;

// Note the usage of the com.ninecookies.wiremock.extensions.api.Callbacks class
wireMockServer.stubFor(post(urlEqualTo("/url/to/post/to"))
        .withPostServeAction("callback-simulator", Callbacks.of(callbackDelay, callbackUrl, callbackData))
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
                }
            ]
        }
    }
}

```

and in addition with Basic authentication and custom trace identifier


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
