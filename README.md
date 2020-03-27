# This is the WireMock Extensions Project

It provides [WireMock](http://wiremock.org/) extensions for a dynamic mocking of JSON responses that are built on the [extension support](http://wiremock.org/docs/extending-wiremock/) provided by [WireMock](http://wiremock.org/).

The project is based on Java 8, uses Maven as build tool and provides extensions for WireMock 2.22.0+.

## Prerequisites
- JDK 8
- Maven 3.1.x+
- Docker 1.6+

## Build
- Clone the `wiremock-extensions` project from git
- Perform a `mvn clean install`
- Wait for the build and tests to finish.


## Featured Keywords
You can find further information in the [documentation](keywords.md).

## Value referencing
The response and callback body stubs act as templates where match pattern may be defined and which will be replaced by matching JsonPaths.

## The JSON Body Transformer
It implements WireMock's `ResponseTransformer` and is an extension that is able to parse a JSON request body using [JsonPath](https://github.com/jayway/JsonPath) and interpolates found results into the JSON response that is returned by WireMock. It allows your WireMock response to be dynamically depending on the JSON request body. It was inspired by the [wiremock-body-transformer](https://github.com/opentable/wiremock-body-transformer) but focus only on JSON contents to provide support for JsonPath patterns. Thus even complex JSON can be handled during response manipulation.
You can find further information in the [documentation](json-body-transformer.md).

## The Callback Simulator
It implements WireMock's `PostServeAction` and is an extension that is able to emit POST requests to arbitrary URLs. It allows your WireMock callback to be dynamically depending on the JSON request and response bodies of the related request.
You can find further information in the [documentation](callback-simulator.md).

### Usage
This extension is not hosted on Maven central but on github. 
You can find further usage information [documentation](usage.md).
