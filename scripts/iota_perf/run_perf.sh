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
./check_result.sh

# check order
curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > order1
curl -s -X GET http://127.0.0.1:6000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > order2
diff order1 order2
