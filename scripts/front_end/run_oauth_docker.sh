#!/bin/bash

OAUTH_NAME=oauth
TAG=v0.1-streamnet
sudo docker build -t ${OAUTH_NAME}:${TAG} --build-arg HOST_IP=$1 .
sudo docker run -itd -p 80:80 -p 8000:8000 -p 9081:9081 -p 9080:9080  --name ${OAUTH_NAME} ${OAUTH_NAME}:${TAG}
cd trias-oauth/
MYSQL_NAME=mysql
sudo docker build -t ${MYSQL_NAME}:${TAG} .
sudo docker run -itd -p 3306:3306 --name ${MYSQL_NAME} ${MYSQL_NAME}:${TAG}
