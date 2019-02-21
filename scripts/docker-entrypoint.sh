#!/bin/sh
source /venv/bin/activate
ipfs init
ipfs daemon &> /iota_cache/ipfslog &
cd /code/iota_api/
python app.py & >applog &
#
python chronic_txn_sync.py &> sync.log &

