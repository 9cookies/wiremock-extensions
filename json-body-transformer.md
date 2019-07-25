
# JSON Body Transformer Project

### Request value referencing

The response body stub acts as a template where the match patterns are defined and which will be replaced by matching JsonPaths in the request body.
The syntax of match patterns is slightly different as original [JsonPath patterns](http://goessner.net/articles/JsonPath/) used by WireMocks [request matching](http://wiremock.org/docs/request-matching/) as the dot '.' of a pattern is omitted but the path is encapsulated with braces.

Imagine following JSON request body

```JSON
{
    "name": "John Doe",
    "age": 35,
    "appeared": "2016-11-23T11:10:00Z"
}
```
To get the `age` property with JsonPath one would define `$.age` JsonPath to get the value `35`. But to reference the value of the `age` property in a JSON response body one has to define `$(age)` instead.

The JSON of a response body referencing the values of `age` and `name` might look like

```JSON
{
    "found_age": "$(age)",
    "composed_string": "$(name) is $(age) years old."
}
``` 

### Data Type Handling

As the request pattern is always a string value it has to be quoted even for numbers, booleans, a.s.o.. The `JsonBodyTransformer` will take care of the resulting data type and adds quotes if necessary and will omit them if required, but worth to mention that values used in composed strings will always be the raw values.

If a pattern defined in a JSON response has no matching counterpart in the JSON request the result will yield to `null`.

For the example above that will mean a response like
```JSON
{
    "found_age": "$(age)",
    "found_name": "$(lastname)"
}
```
will be returned as
```JSON
{
    "found_age": 35,
    "found_name": null
}
```
rather than
```JSON
{
    "found_age": 35,
    "found_name": "null"
}
```
which would be incorrect as `lastname` wasn't specified as `"null"` but was not found in the request.

### Stubbing
Instantiating the WireMock server with `JsonBodyTransformer` [extension](http://wiremock.org/docs/extending-wiremock/) instance
```java
new WireMockServer(wireMockConfig().extensions(new JsonBodyTransformer()));
```

The examples assume that you are familiar with WireMocks [stubbing](http://wiremock.org/docs/stubbing/) technique.

Specifying the transformer in code with response body
```java
stubFor(post(urlEqualTo("url/to/post/to")).willReturn(aResponse()
        .withStatus(201)
        .withHeader("content-type", "application/json")
        .withBody("{\"name\":\"$(name)\"}")
        .withTransformers("json-body-transformer");
```
Similar in JSON
```JSON
{
    "request": {
        "method": "POST",
        "url": "url/to/post/to"
    },
    "response": {
        "status": 201,
        "headers": {
            "content-type": "application/json"
        },
        "jsonBody": {
            "name": "$(name)"
        },
		"transformers" : ["json-body-transformer"]
    }
}
```

Specifying the transformer in code with file response
```java
stubFor(post(urlEqualTo("url/to/post/to")).willReturn(aResponse()
        .withStatus(201)
        .withHeader("content-type", "application/json")
        .withBodyFile("post-response.json")
        .withTransformers("json-body-transformer");
```
Similar in JSON
```JSON
{
    "request": {
        "method": "POST",
        "url": "url/to/post/to"
    },
    "response": {
        "status": 201,
        "headers": {
            "content-type": "application/json"
        },
        "bodyFileName": "post-response.json",
		"transformers" : ["json-body-transformer"]
    }
}
```
