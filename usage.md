# Usage

### Maven usage

This extension is not hosted on Maven central but on github. To use this artifact add the following repository definition either to your maven `settings.xml` or to the `pom.xml` file of your project that makes use of WireMock.


```XML
<repositories>
    <repository>
        <id>9c-snapshots</id>
        <url>https://raw.github.com/9cookies/mvn-repo/master/snapshots/</url>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
    <repository>
        <id>9c-releases</id>
        <url>https://raw.github.com/9cookies/mvn-repo/master/releases/</url>
        <releases>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </releases>
    </repository>
</repositories>
```

Add the following dependency to your projects `pom.xml` file.

```XML
<dependency>
    <groupId>com.ninecookies.wiremock.extensions</groupId>
    <artifactId>wiremock-extensions</artifactId>
    <version>0.0.7</version>
</dependency>
```

### WireMock Maven plug-in usage

If you are using the [WireMock Maven Plugin](https://github.com/automatictester/wiremock-maven-plugin#wiremock-maven-plugin) you have to specify the plug-in repository instead like so:

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

In the `wiremock-maven-plugin` the add the dependency and register the provided extensions like so:
```XML
<build>
    <plugins>
       <plugin>
          <groupId>uk.co.automatictester</groupId>
          <artifactId>wiremock-maven-plugin</artifactId>          
          <version>5.0.0</version>
          
          [...]
          
          <dependencies>
             <dependency>
                <groupId>com.github.tomakehurst</groupId>
                <artifactId>wiremock</artifactId>
                <version>2.24.1</version>
             </dependency>
             <dependency>
                <groupId>com.ninecookies.wiremock.extensions</groupId>
                <artifactId>wiremock-extensions</artifactId>
                <version>0.0.7</version>
             </dependency>
          </dependencies>
          <executions>
             <execution>
                <goals>
                   <goal>run</goal>
                </goals>
                <configuration>
                   <params>--extensions com.ninecookies.wiremock.extensions.JsonBodyTransformer, com.ninecookies.wiremock.extensions.CallbackSimulator</params>
                </configuration>
             </execution>
          </executions>
       </plugin>   
    </plugins>
</build>
```

### Standalone usage

As WireMock supports running as a [standalone process](http://wiremock.org/docs/running-standalone/) there is also a [standalone version](https://raw.github.com/9cookies/mvn-repo/master/releases/com/ninecookies/wiremock/extensions/wiremock-extensions/0.0.6/wiremock-extensions-0.0.6-jar-with-dependencies.jar) of the wiremock-extensions available that embeds its dependencies.

Start WireMock as standalone process with JsonBodyTransformer and CallbackSimulater enabled as follows
```
$ java -cp wiremock-standalone-2.22.0.jar;wiremock-extensions-0.0.7-jar-with-dependencies.jar com.github.tomakehurst.wiremock.standalone.WireMockServerRunner --verbose --extensions com.ninecookies.wiremock.extensions.JsonBodyTransformer,com.ninecookies.wiremock.extensions.CallbackSimulator,com.ninecookies.wiremock.extensions.RequestMatcherExtension
```

See also the stubbing documentation of [JSON Body Transformer](json-body-transformer.md#stubbing) and [Callback Simulator](callback-simulator.md#stubbing).
