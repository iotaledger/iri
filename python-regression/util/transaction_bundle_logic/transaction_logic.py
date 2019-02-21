from iota import ProposedBundle, ProposedTransaction, Address, Tag
from util import static_vals as static
from util.test_logic import api_test_logic as api_utils
from util.test_logic import value_fetch_logic as value_fetch
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


def create_and_attach_transaction(api, value_transaction, arg_list, *reference):
    """
    Create a transaction and attach it to the tangle.

    :param api: The api target you would like to make the call to
    :param value_transaction: A bool to determine if this transaction is a value or zero value transaction
    :param arg_list: The argument list (dictionary) for the transaction
    :return sent: The return value for the attachToTangle call (contains the attached transaction trytes)
    """
    transaction = ProposedTransaction(**arg_list)

    if value_transaction:
        inputs = api.get_inputs(start=0, stop=10, threshold=0)
        prepared_transaction = api.prepare_transfer(
            transfers=[transaction],
            inputs=[inputs['inputs'][0]],
            change_address=Address(static.TEST_EMPTY_ADDRESS)
        )
    else:
        prepared_transaction = api.prepare_transfer(
            transfers=[transaction]
        )

    gtta = api.get_transactions_to_approve(depth=3)
    trunk = str(gtta['trunkTransaction'])
    if reference:
        branch = reference[0]
    else:
        branch = str(gtta['branchTransaction'])

    sent = api.attach_to_tangle(trunk, branch, prepared_transaction['trytes'], 9)
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


def check_for_seed(arg_list):
    """
    Checks the given argument list for a seed, if none is provided, returns an empty string

    :param arg_list: The argument list for the step that will be searched
    :return: The seed if provided, empty string if not
    """
    seed = ""
    for arg in arg_list:
        if arg['keys'] == 'seed' and arg['type'] == 'staticList':
            seed = arg['values']

    return seed


def fetch_transaction_from_list(args, node):
    """
    Fetches a reference transaction from either a static value list in the ../staticValues.py file, or from the response
    for a previous "findTransactions" call.

    :param args: The step argument list
    :param node: The current working node
    :return: The transaction to be used as a reference
    """

    options = {}
    api_utils.prepare_options(args, options)

    if args[0]['type'] == 'responseValue':
        transaction_list = value_fetch.fetch_response(args[0]['values'])
        reference_transaction = transaction_list[node][len(transaction_list) - 1]
    elif args[0]['type'] == 'staticValue':
        transaction_list = options['transactions']
        reference_transaction = transaction_list[len(transaction_list) - 1]

    assert reference_transaction, "No reference transaction found (Possibly incorrect argument type, check gherkin file"

    return reference_transaction


def evaluate_and_send(api, seed, arg_list):
    """
    Prepares a transaction for sending. If the provided seed isn't empty, it changes the bool value to be passed on to
    to the create_and_attach_transaction() function to instruct it to look for an available balance.

    :param api: The api target you would like to make the call to
    :param seed: The seed associated with the given api (This can be an empty string if none is used)
    :param arg_list: The argument list (dictionary) for the transaction
    :return: The transaction object created in the create_and_attach_transaction() function
    """
    is_value_transaction = False

    if seed != "":
        is_value_transaction = True

    options = {}
    api_utils.prepare_options(arg_list, options)
    api_utils.prepare_transaction_arguments(options)

    transaction = create_and_attach_transaction(api, is_value_transaction, options)
    api.broadcast_and_store(transaction.get('trytes'))

    return transaction
