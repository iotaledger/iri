#!/bin/bash
[ -z "$PORT" ]	&& p="-p 14265" || p="-p $PORT"
[ -z "$NEIGHBORS" ]  || n="-n $NEIGHBORS"
[ -z "$CONFIG" ]  || c="-c $CONFIG"
[ -z "$UDP_RECEIVER_PORT" ]  || u="-u $UDP_RECEIVER_PORT"
[ -z "$TCP_RECEIVER_PORT" ]  || t="-t $TCP_RECEIVER_PORT"
[ -z "$TESTNET" ]  || testnet="--testnet"
[ -z "$REMOTE" ]  || remote="--remote"
[ -z "$REMOTE_AUTH" ]  || remote_auth="--remote-auth $REMOTE_AUTH"
[ -z "$REMOTE_LIMIT_API" ]  && remote_limit_api="--remote-limit-api \"addNeighbors, removeNeighbors, getNeighbors\"" || remote_limit_api="--remote-limit-api $REMOTE_LIMIT_API"
[ -z "$SEND_LIMIT" ]  || send_limit="--send-limit $SEND_LIMIT"
[ -z "$MAX_PEERS" ]  || max_peers="--max-peers $MAX_PEERS"
[ -z "$DNS_RESOLUTION_FALSE" ]  || dns_resolution_false="--dns-resolution-false"

java -XX:+DisableAttachMechanism -Xmx8g -Xms256m -Dlogback.configurationFile=/iri/conf/logback.xml -Djava.net.preferIPv4Stack=true -jar iri.jar $p $u $t $n $c $testnet $remote $remote_auth $remote_limit_api $send_limit $maxp_peers $dns_resolution_false $@
