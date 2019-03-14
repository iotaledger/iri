import random

num_nodes = 16
avg_degree = 5

cryptogen = random.SystemRandom()

for n in range(0, num_nodes):
    for d in range(0, avg_degree):
        attester = n
        attestee = cryptogen.randrange(num_nodes)
        score = cryptogen.randrange(2)
        print "./tee addattestationinfo -info " + str(attester)+","+str(attestee)+","+str(score)
