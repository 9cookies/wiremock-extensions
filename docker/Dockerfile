FROM 940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/rps-base-java:jdk-8.181

ARG WIREMOCK_VERSION=2.22.0
ARG EXTENSION_VERSION=0.0.6

# basic utils
RUN apk add --update --no-cache openssl 'su-exec>=0.2' bash curl jq

# wiremock std jar
RUN mkdir -p /var/wiremock/lib/
COPY wiremock-standalone-$WIREMOCK_VERSION.jar /var/wiremock/lib/wiremock-standalone.jar
COPY wiremock-extensions-$EXTENSION_VERSION-jar-with-dependencies.jar /var/wiremock/lib/wiremock-extensions.jar
COPY log4j2.xml /var/wiremock/lib/log4j2.xml
COPY docker-entrypoint.sh /

# set working directory to external mount point
WORKDIR /home/wiremock
VOLUME /home/wiremock

EXPOSE 8080 8443
EXPOSE 7091 7091

ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["--disable-banner"]
