#!/bin/bash
curl -k -s https://www.streamnet-chain.com/put_file -X POST -H "Content-Type: application/json" -d "{\"project\":\"diamond\",\"key\":\"zhaoming-vip\",\"value\":\"xx\",\"tag\":\"KV\"}"
sleep 1
curl -k -s https://www.streamnet-chain.com/put_file -X POST -H "Content-Type: application/json" -d @data.json 
