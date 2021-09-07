#!/bin/bash

# uncomment to debug this script
#set -x

# expose the docker host's ip address in /etc/hosts as host.docker.internal
# to work around https://github.com/docker/for-linux/issues/264
host host.docker.internal | grep "host.docker.internal has address" >> /dev/null
if [ $? -ne 0 ]; then
	echo "$(ip -4 route show default | cut -d' ' -f3)    host.docker.internal" >> /etc/hosts
fi

# create the callback result mapping with lowest priority by default to avoid 404 on report creation
# Note: we can't simply put the file to the image as the default mapping folder might
# be replaced by a volume - anyway ensure the folder exists
if [ ! -d "mappings" ]; then
  mkdir "mappings"
fi
if [ ! -f "mappings/callback-result.json" ]; then
  echo '{"priority":10,"request":{"url":"/callback/result","method":"POST"},"response":{"status":204}}' > "mappings/callback-result.json"
fi

# Add `java -jar /wiremock-standalone.jar` as command if needed
if [ "${1:0:1}" = "-" ]; then
	set -- java $JAVA_OPTS -Dlog4j.configurationFile="/var/wiremock/lib/log4j2.xml" \
	-cp /var/wiremock/lib/*:/var/wiremock/extensions/* \
	com.github.tomakehurst.wiremock.standalone.WireMockServerRunner \
	--extensions com.ninecookies.wiremock.extensions.JsonBodyTransformer,com.ninecookies.wiremock.extensions.CallbackSimulator,com.ninecookies.wiremock.extensions.RequestTimeMatcher \
	"$@"
fi

exec "$@"
