from __future__ import print_function
import sys
sys.path.append("..")
import ConfigParser
import commands
import threading
import json
from flask import Flask, request
from iota_cache.iota_cache import IotaCache
from tag_generator import TagGenerator
from collections import deque
import StringIO
import gzip
from iota import TryteString
from key_manager.signmessage import sign_input_message

cf = ConfigParser.ConfigParser()
cf.read("conf")
iota_addr = cf.get("iota", "addr")
iota_seed = cf.get("iota", "seed")
enable_ipfs = cf.getboolean("iota", "enableIpfs")
enable_compression = cf.getboolean("iota", "enableCompression")
enable_batching = cf.getboolean("iota", "enableBatching")
enable_crypto = cf.getboolean("iota", "enableCrypto")
listen_port = cf.get("iota", "listenPort")
listen_address = cf.get("iota", "listenAddress")
cache = IotaCache(iota_addr, iota_seed)

# txs buffer. dequeue is thread-safe
txn_cache = deque()
TIMER_INTERVAL = 20
BATCH_SIZE = 20
COMPRESSED_SIZE = 7

cache_lock = threading.Lock()
lock = threading.Lock()


if (enable_ipfs == True and enable_compression == True) or (enable_batching == False and enable_compression == True):
    print("Error configure!", file=sys.stderr)
    sys.exit(-1)

def sign_message(data,address, base58_priv_key):
    if enable_crypto is True:
        message = json.dumps(data, sort_keys=True)
        signature = sign_input_message(address, message.replace(' ', ''), base58_priv_key)
        data['sign'] = signature
        return json.dumps(data, sort_keys=True)
    else:
        return json.dumps(data, sort_keys=True)

def compress_str(data):
    if enable_compression == True:
        out = StringIO.StringIO()
        with gzip.GzipFile(fileobj=out, mode="w") as f:
            f.write(data)
        compressed_data = out.getvalue()
        return TryteString.from_bytes(compressed_data).__str__()
    else:
        return data

def send(tx_string, tx_num=1, tag='TR'):
    if enable_ipfs == True:
        send_to_ipfs_iota(tx_string, tx_num, tag)
    else:
        send_to_iota(tx_string, tx_num, tag)

def send_to_ipfs_iota(tx_string, tx_num, tag):
    global lock
    with lock:
        filename = 'json'
        f = open(filename, 'w')
        f.write(tx_string)
        f.flush()
        f.close()

        (status, ipfs_hash) = commands.getstatusoutput(' '.join(['ipfs', 'add', filename, '-q']))
        if status != 0:
            print("[ERROR]Sending to ipfs failed -- '%s'" % ipfs_hash, file=sys.stderr)
            return

        print("[INFO]Cache json %s in ipfs, the hash is %s." % (tx_string, ipfs_hash), file=sys.stderr)

        if tx_num == 1:
            data = ipfs_hash
        else:
            data = json.dumps({"address": ipfs_hash, "tx_num": tx_num}, sort_keys=True)

        cache.cache_txn_in_tangle_simple(data, TagGenerator.get_current_tag(tag))
        print("[INFO]Cache hash %s in tangle, the tangle tag is %s." % (ipfs_hash, TagGenerator.get_current_tag("TR")), file=sys.stderr)

def send_to_iota(tx_string, tx_num, tag):
    global lock
    with lock:
        data = json.dumps({"txn_content": tx_string, "tx_num": tx_num}, sort_keys=True)

        if enable_batching is False:
            cache.cache_txn_in_tangle_simple(data, TagGenerator.get_current_tag(tag))
        else:
            compressed_data = compress_str(data)
            cache.cache_txn_in_tangle_message(compressed_data, TagGenerator.get_current_tag(tag))

        print("[INFO]Cache data in tangle, the tangle tag is %s." % (TagGenerator.get_current_tag(tag)), file=sys.stderr)

def get_cache():
    if enable_batching is False:
        return

    address = '14dD6ygPi5WXdwwBTt1FBZK3aD8uDem1FY'
    base58_priv_key = 'L41XHGJA5QX43QRG3FEwPbqD5BYvy6WxUxqAMM9oQdHJ5FcRHcGk'
    global cache_lock
    with cache_lock:
        nums = min(len(txn_cache), BATCH_SIZE)
        if nums == 0:
            return

        tx_list = []
        tr_list = []
        num_tr = 0
        num_tx = 0
        for i in range(nums):
            tx = txn_cache.popleft()
            req_json = json.loads(tx)
            if not req_json.has_key(u'tag'):
                tr_list.append(tx)
                num_tr += 1
            elif req_json[u'tag'] == 'TX':
                tx_list.append(sign_message(req_json, address, base58_priv_key))
                num_tx += 1

        tr_txs = json.dumps(tr_list)
        tx_txs = json.dumps(tx_list)
        if num_tx != 0:
            send(tx_txs, num_tx, 'TX')
        if num_tr != 0:
            send(tr_txs, num_tr, 'TR')


