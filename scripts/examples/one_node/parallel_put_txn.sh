#!/bin/bash

curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"b\",\"amnt\":100,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"c\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"d\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"e\",\"amnt\":100,\"tag\":\"TX\"}" &

curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"f\",\"amnt\":100,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"g\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"h\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"i\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"j\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"k\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"l\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"m\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"n\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"o\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"p\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"q\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"r\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"s\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"t\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"u\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"v\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"w\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"x\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"y\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"z\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"B\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"C\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"D\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"E\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"F\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"G\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"H\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"I\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"J\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"K\",\"amnt\":100,\"tag\":\"TX\"}" &
