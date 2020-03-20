from iota import ProposedTransaction, ProposedBundle, Tag, Address, Transaction
from util import conversion as converter
from util.transaction_bundle_logic import bundle_logic

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def issue_milestone(address, api, index, reference_transaction, full_reference=False):
    txn1 = ProposedTransaction(
        address=Address(address),
        value=0
    )

    txn2 = ProposedTransaction(
        address=Address(address),
        value=0
    )

    bundle = ProposedBundle()
    bundle.add_transaction(txn1)
    bundle.add_transaction(txn2)

    bundle[0]._legacy_tag = Tag(converter.int_to_trytestring(index, 9))

    bundle_logic.finalize(bundle)

    tips = api.get_transactions_to_approve(depth=3)
    trunk = tips['trunkTransaction']
    if reference_transaction:
        if full_reference:
            if len(reference_transaction) > 1:
                trunk = reference_transaction[len(reference_transaction) - 2]
            else:
                logger.error('Cannot reference 2 txs because is only 1 tx in the reference list')
        branch = reference_transaction[len(reference_transaction) - 1]
    else:
        branch = tips['branchTransaction']

    bundle_trytes = bundle.as_tryte_strings()
    milestone = api.attach_to_tangle(trunk, branch, bundle_trytes, 9)
    api.broadcast_and_store(milestone['trytes'])

    return milestone


def update_latest_milestone(config, node, milestone):
    if 'latestMilestone' not in config:
        config['latestMilestone'] = {}

    milestone_hash = Transaction.from_tryte_string(milestone['trytes'][0]).hash
    milestone_hash2 = Transaction.from_tryte_string(milestone['trytes'][1]).hash
    config['latestMilestone'][node] = [milestone_hash, milestone_hash2]
