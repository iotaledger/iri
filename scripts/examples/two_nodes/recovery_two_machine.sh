#!/bin/bash

echo -e "\033[31m 1.2 single txn two machines locally ... \033[0m"

rm -rf iri-1.5.5.jar
cd ../../..
##mvn clean ; mvn package
cp target/iri-1.5.5.jar scripts/examples/two_nodes
cd scripts/examples/two_nodes
rm -rf db1*
rm -rf db2*
rm -rf ixi
rm -rf streamnet*a

pid=`ps aux | awk '/java/ && !/awk/ {print $2}'`
if [ -n "$pid" ]; then
  kill -9 $pid
fi

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

sleep 5

echo "reset cli ..."
cli_pid=`ps aux | awk '/python/ && /app.py/ && !/awk/ {print $2}'`
if [ -n "$cli_pid" ]; then
  kill -9 $cli_pid
fi;

rm -rf cli*.log
cd ../../iota_api/

cp ../examples/two_nodes/cli_conf_two_nodes_1 ./conf
python app.py &> ../examples/two_nodes/cli1.log  &

sleep 5

cp ../examples/two_nodes/cli_conf_two_nodes_2 ./conf
python app.py &> ../examples/two_nodes/cli2.log  &

cd ../examples/two_nodes

sleep 5

echo "put single txn to first machine ..."
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"a\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1

echo -e "\nbegin to collect data ... \n"
./get_balance_two_nodes.sh > 5000_balance_a.data

sleep 2

curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > 5000_total_order_a.data

curl -s -X GET http://127.0.0.1:5000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > 5000_dag_a.data


sed 's/5000/6000/g' get_balance_two_nodes.sh | tee 6000_get_balance.sh > /dev/null
./6000_get_balance.sh > 6000_balance_a.data

sleep 2

curl -s -X GET http://127.0.0.1:6000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > 6000_total_order_a.data

curl -s -X GET http://127.0.0.1:6000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > 6000_dag_a.data

echo -e "\ncheck diff between the two machines ..."
diff 5000_total_order_a.data 6000_total_order_a.data > two_machine.diff

diff 5000_balance_a.data 6000_balance_a.data >> two_machine.diff

diff 5000_dag_a.data 6000_dag_a.data >> two_machine.diff

if [ -s two_machine.diff ]; then
  cat two_machine.diff
else 
  echo -e "validate result : \033[32m PASSED \033[0m"
fi

pid=`ps aux | awk '/java/ && !/awk/ {print $2}'`
if [ -n "$pid" ]; then
  kill -9 $pid
fi
sleep 5

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

sleep 5

echo -e "\nbegin to collect data ... \n"
./get_balance_two_nodes.sh > 5000_balance_b.data

sleep 2

curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > 5000_total_order_b.data

curl -s -X GET http://127.0.0.1:5000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > 5000_dag_b.data


./6000_get_balance.sh > 6000_balance_b.data

sleep 2

curl -s -X GET http://127.0.0.1:6000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > 6000_total_order_b.data

curl -s -X GET http://127.0.0.1:6000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > 6000_dag_b.data

echo -e "\ncheck diff between the two machines ..."
diff 5000_total_order_a.data 6000_total_order_a.data > two_machine.diff

diff 5000_balance_a.data 6000_balance_a.data >> two_machine.diff

diff 5000_dag_a.data 6000_dag_a.data >> two_machine.diff

diff 5000_total_order_a.data 5000_total_order_b.data >> two_machine.diff

diff 5000_balance_a.data 5000_balance_b.data >> two_machine.diff

diff 5000_dag_a.data 5000_dag_b.data >> two_machine.diff

diff 6000_total_order_a.data 6000_total_order_b.data >> two_machine.diff

diff 6000_balance_a.data 6000_balance_b.data >> two_machine.diff

diff 6000_dag_a.data 6000_dag_b.data >> two_machine.diff



if [ -s two_machine.diff ]; then
  cat two_machine.diff
else
  echo -e "1.2 single txn two machines validate result : \033[32m PASSED \033[0m"
fi

echo "clean tmp files ..."
rm -r 5000_* 6000_balance_* 6000_total* 6000_dag* 

echo -e "\n\033[31m 2.2 multi txn two machines locally ... \033[0m"

rm -rf db1*
rm -rf db2*
rm -rf ixi
rm -rf streamnet*a

pid=`ps aux | awk '/java/ && !/awk/ {print $2}'`
if [ -n "$pid" ]; then
  kill -9 $pid
fi

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

sleep 5

echo "put multi txns to first machine ..."
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"a\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"b\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"c\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"d\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"m\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"n\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"a\",\"to\":\"e\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"a\",\"to\":\"f\",\"amnt\":1000,\"tag\":\"TX\"}" &
sleep 5

echo -e "\nbegin to collect data ... \n"
./get_balance_two_nodes.sh > 5000_balance_a.data

sleep 2

curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > 5000_total_order_a.data

curl -s -X GET http://127.0.0.1:5000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > 5000_dag_a.data


./6000_get_balance.sh > 6000_balance_a.data

sleep 2

curl -s -X GET http://127.0.0.1:6000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > 6000_total_order_a.data

curl -s -X GET http://127.0.0.1:6000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > 6000_dag_a.data

echo -e "\ncheck diff between the two machines ..."
diff 5000_total_order_a.data 6000_total_order_a.data > two_machine.diff

diff 5000_balance_a.data 6000_balance_a.data >> two_machine.diff

diff 5000_dag_a.data 6000_dag_a.data >> two_machine.diff

if [ -s two_machine.diff ]; then
  cat two_machine.diff
else 
  echo -e "check diff between two machines result : \033[32m PASSED \033[0m"
fi

pid=`ps aux | awk '/java/ && !/awk/ {print $2}'`
if [ -n "$pid" ]; then
  kill -9 $pid
fi

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

sleep 5

echo -e "\nbegin to collect data ... \n"
./get_balance_two_nodes.sh > 5000_balance_b.data

sleep 2

curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > 5000_total_order_b.data

curl -s -X GET http://127.0.0.1:5000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > 5000_dag_b.data


./6000_get_balance.sh > 6000_balance_b.data

sleep 2

curl -s -X GET http://127.0.0.1:6000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > 6000_total_order_b.data

curl -s -X GET http://127.0.0.1:6000/get_dag -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"type\": \"JSON\"}" > 6000_dag_b.data

echo -e "\ncheck diff between the two machines ..."
diff 5000_total_order_a.data 6000_total_order_a.data > two_machine.diff

diff 5000_balance_a.data 6000_balance_a.data >> two_machine.diff

diff 5000_dag_a.data 6000_dag_a.data >> two_machine.diff

diff 5000_total_order_a.data 5000_total_order_b.data >> two_machine.diff

diff 5000_balance_a.data 5000_balance_b.data >> two_machine.diff

diff 5000_dag_a.data 5000_dag_b.data >> two_machine.diff

diff 6000_total_order_a.data 6000_total_order_b.data >> two_machine.diff

diff 6000_balance_a.data 6000_balance_b.data >> two_machine.diff

diff 6000_dag_a.data 6000_dag_b.data >> two_machine.diff

if [ -s two_machine.diff ]; then
  cat two_machine.diff
else 
  echo -e "2.2 multi txns two machines validate result : \033[32m PASSED \033[0m"
fi
echo "clean tmp files ..."
rm -r 5000_* 6000_balance_* 6000_total* 6000_dag* two*.diff
