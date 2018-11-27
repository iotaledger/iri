#from unittest.mock import MagicMock
import mock
from mock import MagicMock
import unittest
from iota import TransactionHash, Iota, Address, ProposedTransaction, Tag, Transaction, TryteString, TransactionTrytes, ProposedBundle


from iota_cache import IotaCache

# test Count class
class TestIotaCache(unittest.TestCase):

    def setUp(self):
        self.transfers = []

    def mock_send_transfer(self, depth, transfers):
        transfers[0].hash = transfers[0].message
        self.transfers.append(transfers)
        return 'OK'

    def mock_getApprovedTxns(self, tag):
        ret = []
        for txn in self.transfers:
            if txn[0].tag == Tag(tag):
                ret.append(txn[0].hash)
        return ret

    def mock_find_transactions(self, bundles, address, tags, approvees):
        ret = {'hashes' : [] }
        for txn in self.transfers:
            if txn[0].tag == Tag(tags[0]):
                ret['hashes'].append(txn[0].hash)
        return ret

    def mock_get_trytes(self, txnHashesAll):
        ret = {'trytes' :[]}
        for txnHash in txnHashesAll:
            for txn in self.transfers:
                if txn[0].hash == txnHash:
                    bundle = ProposedBundle()
                    bundle.add_transaction(txn[0])
                    bundle.finalize()
                    ret['trytes'].append(bundle.as_tryte_strings()[0])
        return ret

    def test_cacheTxnInTangle(self):
        cache = IotaCache()
        cache.api.get_new_addresses = MagicMock(return_value={'addresses' : [b'KA9XBBDIYSTVOTEMAUL9JBYKWDN9WAJQWJO9GQYBNOJBSVRBESQXYETFDCSFFWL9PPSPWWAGSNMQSSMBC']})
        cache.api.send_transfer = MagicMock(return_value='OK')
        ret = cache.cacheTxnInTangle('addr1', 'TAG')
        self.assertEqual(ret, 'OK')

    def test_getNonConsumedTxns(self):
        cache = IotaCache()
        cache.api.get_new_addresses = MagicMock(return_value={'addresses' : [b'KA9XBBDIYSTVOTEMAUL9JBYKWDN9WAJQWJO9GQYBNOJBSVRBESQXYETFDCSFFWL9PPSPWWAGSNMQSSMBC']})
        cache.api.send_transfer = MagicMock(side_effect=self.mock_send_transfer)
        ret = cache.cacheTxnInTangle('addr1', 'TAG')
        ret = cache.cacheTxnInTangle('addr2', 'TAG')
        ret = cache.cacheTxnInTangle('addr3', 'TAG')
        ret = cache.cacheTxnInTangle('addr4', 'TAG')
        ret = cache.cacheTxnInTangle('addr5', 'TAG')
        ret = cache.cacheTxnInTangle('addr6', 'TAG')
        ret = cache.cacheTxnInTangle('addr7', 'TAG')
        ret = cache.cacheTxnInTangle('addr8', 'TAG')
        ret = cache.cacheTxnInTangle('addr3', 'TAGCONSUMED')
        ret = cache.cacheTxnInTangle('addr4', 'TAGCONSUMED')
        ret = cache.cacheTxnInTangle('addr5', 'TAGCONSUMED')
        cache.getApprovedTxns = MagicMock(side_effect=self.mock_getApprovedTxns)
        cache.api.get_trytes = MagicMock(side_effect=self.mock_get_trytes)
        cache.api.find_transactions = MagicMock(side_effect=self.mock_find_transactions)
        print cache.getNonConsumedTxns('TAG')

if __name__ == '__main__':
    unittest.main()
