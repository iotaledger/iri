#!/bin/bash

JM_HOME=/usr/local/apache-jmeter-5.1/bin/
echo "jmeter home " $JM_HOME
sudo cp ../../iota_deploy/*.py .
sudo cp ../../iota_perf/*.py .
sudo touch PerformanceTest.jmx
sudo touch PerformanceTest1.jmx
sudo chmod 777 PerformanceTest.jmx
sudo chmod 777 PerformanceTest1.jmx
sleep 60

# restart nginx
TOPOLOGY=$1
DATA=$2
TYPE=$3
if [ ${TOPOLOGY} != "4_circle" -a ${TOPOLOGY} != "4_clique" -a ${TOPOLOGY} != "7_circle" -a ${TOPOLOGY} != "7_clique" -a ${TOPOLOGY} != "7_bridge" -a ${TOPOLOGY} != "7_star" ];
then
    echo "configure 3" ${TOPOLOGY} ${DATA}
    sudo cp conf_info/nginx/aws/nginx3.conf /etc/nginx/nginx.conf
elif [ ${TOPOLOGY} != "3_clique" -a ${TOPOLOGY} != "7_circle" -a ${TOPOLOGY} != "7_clique" -a ${TOPOLOGY} != "7_bridge" -a ${TOPOLOGY} != "7_star" ];
then
    echo "configure 4 "${TOPOLOGY} ${DATA}
    sudo cp conf_info/nginx/aws/nginx4.conf /etc/nginx/nginx.conf
elif [ ${TOPOLOGY} != "4_clique" -a ${TOPOLOGY} != "4_circle" -a ${TOPOLOGY} != "3_clique" ];
then
    echo "configure 7" ${TOPOLOGY} ${DATA}
    sudo cp conf_info/nginx/aws/nginx7.conf /etc/nginx/nginx.conf
fi
sudo nginx -s stop
sleep 2
sudo nginx
# generate data
sudo python data_generate.py ${DATA}
echo "run bootstrapping"
sudo sed 's/NUM_CALL/500/g' PerformanceTestDAG2TM_TPS.jmx | sudo tee   PerformanceTest.jmx >  /dev/null
sudo sed 's/NUM_THREAD/1/g' PerformanceTest.jmx | sudo tee   PerformanceTest1.jmx > /dev/null
sudo sed 's/PORT/5001/g' PerformanceTest1.jmx | sudo tee   PerformanceTest.jmx > /dev/null
sudo sed 's/DATA/data/g' PerformanceTest.jmx | sudo tee   PerformanceTest1.jmx > /dev/null
sudo sed 's/put_cache/put_file/g' PerformanceTest1.jmx | sudo tee PerformanceTest.jmx > /dev/null
sudo ${JM_HOME}/jmeter -n -t PerformanceTest.jmx

echo "run experiment"
sudo sed 's/NUM_CALL/'${DATA}'/g' PerformanceTestDAG2TM_TPS.jmx | sudo tee   PerformanceTest.jmx >  /dev/null
sudo sed 's/NUM_THREAD/2/g' PerformanceTest.jmx | sudo tee   PerformanceTest1.jmx >  /dev/null
sudo sed 's/PORT/80/g' PerformanceTest1.jmx | sudo tee   PerformanceTest.jmx >  /dev/null
sudo sed 's/DATA/data1/g' PerformanceTest.jmx | sudo tee   PerformanceTest1.jmx >  /dev/null
sudo sed 's/put_cache/'${TYPE}'/g' PerformanceTest1.jmx | sudo tee PerformanceTest.jmx > /dev/null
sudo ${JM_HOME}/jmeter -n -t PerformanceTest.jmx
sleep 20
