#!/bin/bash

rm -rf cli*.log
cd ../../iota_api/

cp ../examples/kv_store/cli_conf_two_nodes_1 ./conf
python app.py &> ../examples/kv_store/cli1.log  &

sleep 5

cp ../examples/kv_store/cli_conf_two_nodes_2 ./conf
python app.py &> ../examples/kv_store/cli2.log  &

cd ../examples/kv_store/
