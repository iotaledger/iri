#!/usr/bin/python
# -*- coding: utf-8 -*-
import re
import sys
import json
import sh
cmd =  sh.Command("/usr/local/bin/pssh")
user = str(sh.Command('whoami')().split()[0])
# example usage:python add_neighbors_batch.py  add  172.21.0.30:14700  172.21.0.26:14600  172.21.0.17:14600

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
    check_flag = []
    if ippvts and len(iplist)>=2 :
        ippvts = list(ippvts.keys())
        for ipinfo in iplist:
            ipadress = ipinfo.split(':')[0]
            if check_ip(ipadress) and ipadress in ippvts:
                check_flag.append('True')
            else:
                check_flag.append('False')
    else :
        check_flag.append('False')
    return check_flag

def add_neighbors(source_ip,source_port,desc_ip,desc_port):
    ip_total = get_ip_list()
    ret = cmd("-i", "-H", "%s@"%user+ip_total[source_ip], "-x", "\"-oStrictHostKeyChecking=no\"",'''/usr/bin/curl -s http://localhost:%s -X POST -H 'Content-Type: application/json' -H  'X-IOTA-API-Version: 1' -d '{"command":"addNeighbors", "uris": ["tcp://%s:%s"]}' '''%(source_port,desc_ip,desc_port))
    content = ret.split('\n')[1]
    json_data  = json.loads(content)
    if json_data.has_key(u'error'):
        add_flag = source_ip + '------>' + desc_ip + ':' + desc_port + '  false'
    else:
        if json_data[u'addedNeighbors']>=1:
            add_flag = source_ip + '------>' + desc_ip + ':' + desc_port + '  success'
        else:
            add_flag = source_ip + '------>' + desc_ip + ':' + desc_port + '  already exist'
    return add_flag

def remove_neighbors(source_ip,source_port,desc_ip,desc_port):
    ip_total = get_ip_list()
    ret = cmd("-i", "-H", "%s@"%user+ip_total[source_ip], "-x", "\"-oStrictHostKeyChecking=no\"",'''/usr/bin/curl -s http://localhost:%s -X POST -H 'Content-Type: application/json' -H  'X-IOTA-API-Version: 1' -d '{"command":"removeNeighbors", "uris": ["tcp://%s:%s"]}' '''%(source_port,desc_ip,desc_port))
    content = ret.split('\n')[1]
    json_data  = json.loads(content)
    if json_data.has_key(u'error'):
        remove_flag = source_ip + '----X-->' + desc_ip + ':' + desc_port + '  false'
    else:
        if json_data[u'removedNeighbors']>=1:
            remove_flag = source_ip + '----X-->' + desc_ip + ':' + desc_port + '  success'
        else:
            remove_flag = source_ip + '------>' + desc_ip + ':' + desc_port + '  not exist'
    return remove_flag

def get_neighbors(source_ip,source_port):
    ip_total = get_ip_list()
    ret = cmd("-i", "-H", "%s@"%user+ip_total[source_ip], "-x", "\"-oStrictHostKeyChecking=no\"",'''/usr/bin/curl -s http://localhost:%s -X POST -H 'Content-Type: application/json' -H  'X-IOTA-API-Version: 1' -d '{"command":"getNeighbors"}' '''%(source_port))
    content = ret.split('\n')[1]
    json_data  = json.loads(content)
    if json_data.has_key(u'error'):
        get_info = source_ip+':'+source_port+u'邻居节点查询失败'
    else:
        ret_total = []
        get_info = {}
        for neighbors_ret in json_data[u'neighbors']:
            address = neighbors_ret['address']
            connectionType = neighbors_ret['connectionType']
            ret_total.append(connectionType+'://'+address)
        get_info[u'neighbors:'] = ret_total
    return get_info

def get_data_from_input(input_data,flag):
    input_ip = input_data[1:]
    ippvts = get_ip_list()
    iplist = input_ip
    check_flag = check_ip_info(iplist, ippvts)
    if "False" in check_flag:
        return  "请检查主机列表是否为空或者输入IP格式不正确"
    else:
        source_ip = input_ip[0].split(':')[0]
        source_port = input_ip[0].split(':')[1]
        ret_info = []
        for neighbor_info in input_ip[1:]:
            desc_ip = neighbor_info.split(':')[0]
            desc_port = neighbor_info.split(':')[1]
            if flag == 'add':
                oret = add_neighbors(source_ip,source_port,desc_ip,desc_port)
                ret_info.append(oret)
            elif flag == 'remove':
                oret = remove_neighbors(source_ip,source_port,desc_ip,desc_port)
                ret_info.append(oret)
        return ret_info

if __name__=='__main__':
    input_data = sys.argv
    flag = input_data[1]
    if flag in ['add','remove']:
        try:
            print(get_data_from_input(input_data[1:],flag))
        except Exception as e:
            print("%s失败"%flag)
    elif flag == 'get':
        ip = input_data[2].split(':')[0]
        port = input_data[2].split(':')[1]
        try:
            print(get_neighbors(ip,port))
        except Exception as e:
            print("查询失败")
    else:
        print("输入格式有误")
