from iota import ProposedTransaction, ProposedBundle, Tag, Address
from util import conversion as converter

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def issue_milestone(address, api, index, *reference_transaction):
    txn1 = ProposedTransaction(
        address=Address(address),
        value=0
    )

    txn2 = ProposedTransaction(
        address=Address(address),
        value=0
    )

    txn1._legacy_tag = Tag(converter.int_to_trytestring(index, 9))
    txn2._legacy_tag = Tag(converter.int_to_trytestring(index, 9))

    bundle = ProposedBundle()
    bundle.add_transaction(txn1)
    bundle.add_transaction(txn2)
    bundle.finalize()

    tips = api.get_transactions_to_approve(depth=3)
    trunk = tips['trunkTransaction']
    if reference_transaction:
        branch = reference_transaction[0]
    else:
        branch = tips['branchTransaction']

    logger.info('Branch: {}'.format(branch))
    logger.info('Trunk: {}'.format(trunk))

    bundle_trytes = bundle.as_tryte_strings()

    milestone = api.attach_to_tangle(trunk, branch, bundle_trytes, 9)
    api.broadcast_and_store(milestone['trytes'])

    return milestone
