import sys
import os
from subprocess import call

# example usage:
# python iota_deploy.py pssh_hosts 13.229.64.41 14700 iri-1.5.5.jar

ipfile       = open(sys.argv[1])
master       = sys.argv[2]
master_port  = sys.argv[3]
iri_jar      = sys.argv[4]

ips = ipfile.readlines()

ip_pubs = []
ip_pvts = []
pvt_ip_master=''
for ip_all in ips:
    ip_pub = ip_all.split(',')[0]
    ip_pvt = ip_all.split(',')[1].strip()
    if ip_pub == master:
        pvt_ip_master = ip_pvt
    ip_pubs.append(ip_pub)
    ip_pvts.append(ip_pvt)

print ip_pubs, ip_pvts

for i in range(0, len(ip_pubs)):
    ip_pub = ip_pubs[i]
    ip_pvt = ip_pvts[i]
    if ip_pub == master:
        call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
                'kill $(ps aux | grep "java \-jar" | grep -v grep | awk "{print \$2}")'])
        call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
                'java -jar ~/iota/run-time/' + iri_jar + ' --testnet --testnet-no-coo-validation --snapshot=/home/ubuntu/iota/run-time/Snapshot.txt -p '+ master_port + ' --max-peers 40 --remote &'])
    call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
            'mkdir -p ~/iota/run_time/data; touch ~/iota/run_time/neighbors'])
    call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
            'sudo rm -rf ~/iota/run_time/data/*;'])
    call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
            'sudo docker stop iota-node'])
    call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
            'sudo docker rm iota-node'])
    call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
            'echo udp://%s:14600 > ~/iota/run_time/neighbors'%pvt_ip_master])

    prev=""
    nxt=""
    if i == 0:
        prev = ip_pvts[len(ip_pvts)-1]
        nxt = ip_pvts[i+1]
    elif i == len(ip_pvts)-1:
        prev = ip_pvts[i-1]
        nxt = ip_pvts[0]
    else:
        prev = ip_pvts[i-1]
        nxt = ip_pvts[i+1]

    call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
            'echo udp://%s:13600 >> ~/iota/run_time/neighbors ; echo udp://%s:13600 >> ~/iota/run_time/neighbors ;'%(prev, nxt)])
    call(["pssh", "-i", "-H", "ubuntu@"+ip_pub, "-x", "\"-oStrictHostKeyChecking=no\"", "-x", "-i%s/gitlocal/dag.pem"%os.path.expanduser("~"), \
            'sudo docker run -d --net=host \
                       --name iota-node \
                       -e API_PORT=13700 \
                       -e UDP_PORT=13600 \
                       -e TCP_PORT=13600 \
                       -v /home/ubuntu/iota/run_time/data1:/iri/data \
                       -v /home/ubuntu/iota/run_time/neighbors:/iri/conf/neighbors \
                       stplaydog/iota-node:test_v1.5.5 \
                       /docker-entrypoint.sh'])
