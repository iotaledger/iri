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

docker run -d --net=host --name ${NAME} -v ${SCRIPTPATH}/data:/iri/data -v ${SCRIPTPATH}/conf/neighbors:/iri/conf/neighbors ${NAME}:${TAG} /entrypoint.sh
