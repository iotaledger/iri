import sys
sys.path.append("..")
import mock
from mock import MagicMock
import unittest
from iota import Iota, Tag, ProposedBundle
from iota_cache import IotaCache


class TestIotaCache(unittest.TestCase):

    def setUp(self):
        self.transfers = []

    def mock_send_transfer(self, depth, transfers, min_weight_magnitude):
        transfers[0].hash = transfers[0].message
        self.transfers.append(transfers)
        return 'OK'

    def mock_get_approved_txns(self, tag):
        ret = []
        for txn in self.transfers:
            if txn[0].tag == Tag(tag):
                ret.append(txn[0].hash)
        return ret

    def mock_find_transactions(self, bundles, address, tags, approvees):
        ret = {'hashes': []}
        for txn in self.transfers:
            if txn[0].tag == Tag(tags[0]):
                ret['hashes'].append(txn[0].hash)
        return ret

    def mock_get_trytes(self, txnHashesAll):
        ret = {'trytes': []}
        for txnHash in txnHashesAll:
            for txn in self.transfers:
                if txn[0].hash == txnHash:
                    bundle = ProposedBundle()
                    bundle.add_transaction(txn[0])
                    bundle.finalize()
                    ret['trytes'].append(bundle.as_tryte_strings()[0])
                    break
        return ret

    def test_cache_txn_in_tangle(self):
        cache = IotaCache()
        cache.api.get_new_addresses = MagicMock(
            return_value={
                'addresses' : [b'KA9XBBDIYSTVOTEMAUL9JBYKWDN9WAJQWJO9GQYBNOJBSVRBESQXYETFDCSFFWL9PPSPWWAGSNMQSSMBC']
            }
        )
        cache.api.send_transfer = MagicMock(return_value='OK')
        ret = cache.cache_txn_in_tangle_sdk('addr1', 'TAG')
        self.assertEqual(ret, 'OK')

    def test_get_non_consumed_txns(self):
        cache = IotaCache()
        cache.api.get_new_addresses = MagicMock(
            return_value={
                'addresses' : [b'KA9XBBDIYSTVOTEMAUL9JBYKWDN9WAJQWJO9GQYBNOJBSVRBESQXYETFDCSFFWL9PPSPWWAGSNMQSSMBC']
            }
        )
        cache.api.send_transfer = MagicMock(side_effect=self.mock_send_transfer)
        cache.cache_txn_in_tangle_sdk('addr1', 'TAG')
        cache.cache_txn_in_tangle_sdk('addr2', 'TAG')
        cache.cache_txn_in_tangle_sdk('addr3', 'TAG')
        cache.cache_txn_in_tangle_sdk('addr4', 'TAG')
        cache.cache_txn_in_tangle_sdk('addr5', 'TAG')
        cache.cache_txn_in_tangle_sdk('addr6', 'TAG')
        cache.cache_txn_in_tangle_sdk('addr7', 'TAG')
        cache.cache_txn_in_tangle_sdk('addr8', 'TAG')
        cache.set_txn_as_synced('addr3', 'TAG')
        cache.set_txn_as_synced('addr4', 'TAG')
        cache.set_txn_as_synced('addr5', 'TAG')
        cache.get_approved_txns = MagicMock(side_effect=self.mock_get_approved_txns)
        cache.api.get_trytes = MagicMock(side_effect=self.mock_get_trytes)
        cache.api.find_transactions = MagicMock(side_effect=self.mock_find_transactions)
        ret = cache.get_non_consumed_txns('TAG')
        self.assertEqual(len(ret), 5)


if __name__ == '__main__':
    unittest.main()

