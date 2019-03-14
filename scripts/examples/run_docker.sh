#!/bin/bash

SCRIPTPATH=$(cd "$(dirname "$0")"; pwd)
NAME=iota-node
TAG=v0.1-streamnet

rm -rf data
rm -rf conf
mkdir data
mkdir conf
touch conf/neighbors
cd ../../

docker build -t ${NAME}:${TAG} .

sudo docker run -d -p 14700:14700 -p 13700:13700 --name ${NAME} -v ${SCRIPTPATH}/data:/iri/data -v ${SCRIPTPATH}/conf/neighbors:/iri/conf/neighbors ${NAME}:${TAG} /entrypoint.sh
# sudo docker run -d -p 5000:5000 --name iota-cli iota-cli:v0.1-streamnet /docker-entrypoint.sh