app = Flask(__name__)


@app.route('/')
def hello_world():
    return 'Hello World!'

@app.route('/get_balance', methods=['GET'])
def get_balance():
    req_json = request.get_json()

    if req_json is None:
        return 'error'

    if not req_json.has_key(u'account'):
        print("[ERROR]Account is needed.", file=sys.stderr)
        return 'error'

    account = req_json[u'account']
    resp = cache.get_balance('StreamNetCoin', account)

    balance = resp[u'balances'][0]
    print("Balance of '%s' is [%s]" % (account, balance), file=sys.stderr)
    return balance

@app.route('/put_file', methods=['POST'])
def put_file():

    address = '14dD6ygPi5WXdwwBTt1FBZK3aD8uDem1FY'
    base58_priv_key = 'L41XHGJA5QX43QRG3FEwPbqD5BYvy6WxUxqAMM9oQdHJ5FcRHcGk'

    req_json = request.get_json()

    if req_json is None:
        return 'error'

    if not req_json.has_key(u'tag'):
        send(sign_message(req_json, address, base58_priv_key))
    else:
        send(sign_message(req_json, address, base58_priv_key), tag=req_json[u'tag'])

    return 'ok'

@app.route('/put_cache', methods=['POST'])
def put_cache():
    if enable_batching is False:
        return 'error'

    req_json = request.get_json()
    if req_json is None:
        return 'error'

    tx_string = json.dumps(req_json, sort_keys=True)

    # cache in local ring-buffer
    txn_cache.append(tx_string)

    if len(txn_cache) >= BATCH_SIZE:
        get_cache()

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

@app.route('/add_neighbors', methods=['POST'])
def add_neighbors():
    req_json = request.get_json()
    if req_json is None:
        return 'error'
    if not req_json.has_key(u'uris'):
        print("[ERROR] Uris are needed.", file=sys.stderr)
        return 'error'
    uris = req_json[u'uris']
    resp = cache.add_neighbors(uris)
    return resp

@app.route('/get_block_content', methods=['GET'])
def get_block_content():
    req_json = request.get_json()
    if req_json is None:
        return 'error'
    if not req_json.has_key(u'hashes'):
        print("[ERROR] Hashes are needed.", file=sys.stderr)
        return 'error'
    hashes = req_json[u'hashes']
    resp = cache.get_block_content(hashes)
    print(resp, file=sys.stderr)
    ret_list = [x.encode('ascii') for x in resp[u'trytes']]
    return str(ret_list)

@app.route('/get_dag', methods=['GET'])
def get_dag():
    req_json = request.get_json()
    if req_json is None:
        return 'error'
    if not req_json.has_key(u'type'):
        print("[ERROR] Hashes are needed.", file=sys.stderr)
        return 'error'
    dag_type = req_json[u'type']
    resp = cache.get_dag(dag_type)
    if req_json.has_key(u'file_save'):
        file_save = req_json[u'file_save'].encode("ascii")
        f = open(file_save, 'w')
        f.write(resp[u'dag'])
        f.close()
    return resp[u'dag'] 

@app.route('/get_utxo', methods=['GET'])
def get_utxo():
    req_json = request.get_json()
    if req_json is None:
        return 'error'
    if not req_json.has_key(u'type'):
        print("[ERROR] Hashes are needed.", file=sys.stderr)
        return 'error'
    dag_type = req_json[u'type']
    resp = cache.get_utxo(dag_type)
    if req_json.has_key(u'file_save'):
        file_save = req_json[u'file_save'].encode("ascii")
        f = open(file_save, 'w')
        f.write(resp[u'dag'])
        f.close()
    return resp[u'dag']

@app.route('/get_total_order', methods=['GET'])
def get_total_order():
    resp = cache.get_total_order()
    return resp[u'totalOrder']

if __name__ == '__main__':
    # timer
    timer_thread = threading.Timer(TIMER_INTERVAL, get_cache)
    timer_thread.start()
    app.run(host=listen_address, port=listen_port)
