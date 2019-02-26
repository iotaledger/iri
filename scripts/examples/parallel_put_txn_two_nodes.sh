#!/bin/bash
curl http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"a\",\"amnt\":10,\"tag\":\"TX\"}"
sleep 1
curl http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"b\",\"amnt\":10,\"tag\":\"TX\"}"
sleep 1
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"c\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"d\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"e\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"f\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"g\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"h\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"i\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"j\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"k\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"l\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"m\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"n\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"o\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"p\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"q\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"r\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"s\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"t\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"u\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:5000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"v\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"w\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"x\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"y\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"z\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"B\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"C\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"D\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"E\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"F\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"G\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"H\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"I\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"J\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"K\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"L\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"M\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"N\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"O\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"P\",\"amnt\":10,\"tag\":\"TX\"}" &
curl http://localhost:6000/put_cache -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"Q\",\"amnt\":10,\"tag\":\"TX\"}" &
