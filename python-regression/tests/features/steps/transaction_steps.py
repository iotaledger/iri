from aloe import world, step
from iota import Transaction, Address
from util import static_vals as static
from util.test_logic import api_test_logic as api_utils
from util.transaction_bundle_logic import transaction_logic as transactions
from util.threading_logic import pool_logic as pool
from util.milestone_logic import milestones
from copy import deepcopy

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
    arg_list = step.hashes
    world.config['nodeId'] = node
    world.config['apiCall'] = 'attachToTangle'
    seed = ""
    is_value_transaction = False

    for arg in arg_list:
        if arg['keys'] == 'seed' and arg['type'] == 'staticList':
            seed = arg['values']
            arg['type'] = 'ignore'
    if seed != "":
        api = api_utils.prepare_api_call(node, seed=seed)
        is_value_transaction = True
    else:
        api = api_utils.prepare_api_call(node)

    options = {}
    api_utils.prepare_options(arg_list, options)

    transaction_args = {}
    for key in options:
        transaction_args[key] = options.get(key)
    api_utils.prepare_transaction_arguments(transaction_args)

    transaction = transactions.create_and_attach_transaction(api, is_value_transaction, transaction_args)
    api.broadcast_and_store(transaction.get('trytes'))

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
    trunk = getattr(static, "NULL_HASH")
    branch = trunk
    trytes = getattr(static, "EMPTY_TRANSACTION_TRYTES")

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
    stitching_address = getattr(static, "STITCHING_ADDRESS")

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
    referencing_address = getattr(static, "REFERENCING_ADDRESS")

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
    transaction_hashes = []
    stored_values = ()
    for iteration in range(int(num_transactions)):
        if iteration == 0:
            stored_values = deepcopy(step.hashes)
            stored_value_list = {}
            for index, value in enumerate(stored_values):
                stored_value_list[index] = value

        for arg_index, arg in enumerate(step.hashes):
            if arg['keys'] == "seed" and (arg['type'] == "staticList" or arg['type'] == "ignore"):
                if arg['type'] != stored_value_list[arg_index]['type']:
                    arg['type'] = stored_value_list[arg_index]['type']

                if arg['values'] != stored_value_list[arg_index]['values']:
                    arg['values'] = stored_value_list[arg_index]['values']

                new_address = getattr(static, arg['values'])
                arg['values'] = new_address[iteration]
        logger.info('Sending Transaction {}'.format(iteration + 1))
        transaction = generate_transaction_and_attach(step, node)
        transaction_hash = Transaction.from_tryte_string(transaction['trytes'][0]).hash
        transaction_hashes.append(transaction_hash)

    setattr(static, "ATTACHED_TRANSACTIONS", transaction_hashes)
    logger.info("Transactions generated and stored")


@step(r'a milestone is issued with index (\d+) and referencing a hash from "([^"]+)"')
def issue_a_milestone_with_reference(step, index, static_variable):
    """
    This method issues a milestone with a given index, and stores that milestone hash in staticVals.py

    :param index: The index of the milestone you are issuing
    """
    node = world.config['nodeId']
    address = getattr(static, "TEST_BLOWBALL_COO")

    api = api_utils.prepare_api_call(node)

    transactions = getattr(static, static_variable)
    reference_transaction = transactions[len(transactions) - 1]
    logger.info('Issuing milestone {}'.format(index))
    milestone = milestones.issue_milestone(address, api, index, reference_transaction)

    if 'latestMilestone' not in world.config:
        world.config['latestMilestone'] = {}

    milestone_hash = Transaction.from_tryte_string(milestone['trytes'][0]).hash
    milestone_hash2 = Transaction.from_tryte_string(milestone['trytes'][1]).hash
    world.config['latestMilestone'][node] = [milestone_hash, milestone_hash2]