#!/bin/bash

starttime=`date +'%Y-%m-%d %H:%M:%S'`
TOPOLOGY=$1
echo $TOPOLOGY
sudo cp ../../iota_deploy/server_deploy_batch.py ./
sudo cp ../../iota_deploy/add_neighbors_batch.py ./
sudo cp conf_info/ipinfo_aws.txt ./ipinfo.txt
sudo cp conf_info/topology/aws/${TOPOLOGY} topology.txt
python server_deploy_batch.py iri $2
python server_deploy_batch.py cli $2 $3
python server_deploy_batch.py add
sleep 10
endtime=`date +'%Y-%m-%d %H:%M:%S'`
start_seconds=$(date --date="$starttime" +%s);
end_seconds=$(date --date="$endtime" +%s);
echo "run time: "$((end_seconds-start_seconds))"s"
