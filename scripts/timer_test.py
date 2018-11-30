import json
from mock import MagicMock
import unittest
from tag_helper import TagHelper
from timer import push_and_sync, cache, tm


class TestTimer(unittest.TestCase):

    def setUp(self):
        pass

    def mock_get_non_consumed_txns(self, tag):
        return self.ipfs_addrs

    def mock_set_txn_as_synced(self, ipfs_addr, tag):
        pass

    def mock_broadcast_tx_async(self, tx):
        pass

    def mock_broadcast_txs_async(self, txs):
        pass

    def test_push_and_sync_for_one_item(self):

        self.ipfs_addrs = [
            "QmSRjsq7wg7njG2HwyHsRT2qUT9teWccjv65Ud9TcWG3Sz"
        ]
        self.transactions = [
            {"to": "b", "from": "a", "timestamp": "1543392247.23", "transfer": "1"}
        ]

        cache.get_non_consumed_txns = MagicMock(side_effect=self.mock_get_non_consumed_txns)
        cache.set_txn_as_synced = MagicMock(side_effect=self.mock_set_txn_as_synced)
        tm.broadcast_tx_async = MagicMock(side_effect=self.mock_broadcast_tx_async)
        tm.broadcast_txs_async = MagicMock(side_effect=self.mock_broadcast_txs_async)

        current_tag = TagHelper.get_current_tag()
        push_and_sync(current_tag)

        for ipfs_addr in self.ipfs_addrs:
            cache.set_txn_as_synced.assert_any_call(ipfs_addr, current_tag)

        tm.broadcast_tx_async.assert_any_call(json.dumps(self.transactions[0], sort_keys=True))

    def test_push_and_sync_for_more_item(self):

        self.ipfs_addrs = [
            "QmSRjsq7wg7njG2HwyHsRT2qUT9teWccjv65Ud9TcWG3Sz",
            "QmURmxLAmL5UGjXA8Vt4kak2hjXHnjZGjphScujY26SRN9",
            "QmS15xXJsbDqqhVLXor2Gb48izGEbWsxrg3F6ERtXQTCvM"
        ]
        self.transactions = [
            {"to": "b", "from": "a", "timestamp": "1543392247.23", "transfer": "1"},
            {"to": "d", "from": "c", "timestamp": "1543392284.83", "transfer": "2"},
            {"to": "f", "from": "e", "timestamp": "1543392301.58", "transfer": "3"},
        ]

        cache.get_non_consumed_txns = MagicMock(side_effect=self.mock_get_non_consumed_txns)
        cache.set_txn_as_synced = MagicMock(side_effect=self.mock_set_txn_as_synced)
        tm.broadcast_tx_async = MagicMock(side_effect=self.mock_broadcast_tx_async)
        tm.broadcast_txs_async = MagicMock(side_effect=self.mock_broadcast_txs_async)

        current_tag = TagHelper.get_current_tag()
        push_and_sync(current_tag)

        for ipfs_addr in self.ipfs_addrs:
            cache.set_txn_as_synced.assert_any_call(ipfs_addr, current_tag)

        transaction_list = []
        for transaction in self.transactions:
            transaction_list.append(transaction)
        tm.broadcast_txs_async.assert_called_once_with(json.dumps({"txs": transaction_list}, sort_keys=True))

