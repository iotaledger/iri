#!/usr/bin/python
# -*- coding: utf-8 -*-


import re
import subprocess
# example usage: 172.21.0.30(src)  172.21.0.26(dest)  172.21.0.17(dest) 14600(port)
#172.21.0.30 172.21.0.26 172.21.0.17

def check_ip(ip):
    p = re.compile('^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$')
    if p.match(ip):
        return True
    else:
        return False


def get_ip_list():
    ipdict = {}
    with open("ipinfo.txt",'r') as f:
        oret = f.readlines()
        for info in oret:
                ippub,ippvt = info.replace('\n','').split(',')
                ipdict[ippvt] = ippub
        return ipdict

def check_ip_info(iplist,ippvts):
    a = []
    if ippvts and len(iplist)>=3 :
        ippvts = list(ippvts.keys())
        for ipinfo in iplist[:-1]:
            if check_ip(ipinfo) and ipinfo in ippvts:
                a.append('True')
            else:
                a.append('False')
    else :
        a.append('False')
    return a

def add_neighbors(source_ip,desc_ip,port):
    ip_total = get_ip_list()
    add_flag = []
    for addip in desc_ip:
        ret = subprocess.Popen(["pssh", "-i", "-H", "trust@"+ip_total[source_ip], "-x", "\"-oStrictHostKeyChecking=no\"",'''/usr/bin/curl -s http://localhost:14700 -X POST -H 'Content-Type: application/json' -H  'X-IOTA-API-Version: 1' -d '{"command":"addNeighbors", "uris": ["tcp://%s:%s"]}' '''%(addip,port)],shell=False, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if 'SUCCESS' in ret.stdout.readline().strip():
            add_flag.append(source_ip + '------>' + addip + ':' + port + ' link success')
        else:
             add_flag.append(source_ip + '------>' + addip + ':' + port + ' link false')
    return add_flag


while True:
    content = raw_input("please input your message:")
    print(content,type(content))
    iplist = str(content).split()
    ippvts = get_ip_list()
    check_flag = check_ip_info(iplist,ippvts)
    if str(content) == 'q':
        break
    elif "False" in check_flag:
        print("请检查主机列表是否为空或者输入IP格式不正确")
    else:
        source_ip = iplist[0]
        desc_ip = iplist[1:-1]
        port = iplist[-1]
        oret = add_neighbors(source_ip,desc_ip,port)
        for link_info in oret:
            print(link_info)

