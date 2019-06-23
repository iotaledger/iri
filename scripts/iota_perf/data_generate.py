import string
from random import SystemRandom
import random
import sys

num_name     = 500
num_name2    = 500
num_txn      = int(sys.argv[1])
txn_range    = 10
num_bs_steps = 500
tx_type      = "TX"

f1 = open("data", "w")
f2 = open("data1", "w")
f3 = open("check", "w")

cryptogen = SystemRandom()

letter_set = string.ascii_uppercase

# Generate names
name_list = []
for i in range(0, num_name):
    name = ''
    for j in range(10):
        position = cryptogen.randrange(26-1)
        name += letter_set[position]
    name_list.append(name)
    f3.write("curl -s -X GET http://127.0.0.1:8080/get_balance -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d '{\"account\": \""+ name +"\"}'\n")
    f3.write("echo \"\"\n")

name_list1 = []
for i in range(0, num_name2):
    name = ''
    for j in range(10):
        position = cryptogen.randrange(26-1)
        name += letter_set[position]
    name_list1.append(name)
    f3.write("curl -s -X GET http://127.0.0.1:8080/get_balance -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d '{\"account\": \""+ name +"\"}'\n")
    f3.write("echo \"\"\n")

# Bootstrapping
account = {}
for i in range(0, num_bs_steps):
    to_idx = i
    to_name = name_list[to_idx]
    value = 10000
    from_name = "A"
    if to_name not in account:
        account[to_name] = value
    else:
        account[to_name] += value
    f1.write(from_name+','+to_name+','+str(value) + ',' + tx_type+"\n")

# Rest of the transactions
for i in range(0, num_txn):
    from_idx = i%num_name
    from_name = name_list[from_idx]

    to_idx = i%num_name2
    to_name = name_list1[to_idx]

    value = cryptogen.randrange(txn_range)
    f2.write(from_name+','+to_name+','+str(value) + ',' + tx_type+"\n")

f1.close()
f2.close()
f3.close()
