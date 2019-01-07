import ConfigParser
import time
import commands
import threading
import json
from flask import Flask, request
from iota_cache import IotaCache
from tag_generator import TagGenerator
from collections import deque

lock = threading.Lock()
def send_to_ipfs_iota(tx_string):
    global lock

    lock.acquire()

    filename = 'json'
    f = open(filename, 'w')
    f.write(tx_string)
    f.flush()
    f.close()

    ipfs_hash = commands.getoutput(' '.join(['ipfs', 'add', filename, '-q']))

    print("[INFO]Cache json %s in ipfs, the hash is %s." % (json.dumps(req_json, sort_keys=True), ipfs_hash))

    cache.cache_txn_in_tangle_simple(ipfs_hash, TagGenerator.get_current_tag())

    print("[INFO]Cache hash %s in tangle, the tangle tag is %s." % (ipfs_hash, TagGenerator.get_current_tag()))

    lock.release()

    # return 'ok'

def get_cache():
    nums = min(len(txn_cache), BATCH_SIZE)
    if nums == 0:
        return

    all_txs = ""
    for i in range(nums):
        tx = txn_cache.popleft()
        # use '\n' to separate transactions.
        # TODO: to think a better way to separate transactions.
        all_txs += tx + '\n'

    send_to_ipfs_iota(all_txs)

# txs buffer. dequeue is thread-safe, but it will overwrite early items if buffer is full.
# TODO: maybe i should implement a 'ring-buffer' class, but for test, dequeue is ok.
CACHE_SIZE = 10000
txn_cache = deque(maxlen=CACHE_SIZE)

# timer
timer_thread = threading.Timer(1, get_cache)
timer_thread.start()

BATCH_SIZE = 100


app = Flask(__name__)

cf = ConfigParser.ConfigParser()
cf.read("conf")
iota_addr = cf.get("iota", "addr")
iota_seed = cf.get("iota", "seed")
cache = IotaCache(iota_addr, iota_seed)


@app.route('/')
def hello_world():
    return 'Hello World!'

@app.route('/put_file', methods=['POST'])
def put_file():
    req_json = request.get_json()

    if req_json is None:
        return 'error'

    req_json["timestamp"] = str(time.time())
    send_to_ipfs_iota(json.dumps(req_json, sort_keys=True))

    return 'ok'

@app.route('/put_cache', methods=['POST'])
def put_cache():
    # get json
    req_json = request.get_json()
    if req_json is None:
        return 'error'

    req_json["timestamp"] = str(time.time())

    tx_string = json.dumps(req_json, sort_keys=True)
    if len(txn_cache) >= CACHE_SIZE:
        # ring-buffer is full, send to ipfs and iota directly.
        send_to_ipfs_iota(tx_string)
    else:
        # cache in local ring-buffer
        txn_cache.append(tx_string)

    return 'ok'

@app.route('/post_contract', methods=['POST'])
def post_contract():
    req_json = request.get_json()

    if req_json is None:
        return 'request error'
    print("now come here to post contract")

    cache.cache_txn_in_tangle_simple(req_json['ipfs_addr'], TagGenerator.get_current_tag("SC"))
    return 'ok'

@app.route('/post_action', methods=['POST'])
def post_action():
    req_json = request.get_json()

    if req_json is None:
        return 'request error'

    cache.cache_txn_in_tangle_simple(req_json['ipfs_addr'], TagGenerator.get_current_tag("SA"))
    return 'ok'

@app.route('/put_contract', methods=['PUT'])
def put_contract():
    req_json = request.get_json()

    if req_json is None:
        return 'request error'

    msg = Fragment(TryteString(req_json['ipfs_addr']))
    ipfs_addr = msg.decode()
    wasm.set_contract(ipfs_addr)
    return 'ok'

@app.route('/put_action', methods=['PUT'])
def put_action():
    req_json = request.get_json()

    if req_json is None:
        return 'request error'

    msg = Fragment(TryteString(req_json['ipfs_addr']))
    ipfs_addr = msg.decode()
    wasm.exec_action(ipfs_addr)
    return 'ok'

if __name__ == '__main__':
    app.run()
