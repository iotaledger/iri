from iota import ProposedTransaction,ProposedBundle,Address,Tag
from util.test_logic import api_test_logic

tests = api_test_logic


def create_transaction_bundle(address, tag, value):
    txn = ProposedTransaction(
        address = Address(address),
        tag = Tag(tag),
        value = value
        )
    bundle = ProposedBundle()
    bundle.add_transaction(txn)
    bundle.finalize()
    
    return bundle