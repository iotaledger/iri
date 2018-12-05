import string
import random

for i in range(0, 10000):
    from_name = ''.join(random.choice(string.ascii_uppercase + string.digits) for _ in range(10))
    to_name = ''.join(random.choice(string.ascii_uppercase + string.digits) for _ in range(10))
    value = random.randint(1,100)
    print from_name+','+to_name+','+str(value)
