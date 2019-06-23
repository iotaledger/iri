import sys
import os
from subprocess import call
import time

# Steps
# 1) in each machine create sudo user stplaydog / deploy the ssh key
# https://www.digitalocean.com/community/tutorials/how-to-create-a-sudo-user-on-ubuntu-quickstart
# 2) install docker in each machine
# https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-16-04
# 3) pull docker images in each machine
# sudo docker pull stplaydog/iota-node:StreamNet_v1.0
# 4) copy the master tar file to the first machine
# 5) install java in master machine:
# https://tecadmin.net/install-oracle-java-8-ubuntu-via-ppa/
# 6) Install IPFS in master machine
# 7) run this script
# https://docs.ipfs.io/introduction/install/
# 8) issue a coo
# if you want to kill local ipfs : https://askubuntu.com/questions/447820/ssh-l-error-bind-address-already-in-use

# example usage:
# 1) For tencent cloud:
# python iota_deploy_prod.py pssh_hosts_ten 192.144.152.140 14700 iri-1.5.5.jar StreamNet_v1.0
# 2) for production environment
# python iota_deploy_prod.py pssh_hosts_prod 52.221.236.50 14700 iri-1.5.5.jar StreamNet_v1.0

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
        call(["pssh", "-H", "stplaydog@"+ip_pub, 'cd ~/iota/run-time/ ; bash  ./run_master.sh'])
        time.sleep(5)
    call(["pssh", "-H", "stplaydog@"+ip_pub, \
            'mkdir -p ~/iota/run_time/data; touch ~/iota/run_time/neighbors'])
    call(["pssh", "-H", "stplaydog@"+ip_pub, \
            'echo stplaydog | sudo -S rm -rf ~/iota/run_time/data/*;'])
    call(["pssh", "-H", "stplaydog@"+ip_pub, \
            'echo stplaydog | sudo -S docker stop iota-node'])
    call(["pssh", "-H", "stplaydog@"+ip_pub, \
            'echo stplaydog | sudo -S docker rm iota-node'])
    call(["pssh", "-H", "stplaydog@"+ip_pub, \
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

    call(["pssh", "-H", "stplaydog@"+ip_pub,  \
            'echo udp://%s:13600 >> ~/iota/run_time/neighbors ; echo udp://%s:13600 >> ~/iota/run_time/neighbors ;'%(prev, nxt)])
    call(["pssh", "-H", "stplaydog@"+ip_pub,  \
            'echo stplaydog | sudo -S docker run -d --net=host \
                       --name iota-node \
                       -e API_PORT=13700 \
                       -e UDP_PORT=13600 \
                       -e TCP_PORT=13600 \
                       -v /home/stplaydog/iota/run_time/data:/iri/data \
                       -v /home/stplaydog/iota/run_time/neighbors:/iri/conf/neighbors \
                       stplaydog/iota-node:%s \
                       /docker-entrypoint.sh'%iri_version])
