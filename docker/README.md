# WireMock docker Image
========================

This image is based on our base Linux image providing Java JDK 1.8.181 and contains [Wiremock](http://wiremock.org/docs)
extended with our [JSON body transformer](https://github.com/9cookies/wiremock-extensions/tree/dev/json-body-transformer).

# How to build this image

The dockerfile defines two variables e.g. the `WIREMOCK_VERSION` (defaults to 2.22.0) and the `EXTENSION_VERSION` (defaults to 0.0.5) 

```bash
# build with default versions
$ docker build \
    --tag xxx .

# build with specific versions
$ docker build \
    --build-arg WIREMOCK_VERSION=x.x.x
    --build-arg EXTENSION_VERSION=x.x.x
    --tag xxx .

# example
$ docker build \
    --build-arg WIREMOCK_VERSION=2.22.0
    --build-arg EXTENSION_VERSION=0.0.5
    --tag 940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/rps-wiremock:2.22.0-0.0.5
```

# Provided features

It is possible to add predefined JSON stubs and response files by mounting the directories containing the files to the image like so:

```bash
# example
$ docker run \
    -v /path/to/external/mappings:/home/wiremock/mappings \
    -v /path/to/external/__files:/home/wiremock/__files \
    ...
```

Beside that it is also possible to further extend wiremock as described in the [Wiremock - Running as Standalone Process](http://wiremock.org/docs/running-standalone/) by mounting a directory containing the extension jar like so:

```bash
#example
$ docker run \
    -v /path/to/external/extensions:/var/wiremock/extensions \
    ...
    --extensions custom.wiremock.ExtensionClassName
```

# How to use this image

e.g. in a pom.xml file for integration tests

```xml
<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    <version>0.27.2</version>
    <configuration>
        <skip>${skipITs}</skip>
        <images>
            <image>
            	<name>123456789.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/rps-localstack:2.22.0-0.0.5</name>
            	<run>
            		<ports>
            			<port>${docker.wiremock.port}:8080</port>
            		</ports>
            		<wait>
                        <tcp>
                            <host>127.0.0.1</host>
                            <ports>
                                <port>8080</port>
                            </ports>
                        </tcp>
                        <log>The WireMock server is started</log>
                        <time>20000</time>
                    </wait>
                    <cmd>--verbose --disable-banner</cmd>
                    <volumes>
                        <bind>
                            <volume>${docker.wiremock.resources}/__files:/home/wiremock/__files</volume>
                            <volume>${docker.wiremock.resources}/mappings:/home/wiremock/mappings</volume>
                        </bind>
                    </volumes>
            	</run>
            </image>
        </images>
    </configuration>
    <executions>
        <execution>
            <id>start</id>
            <phase>pre-integration-test</phase>
            <goals>
                <goal>build</goal>
                <goal>start</goal>
            </goals>
        </execution>
        <execution>
            <id>stop</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>stop</goal>
            </goals>
        </execution>
    </executions>
</plugin>

```