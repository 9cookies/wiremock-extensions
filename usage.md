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

See also the stubbing documentation of [JSON Body Transformer](json-body-transformer.md#stubbing) and [Callback Simulator](callback-simulator.md#stubbing).
