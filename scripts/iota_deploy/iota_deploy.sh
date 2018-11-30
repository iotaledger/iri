#!/bin/bash

IRI_JAR=iri-1.5.4.jar
DAG1=13.229.64.41
DAG2=3.0.95.215
DAG3=18.136.201.52
DAG4=54.169.148.38

# start the genesis node
pssh  -i  -H ubuntu@${DAG1} -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'kill $(ps aux | grep "java \-jar" | grep -v grep | awk "{print \$2}")'
pssh  -i  -H ubuntu@${DAG1} -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'java -jar ~/iota/run-time/iri-1.5.4.jar --testnet --testnet-no-coo-validation --snapshot=/home/ubuntu/iota/run-time/Snapshot.txt -p 14700 --max-peers 40 --remote &'

# deploy in each machine
pssh  -i  -h pssh_hosts -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'mkdir -p ~/iota/run_time/data; touch ~/iota/run_time/neighbors'
pssh  -i  -h pssh_hosts -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'sudo rm -rf ~/iota/run_time/data/*;'
pssh  -i  -h pssh_hosts -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'sudo docker stop iota-node'
pssh  -i  -h pssh_hosts -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'sudo docker rm iota-node'
pssh  -i  -h pssh_hosts -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'echo udp://172.31.16.65:14600 > ~/iota/run_time/neighbors'
pssh  -i  -H ubuntu@${DAG1} -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'echo udp://3.0.95.215:13600 >> ~/iota/run_time/neighbors ; echo udp://18.136.201.52:13600 >> ~/iota/run_time/neighbors ;'
pssh  -i  -H ubuntu@${DAG2} -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'echo udp://13.229.64.41:13600 >> ~/iota/run_time/neighbors ; echo udp://54.169.148.38:13600 >> ~/iota/run_time/neighbors ;' 
pssh  -i  -H ubuntu@${DAG3} -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'echo udp://13.229.64.41:13600 >> ~/iota/run_time/neighbors ; echo udp://54.169.148.38:13600 >> ~/iota/run_time/neighbors ;'
pssh  -i  -H ubuntu@${DAG4} -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" 'echo udp://3.0.95.215:13600 >> ~/iota/run_time/neighbors ; echo udp://18.136.201.52:13600 >> ~/iota/run_time/neighbors ;'

for HOST in ${DAG1} ${DAG2} ${DAG3} ${DAG4}
do
    pssh  -i  -H ubuntu@${HOST} -x "-oStrictHostKeyChecking=no  -i ~/gitlocal/dag.pem" \
    'sudo docker run -d --net=host \
                       --name iota-node \
                       -e API_PORT=13700 \
                       -e UDP_PORT=13600 \
                       -e TCP_PORT=13600 \
                       -v /home/ubuntu/iota/run_time/data1:/iri/data \
                       -v /home/ubuntu/iota/run_time/neighbors:/iri/conf/neighbors \
                       irvine/iota-node:test_v1 \
                       /docker-entrypoint.sh'
done
