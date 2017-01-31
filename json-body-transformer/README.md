
# This is the JSON Body Transformer Project

It is a [WireMock](http://wiremock.org/) extension that is able to parse a JSON request body using [JsonPath](https://github.com/jayway/JsonPath) and interpolates found results into the JSON response that is returned by WireMock. It is built on the [extension support](http://wiremock.org/docs/extending-wiremock/) provided by WireMock and allows your WireMock response to be dynamically depending on the JSON request body. It was inspired by the [wiremock-body-transformer](https://github.com/opentable/wiremock-body-transformer) but focus only on JSON contents to provide support for JsonPath patterns. Thus even complex JSON can be handled during response manipulation.

### Request values referencing

The response body stub acts as a template where the match patterns are defined and which will be replaced by matching JsonPaths in the request body.
The syntax of match patterns are slightly different as original [JsonPath patterns](http://goessner.net/articles/JsonPath/) used by WireMocks [request matching](http://wiremock.org/docs/request-matching/) as the dot '.' of a pattern is omitted but the path is encapsulated with braces.

Imagine following JSON request body
```JSON
{
    "name": "John Doe",
    "age": 35,
    "appeared": "2016-11-23T11:10:00Z"
}
```
To get the `age` property with JsonPath one would define `$.age` JsonPath to get the value `35`. But to reference the value of the `age` property in a JSON response body one has to define `$(age)` instead.

The JSON of a response body referencing the value of `age` might look like

```JSON
{
    "found_age": "$(age)"
}
``` 

### Data Type Handling

As the request pattern is always a string value it has to be quoted even for numbers, booleans, a.s.o.. The `JsonBodyTransformer` will take care of the resulting data type and adds quotes if necessary and will omit them if required.

If a pattern defined in a JSON response has no matiching coutnerpart in the JSON request the result will yield to `null`.

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
which would be incorrect as `lastname` wasn't specified as `"null"` but was not found in the reqest.

### Stubbing
Instantiating the WireMock server with `JsonBodyTransformer` [extension](http://wiremock.org/docs/extending-wiremock/) instance
```java
new WireMockServer(wireMockConfig().extensions(new JsonBodyTransformer()));
```

The examles assume that you are familiar with WireMocks [stubbing](http://wiremock.org/docs/stubbing/) technique.

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

### Additional features
To get even more generic responses that help better testing the transformer defines some key words that start with an exclamation mark `!`.

Generating random integer for a response property
```JSON
{ "id": "$(!Random)" }
```

Generating random UUID for a response property
```JSON
{ "uuid": "$(!UUID)" }
```

Note that multiple occurrences of `$(!Random)` or `$(!UUID)` will result in injecting the same value for all properties. If this is not the desired behavior it is possible to add a arbitrary suffix to the `Random` or `UUID` keyword to get different values for different properties injected.
```JSON
{
  "id": "$(!RandomId)",
  "otherId": "$(!RandomOther)",
  "userId": "$(!UUID.User)",
  "ownerId": "$(!UUID.Owner)"
}
```
However, same suffixes result in same values for different properties. The following example shows the reuse of a specific random value for owner and creator id, but with a different id for modifier.
```JSON
{
  "id": "$(!Random)",
  "ownerId": "$(!UUID.Owner)",
  "creatorId": "$(!UUID.Owner)",
  "modifierId": "$(!UUID.Modifier)"
}
```

Generating current time stamp in [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601) UTC format for a response property
```JSON
{ "created": "$(!Instant)" }
```

Generating current time stamp in [Unix Epoch](https://en.wikipedia.org/wiki/Unix_time) UTC format for a response property
```JSON
{ "created": "$(!Timestamp)" }
```

Generating computed time stamp for a response property using the response pattern `$(!Instant.plus[UNITAMOUNT])` for [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601) or `$(!Timestamp.plus[UNITAMOUNT])` for [Unix Epoch](https://en.wikipedia.org/wiki/Unix_time) format where `UNIT` indicates the time unit and `AMOUNT` the amount to add or subtract. Valid units are `s`, `m` and `h` for seconds, minutes and hours. Units are case insensitive. Amount might be positive or negative depending on whether the desired result should be in the past (negative) or in the future (positive).
```JSON
{
    "one_second_in_future": "$(!Instant.plus[s1])",
    "one_second_in_past": "$(!Instant.plus[s-1])",
    "one_minute_in_future":  "$(!Instant.plus[m1])",
    "one_minute_in_past":  "$(!Timestamp.plus[m-1])",
    "one_hour_in_future":  "$(!Timestamp.plus[h1])",
    "one_hour_in_past":  "$(!Timestamp.plus[h-1])"
}
```

Note that all time stamps are returned in UTC format.


### Maven usage

This extension is not hosted on Maven central but on github. To use this artifact add the following repository definition either to your maven `settings.xml` or to the `pom.xml` file of your project that makes use of WireMock.

```XML
<repository>
	<id>9c-snapshots</id>
    <url>https://raw.github.com/9ccookies/mvn-repo/master/snapshots/</url>
    <snapshots>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>
    </snapshots>
</repository>
<repository>
    <id>9c-releases</id>
    <url>https://raw.github.com/9ccookies/mvn-repo/master/releases/</url>
    <releases>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>
    </releases>
</repository>
```

Add the following dependency to your projects `pom.xml` file.

```XML
<dependency>
	<groupId>com.ninecookies.wiremock.extensions</groupId>
	<artifactId>json-body-transformer</artifactId>
	<version>0.0.1</version>
</dependency>
```

### Maven plug-in usage


```XML
<pluginRepositories>
    <pluginRepository>
        <id>9c-snapshots</id>
        <url>https://raw.github.com/9cookies/mvn-repo/master/snapshots/</url>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </pluginRepository>
    <pluginRepository>
        <id>9c-releases</id>
        <url>https://raw.github.com/9cookies/mvn-repo/master/releases/</url>
        <releases>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </releases>
    </pluginRepository>
</pluginRepositories>
```

```XML
<plugin>
	<groupId>uk.co.automatictester</groupId>
	<artifactId>wiremock-maven-plugin</artifactId>
	<version>2.0.0</version>
	<dependencies>
		<dependency>
			<groupId>com.ninecookies.wiremock.extensions</groupId>
			<artifactId>json-body-transformer</artifactId>
			<version>0.0.1-SNAPSHOT</version>
		</dependency>
	</dependencies>
	<executions>
		<execution>
			<goals>
				<goal>run</goal>
			</goals>
			<configuration>
				<params>--extensions com.ninecookies.wiremock.extensions.JsonBodyTransformer</params>
			</configuration>
		</execution>
	</executions>
</plugin>
```

### Standalone usage

As WireMock supports running as a [standalone process](http://wiremock.org/docs/running-standalone/) there is also a [standalone version]https://raw.githubusercontent.com/mscookies/mvn-repo/master/releases/com/ninecookies/wiremock/extensions/json-body-transformer/0.0.1/json-body-transformer-0.0.1-jar-with-dependencies.jar) of the JsonBodyTransformer available that embeds its dependencies.

Start WireMock as standalone process with JsonBodyTransformer enabled as follows
```
$ java -cp wiremock-standalone-2.1.11.jar;json-body-transformer-0.0.1-jar-with-dependencies.jar com.github.tomakehurst.wiremock.standalone.WireMockServerRunner --verbose
```
