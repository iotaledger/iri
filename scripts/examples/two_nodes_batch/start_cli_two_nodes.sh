#!/bin/bash

rm -rf cli*.log
cd ../../iota_api/

cp ../examples/two_nodes_batch/cli_conf_two_nodes_1 ./conf
python app.py &> ../examples/two_nodes_batch/cli1.log  &

sleep 5

cp ../examples/two_nodes_batch/cli_conf_two_nodes_2 ./conf
python app.py &> ../examples/two_nodes_batch/cli2.log  &

cd ../examples/two_nodes_batch/
