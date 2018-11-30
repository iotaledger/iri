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

    cache.cache_txn_in_tangle(ipfs_hash, TagGenerator.get_current_tag())

    print("[INFO]Cache hash %s in tangle, the tangle tag is %s." % (ipfs_hash, TagGenerator.get_current_tag()))

    return 'ok'


if __name__ == '__main__':
    app.run()


