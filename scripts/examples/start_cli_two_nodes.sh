#!/bin/bash

rm cli*.log
cd ../iota_api/

cp ../examples/cli_conf_two_nodes_1 ../iota_api/conf
python app.py &> ../examples/cli1.log  &

sleep 5

cp ../examples/cli_conf_two_nodes_2 ../iota_api/conf
python app.py &> ../examples/cli2.log  &
