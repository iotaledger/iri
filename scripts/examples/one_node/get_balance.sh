#!/bin/bash

total=0
for account in {a..z} {A..Z}
do
    value=$(curl -s -X GET http://127.0.0.1:8000/get_balance -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d "{\"account\": \"$account\"}")
    echo "$account:"${value}
    total=$((total+value))
done
echo "total:"${total}
