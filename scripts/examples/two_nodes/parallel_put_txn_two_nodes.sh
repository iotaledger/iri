#!/bin/bash
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"a\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"b\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"c\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"d\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"m\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"A\",\"to\":\"n\",\"amnt\":10000,\"tag\":\"TX\"}"
sleep 1
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"a\",\"to\":\"e\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"a\",\"to\":\"f\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"a\",\"to\":\"g\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"a\",\"to\":\"h\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"a\",\"to\":\"i\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"b\",\"to\":\"e\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"b\",\"to\":\"f\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"b\",\"to\":\"g\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"b\",\"to\":\"h\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"b\",\"to\":\"i\",\"amnt\":1000,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"u\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"v\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"w\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"x\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"y\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"c\",\"to\":\"z\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"u\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"v\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"w\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"x\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"d\",\"to\":\"y\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"x\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"y\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"z\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"B\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"C\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"D\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"E\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"F\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"G\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"H\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"m\",\"to\":\"I\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"n\",\"to\":\"J\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"n\",\"to\":\"K\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"n\",\"to\":\"L\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"n\",\"to\":\"M\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"n\",\"to\":\"N\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"n\",\"to\":\"O\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"n\",\"to\":\"P\",\"amnt\":10,\"tag\":\"TX\"}" &
curl -s http://localhost:6000/put_file -X POST -H "Content-Type: application/json" -d "{\"from\":\"n\",\"to\":\"Q\",\"amnt\":10,\"tag\":\"TX\"}" &
