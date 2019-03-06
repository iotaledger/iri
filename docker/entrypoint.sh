#!/bin/bash
# See Dockerfile and DOCKER.md for further info

if [ "${DOCKER_IRI_MONITORING_API_PORT_ENABLE}" == "1" ]; then
  nohup socat -lm TCP-LISTEN:14266,fork TCP:127.0.0.1:${DOCKER_IRI_MONITORING_API_PORT_DESTINATION} &
fi

$neighbors
for buddy in $(cat /iri/conf/neighbors); do
  neighbors="$buddy $neighbors"
done
neighbors=${neighbors::-1}

exec java \
  $JAVA_OPTIONS \
  -Xms$JAVA_MIN_MEMORY \
  -Xmx$JAVA_MAX_MEMORY \
  -Djava.net.preferIPv4Stack=true \
  -jar $DOCKER_IRI_JAR_PATH \
  --remote --remote-limit-api "$DOCKER_IRI_REMOTE_LIMIT_API" \
  --port 14700 \
  --udp-receiver-port 13700 \
  --tcp-receiver-port 13700 \
  --testnet \
  --testnet-no-coo-validation \
  --mwm 1 \
  --walk-validator "NULL" \
  --ledger-validator "NULL" \
  --enable-streaming-graph \
  --entrypoint-selector-algorithm "KATZ" \
  --tip-sel-algo "CONFLUX" \
  --ipfs-txns false \
  --batch-txns true \
  --neighbors "$neighbors" \
  --max-peers 21
  "$@"
