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
            	<name>940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/rps-wiremock</name>
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

When used for integration testing with SQS message publishing to an Amazon SQS-compatible interface provided through another docker container the other and the wiremock container must share a network for a proper resolution of `getQueueUrl()` results.

The example below shows the maven docker configuration to use wiremock container alongside the fully functional local
AWS cloud stack (localstack) container with SQS enabled.

> :bulb: the wiremock port is exposed on the localstack container and the wiremock container configured its network.

```xml
<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    <version>0.27.2</version>
    <configuration>
        <skip>${skipITs}</skip>
        <images>
            <image>
                <name>940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/rps-localstack</name>
                <run>
                    <!-- note the wiremock port is exposed on this container -->
                    <ports>
                        <port>${sqs.port}:4576</port>
                        <port>8081:8080</port>
                        <port>${wiremock.port}:8090</port>
                    </ports>
                    <wait>
                        <tcp>
                            <host>127.0.0.1</host>
                            <ports>
                                <port>4576</port>
                                <port>8080</port>
                                <port>8090</port>
                            </ports>
                        </tcp>
                        <log>initialization finished successfully</log>
                        <time>60000</time>
                    </wait>
                    <env>
                        <SERVICES>sqs</SERVICES>
                    </env>
                    <volumes>
                        <bind>
                            <volume>${docker.resources.localstack}/init.sh:/docker-entrypoint-initaws.d/init.sh</volume>
                        </bind>
                    </volumes>
                </run>
            </image>
            <image>
                <name>940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/rps-wiremock</name>
                <run>
                    <!-- note wiremock uses the localstack network -->
                    <network>
                        <mode>container</mode>
                        <name>940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/rps-localstack</name>
                    </network>
                    <!-- note wiremock uses port 8090 as default port occupied by localstack dashboard -->
                    <cmd>--port 8090</cmd>
                    <env>
                        <MAX_RETRIES>3</MAX_RETRIES>
                        <RETRY_BACKOFF>500</RETRY_BACKOFF>
                        <!-- note wiremock defines the localstack SQS messaging port - not the mapped port -->
                        <MESSAGING_SQS_ENDPOINT>http://localhost:4576</MESSAGING_SQS_ENDPOINT>
                        <AWS_REGION>us-east-1</AWS_REGION>
                        <AWS_ACCESS_KEY_ID>X</AWS_ACCESS_KEY_ID>
                        <AWS_SECRET_ACCESS_KEY>X</AWS_SECRET_ACCESS_KEY>
                    </env>
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