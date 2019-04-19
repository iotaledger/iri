#!/bin/bash

curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"b\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"c\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"d\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"e\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:8000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"f\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"g\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"h\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"i\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"j\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"k\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"l\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"m\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"n\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"o\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"p\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"q\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"r\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"s\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"t\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"u\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"v\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"w\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"x\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"y\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"z\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"B\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"C\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"D\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"E\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"F\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"G\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"H\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"I\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"J\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"K\",\"amnt\":1000,\"tag\":\"TX\"}" &

curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"f\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"g\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"h\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"i\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"j\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"k\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"l\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"m\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"n\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"o\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"p\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"q\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"r\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"s\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"t\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"u\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"v\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"w\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"x\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"y\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"z\",\"amnt\":100,\"tag\":\"TX\"}" &

curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"f\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"g\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"h\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"i\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"j\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"k\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"l\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"m\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"n\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"o\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"p\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"q\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"r\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"s\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"t\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"u\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"v\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"w\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"x\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"y\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"z\",\"amnt\":100,\"tag\":\"TX\"}" &

curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"F\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"G\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"H\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"I\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"J\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"K\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"L\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"M\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"N\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"O\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"P\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"Q\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"R\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"S\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"T\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"U\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"V\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"W\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"X\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"Y\",\"amnt\":100,\"tag\":\"TX\"}" &
curl -s http://localhost:8000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"e\",\"to\":\"Z\",\"amnt\":100,\"tag\":\"TX\"}" &
