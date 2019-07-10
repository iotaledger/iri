from aloe import world, step
from iota import Transaction
from util import static_vals as static
from util.test_logic import api_test_logic as api_utils
from util.transaction_bundle_logic import transaction_logic as transactions
from util.threading_logic import pool_logic as pool
from util.milestone_logic import milestones

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@step(r'a transaction is generated and attached on "([^"]+)" with:')
def generate_transaction_and_attach(step, node):
    """
    Creates a transaction with the specified arguments.

    :param node: The node that the transaction will be generated on.
    :param step.hashes: A gherkin table present in the feature file specifying the
                        arguments and the associated type.
    """
    world.config['nodeId'] = node
    world.config['apiCall'] = 'attachToTangle'

    seed = transactions.check_for_seed(step.hashes)
    api = api_utils.prepare_api_call(node, seed=seed)
    transaction = transactions.evaluate_and_send(api, seed, step.hashes)

    assert len(transaction['trytes']) > 0, "Transaction was not created correctly"
    world.responses['attachToTangle'] = {}
    world.responses['attachToTangle'][node] = transaction

    setattr(static, "TEST_STORE_TRANSACTION", transaction.get('trytes'))
    return transaction


@step(r'an inconsistent transaction is generated on "([^"]+)"')
def create_inconsistent_transaction(step, node):
    """
    Creates an inconsistent transaction by generating a zero value transaction that references
    a non-existent transaction as its branch and trunk, thus not connecting with any other part
    of the tangle.

    :param node: The node that the transaction will be generated on.
    """
    world.config['nodeId'] = node
    api = api_utils.prepare_api_call(node)
    trunk = static.NULL_HASH
    branch = trunk
    trytes = static.EMPTY_TRANSACTION_TRYTES

    argument_list = {'trunk_transaction': trunk, 'branch_transaction': branch,
                     'trytes': [trytes], 'min_weight_magnitude': 14}

    transaction = transactions.attach_store_and_broadcast(api, argument_list)
    transaction_trytes = transaction.get('trytes')
    transaction_hash = Transaction.from_tryte_string(transaction_trytes[0])

    if 'inconsistentTransactions' not in world.responses:
        world.responses['inconsistentTransactions'] = {}
    world.responses['inconsistentTransactions'][node] = transaction_hash.hash


@step(r'a stitching transaction is issued on "([^"]*)" with the tag "([^"]*)"')
def issue_stitching_transaction(step, node, tag):
    world.config['nodeId'] = node
    world.config['stitchTag'] = tag

    api = api_utils.prepare_api_call(node)
    logger.info('Finding Transactions')
    side_tangle_transaction = static.SIDE_TANGLE_TRANSACTIONS[0]
    gtta_transactions = api.get_transactions_to_approve(depth=3)

    trunk = side_tangle_transaction
    branch = gtta_transactions['branchTransaction']
    stitching_address = static.STITCHING_ADDRESS

    logger.debug('Trunk: ' + str(trunk))
    logger.debug('Branch: ' + str(branch))

    bundle = transactions.create_transaction_bundle(stitching_address, tag, 0)
    trytes = bundle[0].as_tryte_string()
    sent_transaction = api.attach_to_tangle(trunk, branch, [trytes], 14)
    api.broadcast_and_store(sent_transaction.get('trytes'))

    # Finds transaction hash and stores it in world
    bundlehash = api.find_transactions(bundles=[bundle.hash])
    if 'previousTransaction' not in world.responses:
        world.responses['previousTransaction'] = {}
    world.responses['previousTransaction'][node] = bundlehash['hashes'][0]


@step(r'a transaction is issued referencing the previous transaction')
def reference_stitch_transaction(step):
    node = world.config['nodeId']
    stitch = world.responses['previousTransaction'][node]
    referencing_address = static.REFERENCING_ADDRESS

    api = api_utils.prepare_api_call(node)

    transaction_bundle = transactions.create_transaction_bundle(referencing_address, 'REFERENCE9TAG', 0)

    def transactions_to_approve(node, arg_list):
        response = api_utils.fetch_call('getTransactionsToApprove', arg_list['api'], {'depth': 3})
        arg_list['responses']['getTransactionsToApprove'][node] = response
        return response

    gtta_results = pool.start_pool(transactions_to_approve, 1, {node: {'api': api, 'responses': world.responses}})
    branch = pool.fetch_results(gtta_results[0], 30)
    options = {'trunk': stitch, 'branch': branch, 'trytes': transaction_bundle.as_tryte_strings(),
               'min_weight_magnitude': 9}

    def make_transaction(node, arg_list):
        response = transactions.attach_store_and_broadcast(arg_list['api'], options)
        arg_list['responses']['attachToTangle'][node] = response
        return response

    transaction_results = pool.start_pool(make_transaction, 1, {node: {'api': api, 'responses': world.responses}})
    pool.fetch_results(transaction_results[0], 30)


@step(r'"(\d+)" transactions are issued on "([^"]+)" with:')
def issue_multiple_transactions(step, num_transactions, node):
    transactions_to_store = []
    world.responses['evaluate_and_send'] = {}
    world.config['nodeId'] = node
    # Placeholder values for seed if present
    seed_value = ""
    seed_type = ""

    for arg_index, arg in enumerate(step.hashes):
        if arg['keys'] == "seed" and arg['type'] == "staticList":
            seed_value = arg['values']
            seed_type = arg['type']

    for iteration in range(int(num_transactions)):
        seed = ""
        if seed_value != "" and seed_type == "staticList":
            seed = getattr(static, seed_value)[iteration]

        api = api_utils.prepare_api_call(node, seed=seed)

        logger.info('Sending Transaction {}'.format(iteration + 1))
        transaction = transactions.evaluate_and_send(api, seed, step.hashes)
        transaction_hash = Transaction.from_tryte_string(transaction.get('trytes')[0]).hash
        transactions_to_store.append(transaction_hash)

    world.responses['evaluate_and_send'][node] = transactions_to_store
    logger.info("Transactions generated and stored")


@step(r'a milestone is issued with index (\d+) and references')
def issue_a_milestone_with_reference(step, index):
    """
    This method issues a milestone with a given index and reference transaction. The input transaction pointer should
    always have the key "transactions", but may be a pointer to either a staticValue list stored in staticValues.py, or
    a responseList for "findTransactions".

    :param index: The index of the milestone you are issuing
    """
    node = world.config['nodeId']
    address = static.TEST_BLOWBALL_COO
    api = api_utils.prepare_api_call(node)

    reference_transaction = transactions.fetch_transaction_from_list(step.hashes, node)
    logger.info('Issuing milestone {}'.format(index))
    milestone = milestones.issue_milestone(address, api, index, reference_transaction)

    if 'latestMilestone' not in world.config:
        world.config['latestMilestone'] = {}

    milestone_hash = Transaction.from_tryte_string(milestone['trytes'][0]).hash
    milestone_hash2 = Transaction.from_tryte_string(milestone['trytes'][1]).hash
    world.config['latestMilestone'][node] = [milestone_hash, milestone_hash2]
