#!/bin/bash
curl -s http://localhost:8001/QueryNodes -X POST -H "Content-Type: application/json" -d "{\"period\":1,\"numRank\":100}" > out1
curl -s http://localhost:8002/QueryNodes -X POST -H "Content-Type: application/json" -d "{\"period\":1,\"numRank\":100}" > out2

diff out1 out2
if [ $? == 0 ]; then
 echo "same"
else
 echo "different"
fi
