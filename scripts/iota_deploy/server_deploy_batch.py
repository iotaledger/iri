#!/usr/bin/python
# -*- coding: utf-8 -*-
import sys,os
import re
import sh
import ast
from multiprocessing.dummy import Pool as ThreadPool
cmd =  sh.Command("/usr/local/bin/pssh")
pycmd = sh.Command("/usr/bin/python")
pwd = os.getcwd()
user = str(sh.Command('whoami')().split()[0])

if 'examples' in pwd:
    father_path = os.path.abspath(os.path.dirname(pwd) + os.path.sep + ".")
    rmdblog = father_path + '/data/testnetdb.log'
    rmdb = father_path + '/data/testnetdb'
    data_volume = father_path + '/data'
    conf_volume = father_path + '/conf/neighbors'
else:
    father_path = os.path.abspath(os.path.dirname(pwd) + os.path.sep + ".")
    rmdblog = father_path + '/examples/data/testnetdb.log'
    rmdb = father_path + '/examples/data/testnetdb'
    data_volume = father_path + '/examples/data'
    conf_volume = father_path + '/examples/conf/neighbors'

def get_ip_list():
    ipdict = {}
    with open("ipinfo.txt",'r') as f:
        oret = f.readlines()
        for info in oret:
                ippub,ippvt = info.replace('\n','').split(',')
                ipdict[ippvt] = ippub
        return ipdict

#iri deploy
def deploy_iri_server_method(*args):
    ip_info = get_ip_list()
    total_ip_info = {value:key for key, value in ip_info.items()}
    ip_address = args[0][0]
    version_image = args[0][1]
    print('%s is deploying iri server'%ip_address)
    oret = cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps |grep iota-node:%s |wc -l"%version_image)
    num_exist = oret.split()[-1]
    data_path = data_volume+'/*'
    cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" %data_path)
    if int(num_exist):
        source_add = total_ip_info[ip_address]+':14700'
        ret_nerighbors = pycmd('add_neighbors_batch.py','get',source_add).strip()
        nerighbors_info = ast.literal_eval(ret_nerighbors.encode('utf-8'))
        total_neighbors = nerighbors_info[u'neighbors:']
        total_neighbors_info = [x.replace('tcp://','') for x in total_neighbors]
        if len(total_neighbors_info):
            for n_ip in total_neighbors_info:
                pycmd('add_neighbors_batch.py','remove',source_add,n_ip)
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker stop iota-node")
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-node")
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" % rmdb)
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" % rmdblog)
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker run  -d -p 14700:14700 -p 13700:13700 --name iota-node -v %s:/iri/data -v %s:/iri/conf/neighbors  iota-node:%s  /entrypoint.sh" % (data_volume, conf_volume,version_image))
    else:
        exitflag = cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps -a |grep iota-node:%s |wc -l"%version_image)
        exitflag = exitflag.split()[-1]
        if int(exitflag):
            cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-node")
            cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" % rmdb)
            cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" % rmdblog)
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker run  -d -p 14700:14700 -p 13700:13700 --name iota-node -v %s:/iri/data -v %s:/iri/conf/neighbors  iota-node:%s  /entrypoint.sh" % (data_volume, conf_volume,version_image))
    print('%s  deploy iri server  success' % ip_address)
    return 'success'

#iri clear
def clear_iri_server_method(*args):
    ip_address = args[0][0]
    version_image = args[0][1]
    print('%s is clearing iri server'%ip_address)
    oret = cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps |grep iota-node:%s |wc -l"%version_image)
    num_exist = oret.split()[-1]
    if int(num_exist):
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker stop iota-node")
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-node")
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" % rmdb)
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" % rmdblog)
    else:
        exitflag = cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps -a |grep iota-node:%s |wc -l"%version_image)
        exitflag = exitflag.split()[-1]
        if int(exitflag):
            cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-node")
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" % rmdb)
        cmd("-i", "-H", "%s@" % user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo rm -rf %s" % rmdblog)
    print('%s  clear iri server  success' % ip_address)
    return 'success'

#cli deploy
def deploy_cli_server_method(*args):
    ip_address = args[0][0]
    version_image = args[0][1]
    batchflag = args[0][2]
    print('%s is deploying cli server'%ip_address)
    oret = cmd("-i", "-H", "%s@"%user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps |grep iota-cli:%s |wc -l"%version_image)
    num_exist =  oret.split()[-1]
    if int(num_exist):
        cmd("-i", "-H", "%s@"%user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker stop iota-cli")
        cmd("-i", "-H", "%s@"%user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker rm iota-cli")
        cmd("-i", "-H", "%s@"%user + ip_address, "-x", "-oStrictHostKeyChecking=no", '''sudo docker run -d -p 5000:5000 -e "ENABLE_BATCHING=%s"  -e "HOST_IP=$SSH_CONNECTION" --name iota-cli iota-cli:%s /docker-entrypoint.sh'''%(batchflag,version_image))
    else:
        exitflag = cmd("-i", "-H", "%s@"%user + ip_address, "-x", "-oStrictHostKeyChecking=no","sudo docker ps -a |grep iota-cli:%s |wc -l"%version_image)
        exitflag = exitflag.split()[-1]
        if int(exitflag):
            cmd("-i", "-H", "%s@"%user + ip_address, "-x", "-oStrictHostKeyChecking=no", "sudo docker rm iota-cli")
        cmd("-i", "-H", "%s@"%user + ip_address, "-x", "-oStrictHostKeyChecking=no",'''sudo docker run -d -p 5000:5000 -e "ENABLE_BATCHING=%s"  -e "HOST_IP=$SSH_CONNECTION" --name iota-cli iota-cli:%s /docker-entrypoint.sh'''%(batchflag,version_image))
    print('%s  deploy cli server  success' % ip_address)
    return 'success'

def server_deploy_method(operation_type,verison_image,batchflag=None):
    pool = ThreadPool(7)
    ip_total = get_ip_list()
    ip_pub = list(ip_total.values())
    if operation_type  in (deploy_iri_server_method,clear_iri_server_method):
        in_param = [(x,verison_image) for x in ip_pub]
        pool.map(operation_type, in_param)
    elif operation_type == deploy_cli_server_method:
        in_param = [(x, verison_image,batchflag) for x in ip_pub]
        pool.map(operation_type,in_param)
    pool.close()
    pool.join()

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
        print('topology neighbors add success')
        return 'success'

if __name__ == '__main__':
    input_p = sys.argv
    if input_p[1] == 'iri':
        version_image = input_p[2]
        server_deploy_method(deploy_iri_server_method,version_image)
    elif input_p[1] == 'cli':
        version_image = input_p[2]
        batchflag = input_p[3]
        server_deploy_method(deploy_cli_server_method,version_image,batchflag)
    elif input_p[1] == 'clear':
        version_image = input_p[2]
        server_deploy_method(clear_iri_server_method,version_image)
    elif input_p[1] in ['add','remove']:
        link_iri_server(input_p[1])
