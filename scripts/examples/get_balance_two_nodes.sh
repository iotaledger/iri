#!/bin/bash

curl http://localhost:5000/get_balance -X GET -H "Content-Type: application/json"
curl http://localhost:6000/get_balance -X GET -H "Content-Type: application/json"
