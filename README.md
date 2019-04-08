# This is the WireMock Extensions Project

It provides [WireMock](http://wiremock.org/) extensions for a dynamic mocking of JSON responses that are built on the [extension support](http://wiremock.org/docs/extending-wiremock/) provided by [WireMock](http://wiremock.org/).

The project is based on Java 8, uses Maven as build tool and provides extensions for WireMock 2.5.0+.

## Prerequisites
- JDK 8
- Maven 3.1.x+
- Docker 1.6+

## Build
- Clone the `wiremock-extensions` project from git
- Perform a `mvn clean install`
- Wait for the build and tests to finish.

## Value referencing

The response body stub as well as the callback body stub act as a template where match pattern may be defined and which will be replaced by matching JsonPaths.

## The JSON Body Transformer
It implements WireMock's `ResponseTransformer` and is an extension that is able to parse a JSON request body using [JsonPath](https://github.com/jayway/JsonPath) and interpolates found results into the JSON response that is returned by WireMock. It allows your WireMock response to be dynamically depending on the JSON request body. It was inspired by the [wiremock-body-transformer](https://github.com/opentable/wiremock-body-transformer) but focus only on JSON contents to provide support for JsonPath patterns. Thus even complex JSON can be handled during response manipulation.
You can find further information in the [documentation](json-body-transformer.md).

## The Callback Simulator
It implements WireMock's `PostServeAction` and is an extension that is able to emit POST requests to arbitrary URLs. It allows you WireMock callback to be dynamically depending on the JSON request and response bodies of the related request.
You can find further information in the [documentation](callback-simulator.md).

## Featured Keywords
You can find further information in the [documentation](keywords.md).

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
	<version>0.0.4</version>
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
	<groupId>uk.co.deliverymind</groupId>
	<artifactId>wiremock-maven-plugin</artifactId>
	<version>2.2.0</version>
	<dependencies>
		<dependency>
			<groupId>com.ninecookies.wiremock.extensions</groupId>
			<artifactId>json-body-transformer</artifactId>
			<version>0.0.4</version>
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

As WireMock supports running as a [standalone process](http://wiremock.org/docs/running-standalone/) there is also a [standalone version](https://raw.githubusercontent.com/mscookies/mvn-repo/master/releases/com/ninecookies/wiremock/extensions/json-body-transformer/0.0.1/json-body-transformer-0.0.1-jar-with-dependencies.jar) of the JsonBodyTransformer available that embeds its dependencies.

Start WireMock as standalone process with JsonBodyTransformer enabled as follows
```
$ java -cp wiremock-standalone-2.5.1.jar;json-body-transformer-0.0.4-jar-with-dependencies.jar com.github.tomakehurst.wiremock.standalone.WireMockServerRunner --verbose
```
