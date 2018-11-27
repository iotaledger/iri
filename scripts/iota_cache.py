import time
import sys
from iota import Iota, Address, ProposedTransaction, Tag, Transaction, TryteString, TransactionTrytes, ProposedBundle
from six import binary_type, moves as compat, text_type


class IotaCache:
    def __init__(self, uri=None, seed=None):
        if not uri:
            self.uri = "http://localhost:14700"
        if not seed:
            self.seed = 'EBZYNR9YVFIOAZUPQOLRZXPPPIKRCJ9EJKVCXMYVLMNOCCOPYPJKCWUZNLJZZZZWTMVQUXZFYLVLZXJ9Q'
        self.api = Iota(self.uri, self.seed)

    def cacheTxnInTangle(self, IPFSAddr, tag):
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
                    message=TryteString.from_string(IPFSAddr),
                ),
            ],
        )
        return result

    def getApprovedTxns(self, tag):
        ret = []
        transactions = self.api.find_transactions(None, None, [tag], None)
        if len(transactions['hashes']) == 0:
            return ret
        tips = self.api.get_tips()
        states = self.api.get_inclusion_states(transactions['hashes'], tips['hashes'])
        i = 0
        for txn in transactions['hashes']:
            if states['states'][i] == True:
                ret.append(txn)
        return ret

    def getNonConsumedTxns(self, tag):
        txnHashesAll = self.getApprovedTxns(tag)
        print txnHashesAll
        txnTrytesAll = self.api.get_trytes(txnHashesAll)
        txnHashesConsumed = self.api.find_transactions(None, None, [tag+b"CONSUMED"], None)
        print txnHashesConsumed
        txnTrytesConsumed = self.api.get_trytes(txnHashesConsumed['hashes'])
        consumedSet = []
        ret = []
        for txnTrytes in txnTrytesConsumed['trytes']:
            txn = Transaction.from_tryte_string(txnTrytes)
            consumedSet.append(txn.signature_message_fragment)
        for txnTrytes in txnTrytesAll['trytes']:
            txn = Transaction.from_tryte_string(txnTrytes)
            if txn.signature_message_fragment not in consumedSet:
                msgTryte = txn.signature_message_fragment.decode()
                ret.append(msgTryte)
        return ret

    def setTxnAsSynced(self, IPFSAddr, tag):
        result = self.cacheTxnInTangle(IPFSAddr, tag+b"CONSUMED")
        return result
