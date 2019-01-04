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

json_list = []
flag = 0
start_time = time.time()
WAIT_TIME = 10
COLLECT_TIME = 2000
LIST_LEN = 100

@app.route('/put_cache', methods=['POST'])
def put_cache():
    global json_list
    global flag
    global start_time

    # check flag, if busy, wait for 10s
    if flag == 0:
        flag = 1
    else:
        wait_time = 0
        while 1:
            time.sleep(1)
            wait_time += 1
            if flag == 0:
                flag = 1
                break
            if wait_time >= WAIT_TIME:
                return 'error'

    # timer
    global timer_thread
    timer_thread = threading.Timer(300, put_cache)
    timer_thread.start()

    # time
    now = time.time()

    # get json
    req_json = request.get_json()
    if req_json is None:
        flag = 0
        return 'error'

    # cache in local list
    req_json["timestamp"] = str(time.time())
    json_list.append(json.dumps(req_json, sort_keys=True))

    if len(json_list) < LIST_LEN and now - start_time < COLLECT_TIME:
        print("[INFO]Cache json %s locally." % (json.dumps(req_json, sort_keys=True)))
        flag = 0
        return 'ok'

    filename = 'jsons'
    f = open(filename, 'w')
    for i in json_list:
        f.write(i)
        f.flush()
    f.close()

    ipfs_hash = commands.getoutput(' '.join(['ipfs', 'add', filename, '-q']))
    print("[INFO]Cache jsons in ipfs, the hash is %s." % (ipfs_hash))

    cache.cache_txn_in_tangle_simple(ipfs_hash, TagGenerator.get_current_tag("TA"))
    print("[INFO]Cache hash %s in tangle, the tangle tag is %s." % (ipfs_hash, TagGenerator.get_current_tag("TR")))

    json_list[:] = []
    start_time = now
    flag = 0

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
