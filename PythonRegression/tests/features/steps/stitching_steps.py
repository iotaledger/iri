from aloe import step, world
from util.test_logic import api_test_logic as tests
from util.transaction_utils import transaction_logic as transactions
from util import static_vals as static
import threading
import random

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

world.config = {}
world.responses = {}

#TODO: Create generic errors file for all exception templating
TIMEOUT_ERROR = "Thread has not completed, request has timed out"

side_tangle_address = b'SIDE9TANGLE9999999999999999999999999999999999999999999999999999999999999999999999'
stitching_addresss = b'STITCHING9TRANSACTIONS99999999999999999999999999999999999999999999999999999999999'
referencing_address = b'REFERENCES9STITCHING9TRANSACTION9999999999999999999999999999999999999999999999999'

@step(r'a stitching transaction is issued on "([^"]*)" with the tag "([^"]*)"')
def issue_stitching_transaction(step,node,tag):
    world.config['nodeId'] = node
    world.config['stitchTag'] = tag

    api = tests.prepare_api_call(node)
    logger.info('Finding Transactions')
    side_tangle_transaction = static.SIDE_TANGLE_TRANSACTIONS[0]
    gtta_transactions = api.get_transactions_to_approve(depth=3)

    trunk = side_tangle_transaction
    branch = gtta_transactions['branchTransaction']
    
    logger.debug('Trunk: ' + str(trunk))
    logger.debug('Branch: ' + str(branch))
    
    bundle = transactions.create_transaction_bundle(stitching_addresss,tag,0)
    trytes = bundle[0].as_tryte_string()
    sent_transaction = api.attach_to_tangle(trunk, branch, [trytes], 14)
    api.broadcast_and_store(sent_transaction.get('trytes'))

    #Finds transaction hash and stores it in world
    bundlehash = api.find_transactions(bundles=[bundle.hash])
    world.responses['previousTransaction'] = bundlehash['hashes'][0]


@step(r'check_consistency is called on this transaction')
def check_stitch_consistency(step):
    logger.info('Checking consistency of stitching transaction')
    node = world.config['nodeId']
    api = tests.prepare_api_call(node)

    transaction = world.responses['previousTransaction']

    timeout = False

    def consistency(responses):
        consistency_response = api.check_consistency(tails=[transaction])
        responses['checkConsistency'] = consistency_response
        return

    try:
        r = threading.Thread(target=consistency, args=(world.responses,))
        r.setDaemon(True)
        r.start()
        r.join(timeout=60)

        if r.is_alive():
            raise ValueError(TIMEOUT_ERROR)

    except ValueError as error:
        logger.info('Failed')
        logger.info(error)
        timeout = True

    if timeout != True:
        logger.debug('Response: {}'.format(world.responses['checkConsistency']))
        logger.info('Consistency check completed, request did not time out')
    else:
        raise ValueError(TIMEOUT_ERROR)


@step(r'the response should return "([^"]*)"')
def check_response_return_value(step,return_val):
    logger.info('Checking Return ')
    response = str(world.responses['checkConsistency']['state'])
    logger.debug(response)
    assert response == return_val, "Response does not equal {}. Response: {}".format(return_val,response)
    

@step(r'a transaction is issued referencing the stitching transaction')
def reference_stitch_transaction(step):
    node = world.config['nodeId']
    stitch = world.responses['previousTransaction']
    
    api = tests.prepare_api_call(node)

    timeout = False

    def transactions_to_approve(responses):
        response = api.get_transactions_to_approve(depth=3)
        responses['getTransactionsToApprove'] = response
        return

    try:
        t = threading.Thread(target=transactions_to_approve, args=(world.responses,))
        t.setDaemon(True)
        t.start()
        t.join(timeout=60)

        if t.is_alive():
            raise ValueError(TIMEOUT_ERROR)

    except ValueError as error:
        logger.info('Failed')
        logger.info(error)
        timeout = True

    if timeout != True:
        branch = world.responses['getTransactionsToApprove']['branchTransaction']

        bundle = transactions.create_transaction_bundle(referencing_address,'REFERENCING9STITCH', 0)

        trytes = bundle[0].as_tryte_string()

        sent_transaction = api.attach_to_tangle(stitch, branch, [trytes], 14)
        api.broadcast_and_store(sent_transaction.get('trytes'))

    else:
        raise ValueError(TIMEOUT_ERROR)
