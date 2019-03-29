#!/bin/bash

rm -rf iri-1.5.5.jar
cd ../../..
mvn clean ; mvn package
cp target/iri-1.5.5.jar scripts/examples/two_nodes
cd scripts/examples/two_nodes
rm -rf db1*
rm -rf db2*
rm -rf ixi
rm -rf streamnet*

java -jar iri-1.5.5.jar --testnet \
                        --mwm 1 \
                        --walk-validator "NULL" \
                        --ledger-validator "NULL" \
                        -p 14700 \
                        --udp-receiver-port 14600 \
                        --tcp-receiver-port 14600 \
                        --db-path "./db1" \
                        --db-log-path "./db1.log" \
                        --neighbors "tcp://localhost:13600" \
                        --max-peers 40 \
                        --remote \
                        --enable-streaming-graph \
                        --entrypoint-selector-algorithm "KATZ" \
                        --tip-sel-algo "CONFLUX" \
                        --ipfs-txns false \
                        --batch-txns false \
                        --weight-calculation-algorithm "IN_MEM" \
                        &>  streamnet1.log &

java -jar iri-1.5.5.jar --testnet \
                        --mwm 1 \
                        --walk-validator "NULL" \
                        --ledger-validator "NULL" \
                        -p 13700 \
                        --udp-receiver-port 13600 \
                        --tcp-receiver-port 13600 \
                        --db-path "./db2" \
                        --db-log-path "./db2.log" \
                        --neighbors "tcp://localhost:14600" \
                        --max-peers 40 \
                        --remote \
                        --enable-streaming-graph \
                        --entrypoint-selector-algorithm "KATZ" \
                        --tip-sel-algo "CONFLUX" \
                        --ipfs-txns false \
                        --batch-txns false \
                        --weight-calculation-algorithm "IN_MEM" \
                        &>  streamnet2.log &
