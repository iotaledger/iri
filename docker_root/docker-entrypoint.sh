#!/bin/bash

cat > /opt/iri/iri.ini <<EOL
[IRI]
PORT = ${API_PORT}
UDP_RECEIVER_PORT = ${UDP_PORT}
TCP_RECEIVER_PORT = ${TCP_PORT}
NEIGHBORS = ${NEIGHBORS}
IXI_DIR = ${IXI_DIR}
HEADLESS = ${HEADLESS}
DEBUG = ${DEBUG}
TESTNET = ${TESTNET}
DB_PATH = ${DB_PATH}
EOL

cd /opt/iri

chown -R iota:iota /opt/iri/data

exec runuser -l iota -c "java \
  $JAVA_OPTIONS \
  -Djava.net.preferIPv4Stack=true \
  -Dlogback.configurationFile=/opt/iri/logback.xml \
  -jar /opt/iri/iri.jar \
  --config /opt/iri/iri.ini \
  --remote --remote-limit-api \"$REMOTE_API_LIMIT\" \
  \"$@\""
