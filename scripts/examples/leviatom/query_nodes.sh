#!/bin/bash
curl -s http://localhost:8001/QueryNodes -X POST -H "Content-Type: application/json" -d "{\"period\":1,\"numRank\":100}"
curl -s http://localhost:8002/QueryNodes -X POST -H "Content-Type: application/json" -d "{\"period\":1,\"numRank\":100}"
