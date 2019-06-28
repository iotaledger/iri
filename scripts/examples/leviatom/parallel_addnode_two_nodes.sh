#!/bin/bash
curl -s http://localhost:8001/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"D\",\"Attestee\":\"A\",\"Score\":\"1\"}"
sleep 1
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"D\",\"Attestee\":\"B\",\"Score\":\"1\"}"
sleep 1
curl -s http://localhost:8001/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"E\",\"Attestee\":\"D\",\"Score\":\"1\"}" &
curl -s http://localhost:8001/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"E\",\"Attestee\":\"B\",\"Score\":\"1\"}" &
curl -s http://localhost:8001/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"E\",\"Attestee\":\"F\",\"Score\":\"1\"}" &
curl -s http://localhost:8001/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"F\",\"Attestee\":\"E\",\"Score\":\"1\"}" &
curl -s http://localhost:8001/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"B\",\"Attestee\":\"C\",\"Score\":\"1\"}" &
curl -s http://localhost:8001/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"C\",\"Attestee\":\"B\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"a\",\"Attestee\":\"B\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"a\",\"Attestee\":\"E\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"b\",\"Attestee\":\"B\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"b\",\"Attestee\":\"E\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"c\",\"Attestee\":\"B\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"c\",\"Attestee\":\"E\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"d\",\"Attestee\":\"E\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"e\",\"Attestee\":\"E\",\"Score\":\"1\"}" &
curl -s http://localhost:8002/AddNode -X POST -H "Content-Type: application/json" -d "{\"Attester\":\"F\",\"Attestee\":\"B\",\"Score\":\"1\"}" &
