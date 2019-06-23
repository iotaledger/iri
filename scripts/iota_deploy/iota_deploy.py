import sys
import os
from subprocess import call
import time

# example usage:
# python iota_deploy.py pssh_hosts 54.179.133.32 14700 iri-1.5.5.jar StreamNet_v1.0

ipfile       = open(sys.argv[1])
master       = sys.argv[2]
master_port  = sys.argv[3]
iri_jar      = sys.argv[4]
iri_version  = sys.argv[5]

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
                'cd ~/iota/run-time/ ; bash  ./run_master.sh'])
        time.sleep(5)
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
                       -v /home/ubuntu/iota/run_time/data:/iri/data \
                       -v /home/ubuntu/iota/run_time/neighbors:/iri/conf/neighbors \
                       stplaydog/iota-node:%s \
                       /docker-entrypoint.sh'%iri_version])
