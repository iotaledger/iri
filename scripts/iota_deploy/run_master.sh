kill $(ps aux | grep "java \-jar" | grep -v grep | awk "{print \$2}")
rm -rf testnetdb
rm -rf testnetdb.log
sleep 5
rm /home/ubuntu/iota/run-time/out
java -jar /home/ubuntu/iota/run-time/iri-1.5.5.jar --testnet --testnet-no-coo-validation --snapshot=/home/ubuntu/iota/run-time/Snapshot.txt -p 14700 --max-peers 40 --remote &>  /home/ubuntu/iota/run-time/out &

source /home/ubuntu/iota/run-time/iota_cache/venv/bin/activate
cd /home/ubuntu/iota/run-time/iota_cache

# start the API server
kill $(ps aux | grep "app.py" | grep -v grep | awk "{print \$2}")
rm /home/ubuntu/iota/run-time/out1
# python /home/ubuntu/iota/run-time/iota_cache/app.py &> /home/ubuntu/iota/run-time/out1 &
gunicorn -w 2 app:app &> /home/ubuntu/iota/run-time/out1 &

# start the sync server
kill $(ps aux | grep "chronic_txn_sync.py" | grep -v grep | awk "{print \$2}")
rm /home/ubuntu/iota/run-time/out2
python chronic_txn_sync.py &> /home/ubuntu/iota/run-time/out2 & 

# TODO we need to have a coordinator server as well
#sleep 10
# java -jar /home/ubuntu/zhaoming/gitlocal/private-iota-testnet/target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar Coordinator localhost 14700
