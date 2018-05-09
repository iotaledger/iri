#!/bin/bash
# See Dockerfile and DOCKER.md for further info

if [ "${DOCKER_IRI_MONITORING_API_PORT_ENABLE}" == "1" ]; then
  nohup socat -lm TCP-LISTEN:14266,fork TCP:127.0.0.1:${DOCKER_IRI_MONITORING_API_PORT_DESTINATION} &
fi

for neighbor in $(grep -v \# $DOCKER_IRI_NEIGHBOR_FILE); do
  DOCKER_IRI_NEIGHBORS+=" $neighbor"
done

exec java \
  $JAVA_OPTIONS \
  -Xms$JAVA_MIN_MEMORY \
  -Xmx$JAVA_MAX_MEMORY \
  -Djava.net.preferIPv4Stack=true \
  -jar $DOCKER_IRI_JAR_PATH \
  --remote --remote-limit-api "$DOCKER_IRI_REMOTE_LIMIT_API" \
  --neighbors "$DOCKER_IRI_NEIGHBORS" \
  "$@"
