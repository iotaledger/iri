#!/usr/bin/python
# -*- coding: utf-8 -*-
import csv
import sys
from sh import  grep,cat,awk

log_file = sys.argv[1]
file_name = sys.argv[2]
total_topology = ['3_clique','4_circle','4_clique','7_circle','7_clique','7_bridge','7_star']
txn_num = [5000,10000,15000,20000]

total_num = []
oret = awk(grep(grep(grep(cat('%s'%log_file),5000),'-v','configure'),'-v',15000),'{print $7}').split()
i = 0
for tps in oret:
    tps_num = tps.replace('/s','')
    total_num.append([5000,tps_num,total_topology[i]])
    i+=1

for tx_nm in txn_num[1:]:
    oret = awk(grep(grep(cat('%s'%log_file),tx_nm),'-v','configure'),'{print $7}').split()
    i = 0
    for tps in oret:
        tps_num = tps.replace('/s', '')
        total_num.append([tx_nm, tps_num, total_topology[i]])
        i += 1

with open(file_name,"w") as csvfile:
      writer = csv.writer(csvfile)
      writer.writerow(['num_txn','TPS','cluster_size'])
      writer.writerows(sorted(total_num,key=lambda stu:stu[2]))
