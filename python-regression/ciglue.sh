#!/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Please specify an image to test"
    exit 1
fi

set -x

UUID=$(uuidgen)
ERROR=0
K8S_NAMESPACE=$(kubectl config get-contexts $(kubectl config current-context) | tail -n+2 | awk '{print $5}')
IMAGE=$1

if [ ! -d tiab ]; then
  git clone --depth 1 https://github.com/iotaledger/tiab tiab
fi

virtualenv -p python2 venv
source venv/bin/activate

cd tiab
git pull
echo "tiab revision: "; git rev-parse HEAD
pip install -r requirements.txt
cd ..

pip install -e .

for machine_dir in tests/features/machine?; do
  python tiab/create_cluster.py -i $IMAGE -t $UUID -n $K8S_NAMESPACE -c $machine_dir/config.yml -o $machine_dir/output.yml -d
  if [ $? -ne 0 ]; then
    ERROR=1
    python <<EOF
import yaml
for (key,value) in yaml.load(open('$machine_dir/output.yml'))['nodes'].iteritems():
  if value['status'] == 'Error':
    print value['log']
EOF
  fi
done

if [ $ERROR -eq 0 ]; then
  echo "Starting tests..." 
  for machine_dir in tests/features/machine?;do
    FEATURES=$(find $machine_dir -type f -name "*.feature" -exec basename {} \; | tr "\n" " ")
    aloe $FEATURES --verbose --nologcapture --where $machine_dir
  done
fi

timeout 10 tiab/teardown_cluster.py -t $UUID -n $K8S_NAMESPACE

deactivate

exit $ERROR
