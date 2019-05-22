#!/bin/bash

JM_HOME=/usr/local/apache-jmeter-5.1/bin/

sudo cp ../../iota_deploy/*.py .
sudo cp ../../iota_perf/*.py .
sudo touch PerformanceTest.jmx
sudo touch PerformanceTest1.jmx
sudo chmod 777 PerformanceTest.jmx
sudo chmod 777 PerformanceTest1.jmx

TYPE=$3
echo $TYPE
for TOPOLOGY in 3_clique 4_circle 4_clique 7_circle 7_clique 7_bridge 7_star;
#for TOPOLOGY in 3_clique;
#for DATA in 2500 5000 7500 10000;
do
    for DATA in 2500 5000 7500 10000;
    #for TOPOLOGY in 7_bridge 7_star 7_clique;
    do
        # restart nginx
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

        sudo cp conf_info/ipinfo_aws.txt ./ipinfo.txt
        # generate data
        sudo python data_generate.py ${DATA}

        # configure iri
        python server_deploy_batch.py iri $1
        sleep 2

        # configure cli
        python server_deploy_batch.py cli $1 $2
        sleep 2

        # configure topology
        sudo cp conf_info/topology/aws/${TOPOLOGY} topology.txt
        python server_deploy_batch.py add
        sleep 60

        # run bootstrapping
        echo "run bootstrapping"
	ps -ef|grep jmeter | grep -v grep | awk "{print \$2}"|xargs kill -9
        sudo sed 's/NUM_CALL/500/g' PerformanceTestDAG2TM_TPS.jmx | sudo tee   PerformanceTest.jmx >  /dev/null
        sudo sed 's/NUM_THREAD/1/g' PerformanceTest.jmx | sudo tee   PerformanceTest1.jmx > /dev/null
        sudo sed 's/PORT/5001/g' PerformanceTest1.jmx | sudo tee   PerformanceTest.jmx > /dev/null
        sudo sed 's/DATA/data/g' PerformanceTest.jmx | sudo tee   PerformanceTest1.jmx > /dev/null
        sudo sed 's/put_cache/put_file/g' PerformanceTest1.jmx | sudo tee PerformanceTest.jmx > /dev/null
        sudo ${JM_HOME}/jmeter -n -t PerformanceTest.jmx
        sleep 10

        # run experiment
        echo "run experiment"
        sudo sed 's/NUM_CALL/'${DATA}'/g' PerformanceTestDAG2TM_TPS.jmx | sudo tee   PerformanceTest.jmx >  /dev/null
        sudo sed 's/NUM_THREAD/2/g' PerformanceTest.jmx | sudo tee   PerformanceTest1.jmx >  /dev/null
        sudo sed 's/PORT/80/g' PerformanceTest1.jmx | sudo tee   PerformanceTest.jmx >  /dev/null
        sudo sed 's/DATA/data1/g' PerformanceTest.jmx | sudo tee   PerformanceTest1.jmx >  /dev/null
        sudo sed 's/put_cache/'${TYPE}'/g' PerformanceTest1.jmx | sudo tee PerformanceTest.jmx > /dev/null
        sudo ${JM_HOME}/jmeter -n -t PerformanceTest.jmx
        sleep 2


        # configure iri
        python server_deploy_batch.py clear $1
        sleep  300
    done
done
