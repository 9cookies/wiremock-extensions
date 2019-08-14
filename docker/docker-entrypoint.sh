#!/bin/bash

# uncomment to debug this script
#set -x

# expose the docker host's ip address in /etc/hosts as host.docker.internal
# to work around https://github.com/docker/for-linux/issues/264
host host.docker.internal | grep "host.docker.internal has address" >> /dev/null
if [ $? -ne 0 ]; then
	echo "$(ip -4 route show default | cut -d' ' -f3)    host.docker.internal" >> /etc/hosts
fi


# Add `java -jar /wiremock-standalone.jar` as command if needed
if [ "${1:0:1}" = "-" ]; then
	set -- java -Xms2048m -Xmx4096m -Dlog4j.configurationFile="/var/wiremock/lib/log4j2.xml" \
	-cp /var/wiremock/lib/*:/var/wiremock/extensions/* \
	com.github.tomakehurst.wiremock.standalone.WireMockServerRunner \
	--extensions com.ninecookies.wiremock.extensions.JsonBodyTransformer,com.ninecookies.wiremock.extensions.CallbackSimulator \
	--container-threads 400 \
	"$@"
fi

exec "$@"
