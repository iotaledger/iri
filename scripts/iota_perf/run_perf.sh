# deploy environment (specifically nginx)
#sudo cp conf_info/nginx/aws/nginx3.conf /etc/nginx/nginx.conf

# generate data
python data_generate.py 10000

# run ramp up
sed 's/NUM_CALL/500/g' PerformanceTestDAG2TM_TPS.jmx | tee   PerformanceTest.jmx >  /dev/null
sed 's/NUM_THREAD/1/g' PerformanceTest.jmx | tee   PerformanceTest1.jmx > /dev/null
sed 's/PORT/5000/g' PerformanceTest1.jmx | tee   PerformanceTest.jmx > /dev/null
sed 's/DATA/data/g' PerformanceTest.jmx | tee   PerformanceTest1.jmx > /dev/null
sed 's/ACTION/put_file/g' PerformanceTest1.jmx | tee PerformanceTest.jmx > /dev/null
jmeter -n -t PerformanceTest.jmx

sleep 10

# run perf with multi-threading
sed 's/NUM_CALL/5000/g' PerformanceTestDAG2TM_TPS.jmx | tee   PerformanceTest.jmx >  /dev/null
sed 's/NUM_THREAD/2/g' PerformanceTest.jmx | tee   PerformanceTest1.jmx > /dev/null
sed 's/PORT/8080/g' PerformanceTest1.jmx | tee   PerformanceTest.jmx > /dev/null
sed 's/DATA/data1/g' PerformanceTest.jmx | tee   PerformanceTest1.jmx > /dev/null
sed 's/ACTION/put_cache/g' PerformanceTest1.jmx | tee PerformanceTest.jmx > /dev/null
jmeter -n -t PerformanceTest.jmx

sleep 30

# check balance
./check_result.sh

# check order
curl -s -X GET http://127.0.0.1:5000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > order1
curl -s -X GET http://127.0.0.1:6000/get_total_order -H 'Content-Type: application/json' -H 'cache-control: no-cache' > order2
diff order1 order2
