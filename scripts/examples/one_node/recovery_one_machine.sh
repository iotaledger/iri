#!/bin/bash

echo "*******[recovery in single machine]********"

echo "1. Single txn on machine locally"

iri_pid=`ps aux|awk '/java/ && !/awk/ {print $2}'`
if [ -n "$iri_pid" ]; then
  kill -9 $iri_pid
fi;

rm -rf iri-1.5.5.jar
cd ../../..
mvn clean ; mvn package
cp target/iri-1.5.5.jar scripts/examples/one_node
cd scripts/examples/one_node
rm -rf testnetdb*
rm -rf ixi
rm -rf streamnet*

java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=6000,suspend=n -jar iri-1.5.5.jar --testnet \
                        --mwm 1 \
                        --walk-validator "NULL" \
                        --ledger-validator "NULL" \
                        -p 14700 \
                        --max-peers 40 \
                        --remote \
                        --enable-streaming-graph \
                        --entrypoint-selector-algorithm "KATZ" \
                        --tip-sel-algo "CONFLUX" \
                        --ipfs-txns false \
                        --batch-txns false \
                        --weight-calculation-algorithm "IN_MEM" \
                        &>  streamnet1.log &

sleep 2

echo "add one txn ... "
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"f\",\"amnt\":100,\"tag\":\"TX\"}"

echo "begin to collect data ... "

./get_balance.sh > balance_a.data

sleep 2

curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > total_order_a.data

curl -s -X GET http://127.0.0.1:5000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > dag_a.data


echo "restart server ... "

iri_pid=`ps aux|awk '/java/ && !/awk/ {print $2}'`
if [ -n "$iri_pid" ]; then
  kill -9 $iri_pid
fi;

java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=6000,suspend=n -jar iri-1.5.5.jar --testnet \
                        --mwm 1 \
                        --walk-validator "NULL" \
                        --ledger-validator "NULL" \
                        -p 14700 \
                        --max-peers 40 \
                        --remote \
                        --enable-streaming-graph \
                        --entrypoint-selector-algorithm "KATZ" \
                        --tip-sel-algo "CONFLUX" \
                        --ipfs-txns false \
                        --batch-txns false \
                        --weight-calculation-algorithm "IN_MEM" \
                        &>  streamnet2.log &

sleep 2

echo "begin to collect data ..."

curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > total_order_b.data

curl -s -X GET http://127.0.0.1:5000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > dag_b.data

./get_balance.sh > balance_b.data

sleep 2

echo "compare balance between the two execution:"
diff balance_a.data balance_b.data 

echo "compare total order"
diff total_order_a.data total_order_b.data

echo "compare dag"
diff dag_a.data dag_b.data

echo "clean tmp file"
rm -r balance_* total_order_* dag_*

echo ""
echo "2.1 multi txn one machine locally"

iri_pid=`ps aux|awk '/java/ && !/awk/ {print $2}'`
if [ -n "$iri_pid" ]; then
  kill -9 $iri_pid
fi;

rm -rf testnetdb*
rm -rf ixi
rm -rf streamnet*

java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=6000,suspend=n -jar iri-1.5.5.jar --testnet \
                        --mwm 1 \
                        --walk-validator "NULL" \
                        --ledger-validator "NULL" \
                        -p 14700 \
                        --max-peers 40 \
                        --remote \
                        --enable-streaming-graph \
                        --entrypoint-selector-algorithm "KATZ" \
                        --tip-sel-algo "CONFLUX" \
                        --ipfs-txns false \
                        --batch-txns false \
                        --weight-calculation-algorithm "IN_MEM" \
                        &>  streamnet1.log &

sleep 2

echo "add multi txns ... "
./parallel_put_txn.sh
sleep 5

echo "begin to collect data ... "

./get_balance.sh > balance_a.data

sleep 2

curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > total_order_a.data

curl -s -X GET http://127.0.0.1:5000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > dag_a.data


echo "restart server ... "

iri_pid=`ps aux|awk '/java/ && !/awk/ {print $2}'`
if [ -n "$iri_pid" ]; then
  kill -9 $iri_pid
fi;

java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=6000,suspend=n -jar iri-1.5.5.jar --testnet \
                        --mwm 1 \
                        --walk-validator "NULL" \
                        --ledger-validator "NULL" \
                        -p 14700 \
                        --max-peers 40 \
                        --remote \
                        --enable-streaming-graph \
                        --entrypoint-selector-algorithm "KATZ" \
                        --tip-sel-algo "CONFLUX" \
                        --ipfs-txns false \
                        --batch-txns false \
                        --weight-calculation-algorithm "IN_MEM" \
                        &>  streamnet2.log &

sleep 2

echo "begin to collect data ..."

curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > total_order_b.data

curl -s -X GET http://127.0.0.1:5000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > dag_b.data

./get_balance.sh > balance_b.data

sleep 2

echo "compare balance between the two execution:"
diff balance_a.data balance_b.data 

echo "compare total order"
diff total_order_a.data total_order_b.data

echo "compare dag"
diff dag_a.data dag_b.data

echo "clean tmp file"
rm -r balance_* total_order_* dag_*

