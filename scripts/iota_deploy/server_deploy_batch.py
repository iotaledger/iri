#!/usr/bin/python
# -*- coding: utf-8 -*-
import sys
import re
import sh
cmd =  sh.Command("/usr/local/bin/pssh")
pycmd = sh.Command("/usr/bin/python")



def get_ip_list():
    ipdict = {}
    with open("ipinfo.txt",'r') as f:
        oret = f.readlines()
        for info in oret:
                ippub,ippvt = info.replace('\n','').split(',')
                ipdict[ippvt] = ippub
        return ipdict

#iri deploy
def deploy_iri_server():
    ip_total = get_ip_list()
    ip_pub = list(ip_total.values())
    for ip_address in ip_pub:
        oret = cmd("-i", "-H", "trust@"+ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps |grep iota-node |wc -l")
        num_exist = oret.split()[-1]
        if int(num_exist):
            cmd("-i", "-H", "trust@"+ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker stop iota-node")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-node")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo rm -rf ~/iri/scripts/examples/data/testnetdb")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo rm -rf ~/iri/scripts/examples/data/testnetdb.log")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker run  -d -p 14700:14700 -p 13700:13700 --name iota-node -v /home/trust/iri/scripts/examples/data:/iri/data -v /home/trust/iri/scripts/examples/conf/neighbors:/iri/conf/neighbors  iota-node:v0.1-streamnet  /entrypoint.sh")
        else:
            exitflag = cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps -a |grep iota-node |wc -l")
            exitflag = exitflag.split()[-1]
            if int(exitflag):
                cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-node")
                cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo rm -rf ~/iri/scripts/examples/data/testnetdb")
                cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo rm -rf ~/iri/scripts/examples/data/testnetdb.log")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker run  -d -p 14700:14700 -p 13700:13700 --name iota-node -v /home/trust/iri/scripts/examples/data:/iri/data -v /home/trust/iri/scripts/examples/conf/neighbors:/iri/conf/neighbors  iota-node:v0.1-streamnet  /entrypoint.sh")
    return 'success'

#iri clear
def clear_iri_server():
    ip_total = get_ip_list()
    ip_pub = list(ip_total.values())
    for ip_address in ip_pub:
        oret = cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps |grep iota-node |wc -l")
        num_exist = oret.split()[-1]
        if int(num_exist):
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo rm -rf ~/iri/scripts/examples/data/testnetdb")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo rm -rf ~/iri/scripts/examples/data/testnetdb.log")
            cmd("-i", "-H", "trust@"+ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker stop iota-node")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-node")
        else:
            exitflag = cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps -a |grep iota-node |wc -l")
            exitflag = exitflag.split()[-1]
            if int(exitflag):
                cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-node")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo rm -rf ~/iri/scripts/examples/data/testnetdb")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo rm -rf ~/iri/scripts/examples/data/testnetdb.log")

    return 'success'

#cli deploy
def deploy_cli_server():
    ip_total = get_ip_list()
    ip_pub = list(ip_total.values())
    for ip_address in ip_pub:
        oret = cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps |grep iota-cli |wc -l")
        num_exist =  oret.split()[-1]
        if int(num_exist):
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker stop iota-cli")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker rm iota-cli")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker run -d -p 5000:5000 --name iota-cli iota-cli:v0.1-streamnet /docker-entrypoint.sh")
        else:
            exitflag = cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps -a |grep iota-cli |wc -l")
            exitflag = exitflag.split()[-1]
            if int(exitflag):
                cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-cli")
            cmd("-i", "-H", "trust@" + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker run -d -p 5000:5000 --name iota-cli iota-cli:v0.1-streamnet /docker-entrypoint.sh")
    return 'success'

# add and remove neighbors
def link_iri_server(input_data):
    with open("topology.txt", 'r') as list_file:
        ip_list = list_file.read()
        moudle = re.compile(r'(?:(?:[0,1]?\d?\d|2[0-4]\d|25[0-5])\.){3}(?:[0,1]?\d?\d|2[0-4]\d|25[0-5]):\d{0,5}')
        result = re.findall(moudle, ip_list)
        add_ip = [x for x in result[::2]]
        desc_ip = [x for x in result[1::2]]
        num = len(add_ip)
        for i in range(0,num):
            k = add_ip[i].replace('\'','')
            v = desc_ip[i].replace('\'','')
            pycmd('add_neighbors_batch.py',input_data,k,v)
        return 'success'



if __name__ == '__main__':
    input_p = sys.argv
    if input_p[1] == 'iri':
        deploy_iri_server()
    elif input_p[1] == 'cli':
        deploy_cli_server()
    elif input_p[1] == 'clear':
        clear_iri_server()
    elif input_p[1] in ['add','remove']:
        link_iri_server(input_p[1])
