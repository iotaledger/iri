#!/usr/bin/env bash

# start iota

# start cli

# start nginx
#sudo cp conf_info/nginx/aws/nginx3.conf /etc/nginx/nginx.conf

# generate data
python data_generate.py 10000

# run ramp up
sed -e 's/NUM_CALL/500/g'       \
    -e 's/NUM_THREAD/1/g'       \
    -e 's/PORT/5000/g'          \
    -e 's/DATA/data/g'          \
    -e 's/ACTION/put_file/g'   PerformanceTestDAG2TM_TPS.jmx > PerformanceTest.jmx
jmeter -n -t PerformanceTest.jmx

sleep 10

# run perf with multi-threading
sed -e 's/NUM_CALL/5000/g'  \
    -e 's/NUM_THREAD/2/g'   \
    -e 's/PORT/8080/g'      \
    -e 's/DATA/data1/g'     \
    -e 's/ACTION/put_cache/g' PerformanceTestDAG2TM_TPS.jmx > PerformanceTest1.jmx
jmeter -n -t PerformanceTest1.jmx

sleep 30

# check balance
result=$(sh ./check_result.sh)
if [ $result -eq 5000000 ]; then
	echo "Result is right"
else
	echo "Wrong! Result should be 5,000,000, but now it's $result"
	exit -1
fi

# check order
curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > order1
curl -s -X GET http://127.0.0.1:6000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > order2
diff order1 order2
if [ $? -eq 0 ]; then
	echo "Orders from 2 nodes are the same."
else
	echo "Wrong! Orders from 2 nodes are different!"
	echo "Order 1"
	cat order1
	echo
	echo "Order 2"
	cat order2

	exit -1
fi
