#!/bin/bash

echo -e "\033[31m *******[recovery in single machine]******** \033[0m"

echo -e "\033[31m 1. Single txn on machine locally \033[0m"

echo "reset cli ..."

cli_pid=`ps aux|awk '/python/ && /app.py/ && !/awk/ {print $2}'`
if [ -n "$cli_pid" ]; then
  kill -9 $cli_pid
fi

rm -rf cli.log
cp cli_conf ../../iota_api/conf
cd ../../iota_api/
pyton app.py &> ../examples/one_node/cli.log  &
cd ../examples/one_node
sleep 5

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
diff balance_a.data balance_b.data > one_machine.diff 

echo "compare total order"
diff total_order_a.data total_order_b.data >> one_machine.diff

echo "compare dag"
diff dag_a.data dag_b.data >> one_machine.diff

if [ -s  one_machine.diff ]; then
  cat one_machine.diff
else
  echo -e "1.1 single txn one machine validate: \033[32m PASSED \033[0m"
fi

echo "clean tmp file"
rm -r balance_* total_order_* dag_* one_machine.diff



echo ""
echo -e "\033[31m 2.1 multi txn one machine locally \033[0m"

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
diff balance_a.data balance_b.data > one_machine.diff

echo "compare total order"
diff total_order_a.data total_order_b.data >> one_machine.diff

echo "compare dag"
diff dag_a.data dag_b.data >> one_machine.diff

if [ -s  one_machine.diff ]; then
  cat one_machine.diff
else
  echo -e "2.1 multi txns one machine validate: \033[32m PASSED \033[0m"
fi

echo "clean up tmp file ..."
rm -r balance_* total_order_* dag_*

