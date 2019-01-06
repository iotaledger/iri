kill $(ps aux | grep "java \-jar" | grep -v grep | awk "{print \$2}")
rm -rf testnetdb
rm -rf testnetdb.log
sleep 5
rm /home/ubuntu/iota/run-time/out
java -jar /home/ubuntu/iota/run-time/iri-1.5.5.jar --testnet --testnet-no-coo-validation --snapshot=/home/ubuntu/iota/run-time/Snapshot.txt --mwm 1 --walk-validator "NULL" --ledger-validator "NULL" --enable-wasm true -p 14700 --max-peers 40 --remote &>  /home/ubuntu/iota/run-time/out &

source /home/ubuntu/iota/run-time/iota_cache/venv/bin/activate
cd /home/ubuntu/iota/run-time/iota_cache

# start ipfs daemon
ipfs daemon &> /home/ubuntu/iota/run-time/ipfslog &
sleep 5

# start the API server
# If we redirect IPFS load to other machines, TPS will be increased
kill $(ps aux | grep "app.py" | grep -v grep | awk "{print \$2}")
ps aux | grep "gunicorn" | grep -v grep | awk "{print \$2}" | xargs kill -9
rm /home/ubuntu/iota/run-time/out1
python /home/ubuntu/iota/run-time/iota_cache/app.py &> /home/ubuntu/iota/run-time/out1 &
#gunicorn -w 4 app:app &> /home/ubuntu/iota/run-time/out1 &

# start the sync server
kill $(ps aux | grep "chronic_txn_sync.py" | grep -v grep | awk "{print \$2}")
rm /home/ubuntu/iota/run-time/out2
python chronic_txn_sync.py &> /home/ubuntu/iota/run-time/out2 &

# TODO we need to have a coordinator server as well
#sleep 10
# java -jar /home/ubuntu/zhaoming/gitlocal/private-iota-testnet/target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar Coordinator localhost 14700
