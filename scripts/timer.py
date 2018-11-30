import json
import datetime
import threading
import commands
import ConfigParser
from iota_cache import IotaCache
from tag_helper import TagHelper
from tm_rpc_client import Tendermint


cf = ConfigParser.ConfigParser()
cf.read("conf")
iota_addr = cf.get("iota", "addr")
iota_seed = cf.get("iota", "seed")
cache = IotaCache(iota_addr, iota_seed)

tm_addr = cf.get("tendermint", "addr")
tm = Tendermint(tm_addr)


def interval_work():
    print("...work work work...")
    now = datetime.datetime.now()
    print(now)

    global timer_thread
    timer_thread = threading.Timer(300, interval_work)
    timer_thread.start()

    tag = TagHelper.get_current_tag()
    push_and_sync(tag)

    # 00:00:00 -> 00:04:59, check previous tag
    if now.hour == 0 and 0 <= now.minute <= 4:
        tag = TagHelper.get_previous_tag()
        push_and_sync(tag)

    print("...work end...")


def push_and_sync(tag):

    result = cache.get_non_consumed_txns(tag)
    content_dict = {}

    for ipfs_addr in result:
        content = commands.getoutput(' '.join(['ipfs', 'cat', ipfs_addr]))
        content_dict[ipfs_addr] = content

    if len(content_dict) > 0:

        if len(content_dict) > 1:
            content_list = []
            for key in content_dict:
                content_list.append(json.loads(content_dict[key]))
            json_str = json.dumps({"txs": content_list}, sort_keys=True)
            tm.broadcast_txs_async(json_str)
            print("[INFO]TM broadcast_txs_async %s." % json_str)
            for key in content_dict:
                cache.set_txn_as_synced(key, tag)
                print("[INFO]IOTA setTxnAsSynced %s." % key)

        else:  # len(content_dict) == 1
            for key in content_dict:
                tm.broadcast_tx_async(content_dict[key])
                print("[INFO]TM broadcast_tx_async %s." % content_dict[key])
                cache.set_txn_as_synced(key, tag)
                print("[INFO]IOTA setTxnAsSynced %s." % key)


if __name__ == "__main__":
    interval_work()

