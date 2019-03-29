#!/bin/bash

rm -rf cli.log
cp cli_conf ../../iota_api/conf
cd ../../iota_api/
gunicorn -w 4 app:app &> ../examples/one_node_batch/cli.log  &
