from aloe import world, step
from iota import Transaction
from util import static_vals as static
from util.test_logic import api_test_logic as api_utils
from util.transaction_bundle_logic import bundle_scenario_setup, transaction_logic as transactions
from util.milestone_logic import milestones
from time import sleep

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
    
@step(r'Then a value bundle which moves funds back and forth from an address is generated referencing the previous transaction with:')
def fake_value_transaction(step):
    """
    Creates a bundle that both receives and sends value between 2 addresses.
    This makes the total ledger change zero
    :param step.hashes: A gherkin table present in the feature file specifying the
                        arguments and the associated type.
    """
    
    node = world.config['nodeId']
    previous = world.responses['evaluate_and_send'][node][0]
    
    seed = get_step_value(step, "seed")[0]
    api = api_utils.prepare_api_call(node, seed=seed)
    
    logger.info('Finding Transactions')
    gtta_transactions = api.get_transactions_to_approve(depth=3)

    trunk = previous
    branch = gtta_transactions['branchTransaction']
    
    value = int(get_step_value(step, "value"))
    tag = get_step_value(step, "tag")
    
    bundle = bundle_scenario_setup.create_fake_transfer_bundle(api, seed, tag, value)
    
    argument_list = {'trunk_transaction': trunk, 'branch_transaction': branch,
                     'trytes': bundle.as_tryte_strings(), 'min_weight_magnitude': 14}

    bundle = transactions.attach_store_and_broadcast(api, argument_list)
    transaction_trytes = bundle.get('trytes')
    transaction_hash = Transaction.from_tryte_string(transaction_trytes[0])
    set_previous_transaction(node, [transaction_hash.hash])

@step(r'a double spend is generated referencing the previous transaction with:')
def create_double_spent(step):
    """
    Creates two bundles which both try to spend the same address.
    This test fails if they are both confirmed
    :param step.hashes: A gherkin table present in the feature file specifying the
                        arguments and the associated type.
    """
    node = world.config['nodeId']
    previous = world.responses['evaluate_and_send'][node][0]
    seed = get_step_value(step, "seed")
    api = api_utils.prepare_api_call(node, seed=seed)
    
    tag = get_step_value(step, "tag")[0]
    value = int(get_step_value(step, "value"))
    
    response = api.get_inputs(start=0, stop=1, threshold=0, security_level=2)
    addressFrom = response['inputs'][0]
    
    bundles = bundle_scenario_setup.create_double_spend_bundles(seed, addressFrom, static.DOUBLE_SPEND_ADDRESSES[0], static.DOUBLE_SPEND_ADDRESSES[1], tag, value)
        
    logger.info('Finding Transactions')
    gtta_transactions = api.get_transactions_to_approve(depth=3)
    trunk1 = previous
    branch1 = gtta_transactions['branchTransaction']
    trunk2 = previous
    branch2 = gtta_transactions['trunkTransaction']
    
    argument_list = {'trunk_transaction': trunk1, 'branch_transaction': branch1,
                     'trytes': bundles[0].as_tryte_strings(), 'min_weight_magnitude': 14}
    firstDoubleSpend = Transaction.from_tryte_string( transactions.attach_store_and_broadcast(api, argument_list).get('trytes')[0] )
                     
    argument_list = {'trunk_transaction': trunk2, 'branch_transaction': branch2,
                     'trytes': bundles[1].as_tryte_strings(), 'min_weight_magnitude': 14}
    secondDoubleSpend = Transaction.from_tryte_string( transactions.attach_store_and_broadcast(api, argument_list).get('trytes')[0] )
         
    set_previous_transaction(node, [firstDoubleSpend.hash])
    set_world_object(node, "firstDoubleSpend", [firstDoubleSpend.hash])

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

    set_world_object(node, 'inconsistentTransactions', transaction_hash.hash)


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
    set_previous_transaction(node, bundlehash['hashes'][0])


@step(r'a transaction is issued referencing the previous transaction')
def reference_stitch_transaction(step):
    node = world.config['nodeId']
    stitch = world.responses['previousTransaction'][node]
    referencing_address = static.REFERENCING_ADDRESS

    api = api_utils.prepare_api_call(node)

    transaction_bundle = transactions.create_transaction_bundle(referencing_address, 'REFERENCE9TAG', 0)
    branch =  api.get_transactions_to_approve(depth=3)['branchTransaction']
    options = {'trunk_transaction': stitch[0], 'branch_transaction': branch, 'trytes':
        transaction_bundle.as_tryte_strings(), 'min_weight_magnitude': 9}

    transaction = transactions.attach_store_and_broadcast(api, options)
    transaction_trytes = transaction.get('trytes')
    transaction_hash = Transaction.from_tryte_string(transaction_trytes[0])

    set_previous_transaction(node, [transaction_hash.hash])


@step(r'"(\d+)" transactions? (?:is|are) issued on "([^"]+)" with:')
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
    :param step.hashes: Contains a reference pointer for list of transactions to get a reference from
    """
    node = world.config['nodeId']
    address = static.TEST_BLOWBALL_COO
    api = api_utils.prepare_api_call(node)

    reference_transaction = transactions.fetch_transaction_from_list(step.hashes, node)
    logger.info('Issuing milestone {}'.format(index))
    milestone = milestones.issue_milestone(address, api, index, reference_transaction)

    milestones.update_latest_milestone(world.config, node, milestone)


@step(r'the next (\d+) milestones are issued')
def issue_several_milestones(step, num_milestones):
    node = world.config['nodeId']
    api = api_utils.prepare_api_call(node)

    latest_milestone_index = int(api.get_node_info()['latestSolidSubtangleMilestoneIndex'])
    logger.info('Latest Milestone Index: {}'.format(latest_milestone_index))
    start_index = latest_milestone_index + 1
    end_index = start_index + int(num_milestones)

    for index in range(start_index, end_index):
        issue_a_milestone(step, index, node)
        #Give node a moment to update solid milestone
        wait_for_update(index, api)


@step(r'milestone (\d+) is issued on "([^"]+)"')
def issue_a_milestone(step, index, node):
    """
    This method issues a milestone with a given index.

    :param index: The index of the milestone you are issuing
    :param node: The node that the milestone will be attached to
    """
    world.config['nodeId'] = node
    address = static.TEST_BLOWBALL_COO
    api = api_utils.prepare_api_call(node)

    logger.info('Issuing milestone {}'.format(index))
    milestone = milestones.issue_milestone(address, api, index)

    if 'latestMilestone' not in world.config:
        world.config['latestMilestone'] = {}

    milestone_hash = Transaction.from_tryte_string(milestone['trytes'][0]).hash
    milestone_hash2 = Transaction.from_tryte_string(milestone['trytes'][1]).hash
    world.config['latestMilestone'][node] = [milestone_hash, milestone_hash2]

def set_previous_transaction(node, txHash):
   set_world_object(node, 'previousTransaction', txHash)

def set_world_object(node, objectName, value):
    if objectName not in world.responses:
        world.responses[objectName] = {}
    world.responses[objectName][node] = value

def get_step_value(step, key_name):
    for arg_index, arg in enumerate(step.hashes):
        if arg['keys'] == key_name :
            if arg['type'] == "staticValue" or arg['type'] == "staticList":
                return getattr(static, arg['values'])
            else:
                return arg['values']
    return 0

def wait_for_update(index, api):
    updated = False
    i = 0
    while i < 10:
        node_info = api.get_node_info()
        if node_info['latestSolidSubtangleMilestoneIndex'] == index:
            updated = True
            break
        i += 1;
        sleep(1)

    assert updated is True, "The node was unable to update to index {}".format(index)
