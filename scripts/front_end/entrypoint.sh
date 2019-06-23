#!/bin/bash

java -jar /opt/trias/oauth/client/oauth-resource-1.0-SNAPSHOT.jar &
java -jar /opt/trias/oauth/server/oauth-server-1.0-SNAPSHOT.jar  &
nginx &
go run main.go
