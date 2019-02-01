import sys
sys.path.append("..")
import json
import datetime
import threading
import ConfigParser
import ipfsapi
from iota_cache.iota_cache import IotaCache
from tag_generator import TagGenerator
from tm_rpc_client import Tendermint


cf = ConfigParser.ConfigParser()
cf.read("conf")


iota_addr = cf.get("iota", "addr")
iota_seed = cf.get("iota", "seed")
cache = IotaCache(iota_addr, iota_seed)

tm_addr = cf.get("tendermint", "addr")
tm = Tendermint(tm_addr)

ipfs_ip = cf.get("ipfs", "ip")
ipfs_port = cf.get("ipfs", "port")
ipfs_client = ipfsapi.connect(ipfs_ip, ipfs_port)


def interval_work():
    print("...work work work...")
    now = datetime.datetime.now()
    print(now)

    global timer_thread
    timer_thread = threading.Timer(60, interval_work)
    timer_thread.start()

    tag = TagGenerator.get_current_tag()
    push_and_sync(tag)

    # 00:00:00 -> 00:00:59, check previous tag
    if now.hour == 0 and 0 <= now.minute <= 0:
        tag = TagGenerator.get_previous_tag()
        push_and_sync(tag)

    print("...work end...")


def push_and_sync(tag):

    result = cache.get_non_consumed_txns(tag)
    content_dict = {}

    for ipfs_addr in result:
        content = ipfs_client.get_json(ipfs_addr)
        content_dict[ipfs_addr] = content

    if len(content_dict) > 0:

        if len(content_dict) > 1:
            content_list = []
            for key in content_dict:
                content_list.append((content_dict[key], key))

            sorted_content_list = sorted(content_list, key=lambda d: d[0]['timestamp'])

            for item in sorted_content_list:
                tm.broadcast_tx_async(json.dumps(item[0], sort_keys=True))
                print("[INFO]TM broadcast_tx_async %s." % item[0])
                cache.set_txn_as_synced(item[1], tag)
                print("[INFO]IOTA setTxnAsSynced %s." % item[1])

        else:  # len(content_dict) == 1
            for key in content_dict:
                tm.broadcast_tx_async(json.dumps(content_dict[key], sort_keys=True))
                print("[INFO]TM broadcast_tx_async %s." % content_dict[key])
                cache.set_txn_as_synced(key, tag)
                print("[INFO]IOTA setTxnAsSynced %s." % key)


if __name__ == "__main__":
    interval_work()
