#!/bin/bash

set -ex

echo "initialize build"
wiremockVersion=$(mvn -f pom.xml -q help:evaluate -Dexpression=wiremock.version -DforceStdout)
extensionVersion=$(mvn -f pom.xml -q help:evaluate -Dexpression=project.version -DforceStdout)

image=rps-wiremock
tag=$wiremockVersion-$extensionVersion
repository=940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/$image

if [ -f "target/wiremock-extensions-$extensionVersion-jar-with-dependencies.jar" ]; then
	cp target/wiremock-extensions-$extensionVersion-jar-with-dependencies.jar docker/
else
	echo "installing wiremock-extensions-$extensionVersion"
	mvn clean install
	cp target/wiremock-extensions-$extensionVersion-jar-with-dependencies.jar docker/
fi

if [ -f "./docker/wiremock-standalone-$wiremockVersion.jar" ]; then
	echo "wiremock-standalone-$wiremockVersion.jar found - skipping download"
else
	wget https://repo1.maven.org/maven2/com/github/tomakehurst/wiremock-standalone/$wiremockVersion/wiremock-standalone-$wiremockVersion.jar \
	    -O ./docker/wiremock-standalone-$wiremockVersion.jar
fi

echo "build docker image $image:$tag"
docker build --no-cache \
	--build-arg WIREMOCK_VERSION=$wiremockVersion \
	--build-arg EXTENSION_VERSION=$extensionVersion \
	--tag $image:$tag \
	docker
docker tag $image:$tag $image:latest
docker tag $image:$tag $repository:latest
