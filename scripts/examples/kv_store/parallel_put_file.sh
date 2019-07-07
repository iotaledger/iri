#!/bin/bash
curl -s http://localhost:5000/put_file -X POST -H "Content-Type: application/json" -d "{\"project\":\"diamond\",\"key\":\"zhaoming-vip\",\"value\":\"xx\",\"tag\":\"KV\"}"
