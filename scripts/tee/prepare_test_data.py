import sys
import random

num_nodes = 16 
avg_degree = 5

for n in range(0, num_nodes):
    for d in range(0, avg_degree):
        attester = n 
        attestee = int(random.uniform(0, num_nodes))
        score = int(random.uniform(0, 2))
        print "./tee addattestationinfo -info " + str(attester)+","+str(attestee)+","+str(score)
