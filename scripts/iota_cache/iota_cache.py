import time
from iota import Iota, Address, ProposedTransaction, Tag, Transaction, TryteString, TransactionTrytes, ProposedBundle
from six import binary_type, moves as compat, text_type


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
        self.api = Iota(self.uri, self.seed)

    def cache_txn_in_tangle(self, ipfs_addr, tag):
        api_response = self.api.get_new_addresses()
        addy = api_response['addresses'][0]
        address = binary_type(addy).decode('ascii')

        result = self.api.send_transfer(
            depth=3,
            transfers=[
                ProposedTransaction(
                    address=Address(address),
                    value=0,
                    tag=Tag(tag),
                    message=TryteString.from_string(ipfs_addr),
                ),
            ],
        )
        return result

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
        txn_hashes_all = self.get_approved_txns(tag)
        txn_trytes_all = self.api.get_trytes(txn_hashes_all)
        txn_hashes_consumed = self.api.find_transactions(None, None, [tag+b"CONSUMED"], None)
        txn_trytes_consumed = self.api.get_trytes(txn_hashes_consumed['hashes'])
        consumedSet = []
        ret = []
        for txnTrytes in txn_trytes_consumed['trytes']:
            txn = Transaction.from_tryte_string(txnTrytes)
            consumedSet.append(txn.signature_message_fragment)
        for txnTrytes in txn_trytes_all['trytes']:
            txn = Transaction.from_tryte_string(txnTrytes)
            if txn.signature_message_fragment not in consumedSet:
                msgTryte = txn.signature_message_fragment.decode()
                ret.append(msgTryte)
        return ret

    def set_txn_as_synced(self, ipfs_addr, tag):
        result = self.cache_txn_in_tangle(ipfs_addr, tag+b"CONSUMED")
        return result

