from __future__ import print_function
import sys
sys.path.append("..")
import time
from iota import Iota, Address, ProposedTransaction, Tag, Transaction, TryteString, TransactionTrytes, ProposedBundle, Nonce, BundleHash,TransactionHash, Fragment
from six import binary_type, moves as compat, text_type
from iota_api.api import attachToTangle, storeMessage, getBalance, addNeighbors, getBlockContent, getDAG, getUTXO, getTotalOrder

class IotaCache(object):

    def __init__(self, uri=None, seed=None):
        if not uri:
            self.uri = "http://localhost:14700"
        else:
            self.uri = uri
        if not seed:
            self.seed = 'EBZYNR9YVFIOAZUPQOLRZXPPPIKRCJ9EJKVCXMYVLMNOCCOPYPJKCWUZNLJZZZZWTMVQUXZFYLVLZXJ9Q'
        else:
            self.seed = seed
        self.api = Iota(self.uri, self.seed, testnet=True)
        self.mwm = 1
        self.depth = 15

    def cache_txn_in_tangle_sdk(self, ipfs_addr, tag):
        api_response = self.api.get_new_addresses()
        addy = api_response['addresses'][0]
        address = binary_type(addy).decode('ascii')

        result = self.api.send_transfer(
            depth=self.depth,
            transfers=[
                ProposedTransaction(
                    address=Address(address),
                    value=0,
                    tag=Tag(tag),
                    message=TryteString.from_string(ipfs_addr),
                ),
            ],
            min_weight_magnitude=self.mwm,
        )
        return result

    def cache_txn_in_tangle_simple(self, data, tag):
        address = "JVSVAFSXWHUIZPFDLORNDMASGNXWFGZFMXGLCJQGFWFEZWWOA9KYSPHCLZHFBCOHMNCCBAGNACPIGHVYX"
        txns = self.api.get_transactions_to_approve(self.depth)
        tr = self.api.get_trytes([txns[u'branchTransaction']])
        txn = Transaction.from_tryte_string(tr[u'trytes'][0], txns[u'branchTransaction'])
        txn.trunk_transaction_hash = txns[u'trunkTransaction']
        txn.branch_transaction_hash = txns[u'branchTransaction']
        txn.tag = Tag(TryteString.from_string(tag))
        txn.signature_message_fragment = Fragment(TryteString.from_bytes(data))
        attach_trytes = attachToTangle(self.uri, txns[u'trunkTransaction'].__str__(), txns[u'branchTransaction'].__str__(), 1, txn.as_tryte_string().__str__())
        res = self.api.broadcast_and_store(attach_trytes[u'trytes'])
        return res

    def cache_txn_in_tangle_message(self, data, tag):
        #address = "JVSVAFSXWHUIZPFDLORNDMASGNXWFGZFMXGLCJQGFWFEZWWOA9KYSPHCLZHFBCOHMNCCBAGNACPIGHVYX"
        address = "14dD6ygPi5WXdwwBTt1FBZK3aD8uDem1FY"
        res = storeMessage(self.uri, address, data, tag)
        return res

    def get_balance(self, coin_type, account):
        address = "JVSVAFSXWHUIZPFDLORNDMASGNXWFGZFMXGLCJQGFWFEZWWOA9KYSPHCLZHFBCOHMNCCBAGNACPIGHVYX"
        res = getBalance(self.uri, address, coin_type, account)
        return res

    def get_approved_txns(self, tag):
        ret = []
        transactions = self.api.find_transactions(None, None, [tag], None)
        if len(transactions['hashes']) == 0:
            return ret
        tips = self.api.get_tips()
        states = self.api.get_inclusion_states(transactions['hashes'], tips['hashes'])
        i = 0
        for txn in transactions['hashes']:
            if states['states'][i] is True:
                ret.append(txn)
        return ret

    def get_non_consumed_txns(self, tag):
        ret = []
        txn_hashes_all = self.get_approved_txns(tag)
        if len(txn_hashes_all) == 0:
            return ret
        txn_trytes_all = self.api.get_trytes(txn_hashes_all)
        consumedSet = []
        txn_hashes_consumed = self.api.find_transactions(None, None, [tag+b"CONSUMED"], None)
        if len(txn_hashes_consumed['hashes']) != 0:
            txn_trytes_consumed = self.api.get_trytes(txn_hashes_consumed['hashes'])
            i=0
            for txnTrytes in txn_trytes_consumed['trytes']:
                txn = Transaction.from_tryte_string(txnTrytes, txn_hashes_consumed['hashes'][i])
                i+=1
                consumedSet.append(txn.signature_message_fragment)
        i=0
        for txnTrytes in txn_trytes_all['trytes']:
            txn = Transaction.from_tryte_string(txnTrytes, txn_hashes_all[i])
            i+=1
            if txn.signature_message_fragment not in consumedSet:
                msgTryte = txn.signature_message_fragment.decode()
                ret.append(msgTryte)

        return ret

    def set_txn_as_synced(self, ipfs_addr, tag):
        result = self.cache_txn_in_tangle_sdk(ipfs_addr, tag+b"CONSUMED")
        return result

    def add_neighbors(self, uris):
        res = addNeighbors(self.uri, uris)
        return res

    def get_block_content(self, hashes):
        res = getBlockContent(self.uri, hashes)
        return res

    def get_dag(self, dag_type):
        res = getDAG(self.uri, dag_type)
        return res

    def get_utxo(self, dag_type):
        res = getUTXO(self.uri, dag_type)
        return res

    def get_total_order(self):
        res = getTotalOrder(self.uri)
        return res
