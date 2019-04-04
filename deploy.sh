#!/bin/bash

# uncomment next line to debug the script
#set -x

# build and deploy maven project
mvn clean deploy
# wiremock and extension versions for docker
wiremockVersion=$(mvn -f json-body-transformer/pom.xml -q help:evaluate -Dexpression=wiremock.version -DforceStdout)
extensionVersion=$(mvn -f json-body-transformer/pom.xml -q help:evaluate -Dexpression=project.version -DforceStdout)
# extensions jar for docker
cp json-body-transformer/target/json-body-transformer-$extensionVersion-jar-with-dependencies.jar docker/
# init docker variables
image=rps-wiremock
tag=$wiremockVersion-$extensionVersion
repository=940776968316.dkr.ecr.eu-west-1.amazonaws.com/deliveryhero/$image
# build docker image
docker build --no-cache \
	--build-arg WIREMOCK_VERSION=$wiremockVersion \
	--build-arg EXTENSION_VERSION=$extensionVersion \
	--tag $image:$tag \
	docker
# create docker tags
docker tag $image:$tag $repository:$tag
docker tag $image:$tag $repository:latest
# push docker tags
docker push $repository:$tag 
docker push $repository:latest
