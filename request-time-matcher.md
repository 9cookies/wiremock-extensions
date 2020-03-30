# Request Time Matcher

### Request time matching

The `request-time-matcher` extension provides the ability to match the time of the request against a provided regular expression.

This comes in handy to simulate e.g. short down times / outages of services during load tests.

Imagine a JSON mapping that serves a callback URL which only ever returns 204

```java
stubFor(post(urlEqualTo("3rd/party/url")).willReturn(aResponse().withStatus(204);
```
Similar in JSON
```JSON
{
    "request": {
        "method": "POST",
        "url": "3rd/party/url"
    },
    "response": {
        "status": 204
    }
}
```

If requests to that very URL should fail for a certain amount of time during a load test to see how the calling service behaves and potentially recovers in such situations this matcher can be used provide an additional stubbing to serve a different response for the same URL at a higher priority as the default (5 - see [stubbing](http://wiremock.org/docs/stubbing/)) as shown in the example below where the HTTP Status 500 will be returned for 10 minutes between 10 and 19:59:59 every hour.

```java
stubFor(post(urlEqualTo("3rd/party/url")).atPriority(3)
    .andMatching("request-time-matcher", Parameters.one("pattern", ".*T\\d{2}:1\\d{1}:\\d{2}\\..*"))
    .willReturn(aResponse().withStatus(500);
```
Similar in JSON
```JSON
{
    "request": {
        "method": "POST",
        "url": "3rd/party/url"
    },
    "customMatcher" : {
      "name" : "request-time-matcher",
      "parameters" : {
        "pattern" : ".*T\\d{2}:1\\d{1}:\\d{2}\\..*"
      }
    },
    "response": {
        "status": 500
    }
}
```
