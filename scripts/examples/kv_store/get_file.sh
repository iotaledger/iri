#!/bin/bash
curl -s http://localhost:5000/get_file -X GET -H "Content-Type: application/json" -d "{\"project\":\"diamond\",\"key\":\"zhaoming-vip\",\"tag\":\"KV\"}"
