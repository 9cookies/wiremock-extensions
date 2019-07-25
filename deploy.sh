#!/bin/bash

# exist script if something goes wrong
set -e
# wiremock and extension versions for docker
wiremockVersion=$(mvn -f pom.xml -q help:evaluate -Dexpression=wiremock.version -DforceStdout)
extensionVersion=$(mvn -f pom.xml -q help:evaluate -Dexpression=project.version -DforceStdout)

# build and deploy maven project
mvn clean deploy
# extensions jar for docker
cp target/wiremock-extensions-$extensionVersion-jar-with-dependencies.jar docker/
# wiremock standalone
wget https://repo1.maven.org/maven2/com/github/tomakehurst/wiremock-standalone/$wiremockVersion/wiremock-standalone-$wiremockVersion.jar \
    -O ./docker/wiremock-standalone-$wiremockVersion.jar
# init docker variables
image=rps-wiremock
tag=$wiremockVersion-$extensionVersion
repository=940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/$image
# build docker image
docker build --no-cache \
	--build-arg WIREMOCK_VERSION=$wiremockVersion \
	--build-arg EXTENSION_VERSION=$extensionVersion \
	--tag $image:latest \
	docker
# create docker tags
docker tag $image:latest $repository:$tag
docker tag $image:latest $repository:latest
# push docker tags
docker push $repository:$tag 
docker push $repository:latest
