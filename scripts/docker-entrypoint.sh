#!/bin/sh

ipfs daemon &
sleep 10
cd /code/iota_api/
nohup  python chronic_txn_sync.py > synclog 2>&1 &
python app.py
