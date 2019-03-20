import string
from random import SystemRandom
import random

num_name     = 100
num_txn      = 10000
txn_range    = 10
num_bs_steps = 100
tx_type      = "TX"

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

# Bootstrapping
account = {}
for i in range(0, num_bs_steps):
    to_idx = cryptogen.randrange(num_name-1)
    to_name = name_list[to_idx]
    value = 10000
    from_name = "A"
    if to_name not in account:
        account[to_name] = value
    else:
        account[to_name] += value
    print from_name+','+to_name+','+str(value) + ',' + tx_type

# Rest of the transactions
for i in range(0, num_txn):
    from_idx = 0
    while True:
        from_idx = cryptogen.randrange(num_name-1)
        if name_list[from_idx] in account:
            break
    from_name = name_list[from_idx]

    to_idx = from_idx
    while True:
        to_idx = cryptogen.randrange(num_name-1)
        if to_idx != from_idx:
            break
    to_name = name_list[to_idx]

    value = cryptogen.randrange(txn_range)
    print from_name+','+to_name+','+str(value) + ',' + tx_type
