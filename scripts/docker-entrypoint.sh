#!/bin/sh

ipfs init
ipfs daemon &
cd /code/iota_api/
python app.py & >applog &
python chronic_txn_sync.py &> sync.log &

