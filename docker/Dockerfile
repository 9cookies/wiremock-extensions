FROM 940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/rps-base-java:jdk-8.181

ARG WIREMOCK_VERSION=2.22.0
ARG EXTENSION_VERSION=0.0.6

# basic utils
RUN apk add --update --no-cache openssl 'su-exec>=0.2' bash curl jq

# wiremock std jar
RUN mkdir -p /var/wiremock/lib/ \ 
  && wget https://repo1.maven.org/maven2/com/github/tomakehurst/wiremock-standalone/$WIREMOCK_VERSION/wiremock-standalone-$WIREMOCK_VERSION.jar \
    -O /var/wiremock/lib/wiremock-standalone.jar \
  && wget https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.26/slf4j-simple-1.7.26.jar \
    -O /var/wiremock/lib/slf4j-simple-1.7.26.jar

COPY json-body-transformer-$EXTENSION_VERSION-jar-with-dependencies.jar /var/wiremock/lib/json-body-transformer.jar
COPY docker-entrypoint.sh /

# set working directory to external mount point
WORKDIR /home/wiremock
VOLUME /home/wiremock

EXPOSE 8080 8443

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["java", "-cp", "/var/wiremock/lib/*:/var/wiremock/extensions/*", "com.github.tomakehurst.wiremock.standalone.WireMockServerRunner", "--extensions", "com.ninecookies.wiremock.extensions.JsonBodyTransformer,com.ninecookies.wiremock.extensions.CallbackSimulator"]
