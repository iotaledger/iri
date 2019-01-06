import ConfigParser
import time
import commands
import json
from flask import Flask, request
from iota_cache import IotaCache
from tag_generator import TagGenerator


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

    filename = 'json'
    f = open(filename, 'w')
    req_json["timestamp"] = str(time.time())
    f.write(json.dumps(req_json, sort_keys=True))
    f.flush()
    f.close()

    ipfs_hash = commands.getoutput(' '.join(['ipfs', 'add', filename, '-q']))

    print("[INFO]Cache json %s in ipfs, the hash is %s." % (json.dumps(req_json, sort_keys=True), ipfs_hash))

    cache.cache_txn_in_tangle_simple(ipfs_hash, TagGenerator.get_current_tag())

    print("[INFO]Cache hash %s in tangle, the tangle tag is %s." % (ipfs_hash, TagGenerator.get_current_tag()))

    return 'ok'


# txs buffer. dequeue is thread-safe, but it will overwrite early items if buffer is full.
# TODO: maybe i should implement a 'ring-buffer' class by myself, but for test, dequeue is ok.
CACHE_SIZE = 10000
txn_cache = deque(maxlen=CACHE_SIZE)

# timer
timer_thread = threading.Timer(300, get_cache)
timer_thread.start()

BATCH_SIZE = 100

@app.route('/put_cache', methods=['POST'])
def put_cache():
    # time
    now = time.time()

    # ring-buffer is full, use 'put_fule' interface, or we can save another buffers
    if len(txn_cache) >= CACHE_SIZE:
        put_file()

    # get json
    req_json = request.get_json()
    if req_json is None:
        return 'error'
    req_json["timestamp"] = str(time.time())

    # cache in local ring-buffer
    txn_cache.append(json.dumps(req_json, sort_keys=True))

    return 'ok'


@app.route('/get_cache', methods=['POST'])
def get_cache():
    filename = 'jsons'
    f = open(filename, 'w')

    nums= min(len(txn_cache), BATCH_SIZE)
    if nums == 0:
        return 'ok'

    for i in range(nums):
        tx = txn_cache.popleft()
        f.write(tx)
        f.flush()
    f.close()

    ipfs_hash = commands.getoutput(' '.join(['ipfs', 'add', filename, '-q']))
    print("[INFO]Cache jsons in ipfs, the hash is %s." % (ipfs_hash))

    cache.cache_txn_in_tangle_simple(ipfs_hash, TagGenerator.get_current_tag("TA"))
    print("[INFO]Cache hash %s in tangle, the tangle tag is %s." % (ipfs_hash, TagGenerator.get_current_tag("TR")))

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
