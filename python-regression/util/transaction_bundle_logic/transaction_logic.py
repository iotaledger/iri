from iota import ProposedBundle,ProposedTransaction,Address,Tag
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def create_transaction_bundle(address, tag, value):
    """
    Create a generic transaction bundle.

    :param address: The address that will be associated with the transaction
    :param tag: The tag that will be associated with the transaction
    :param value: The value of the transaction
    """
    txn = ProposedTransaction(
        address=Address(address),
        tag=Tag(tag),
        value=value
    )
    bundle = ProposedBundle()
    bundle.add_transaction(txn)
    bundle.finalize()

    return bundle


def create_and_attach_transaction(api, arg_list):
    """
    Create a transaction and attach it to the tangle.

    :param api: The api target you would like to make the call to
    :param arg_list: The argument list (dictionary) for the transaction
    :return sent: The return value for the attachToTangle call (contains the attached transaction trytes)
    """
    transaction = ProposedTransaction(**arg_list)

    bundle = ProposedBundle()
    bundle.add_transaction(transaction)
    bundle.finalize()
    trytes = str(bundle[0].as_tryte_string())

    gtta = api.get_transactions_to_approve(depth=3)
    branch = str(gtta['branchTransaction'])
    trunk = str(gtta['trunkTransaction'])

    sent = api.attach_to_tangle(trunk, branch, [trytes], 9)
    return sent


def attach_store_and_broadcast(api, args_list):
    """
    Attach, store and broadcast the given transaction with the given arguments.

    :param api: The api target you would like to make the call to
    :param args_list: The argument list (dictionary) for the transaction
    :return transaction: The return value for the attachToTangle call (contains the attached transaction trytes)
    """
    transaction = api.attach_to_tangle(**args_list)
    api.store_transactions(transaction.get('trytes'))
    api.broadcast_transactions(transaction.get('trytes'))
    logger.info('Done attaching, storing and broadcasting')
    return transaction
