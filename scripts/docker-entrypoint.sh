#!/bin/sh

ipfs daemon &
sleep 10
cd /code/iota_api/
python modify_conf_file.py modify_iota_cli_conf
nohup  python chronic_txn_sync.py > synclog 2>&1 &
python app.py
