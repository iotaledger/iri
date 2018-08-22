from aloe import step
from tests.features.steps import api_test_steps,blowball_tests_steps

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

tests = api_test_steps
blowball_tests = blowball_tests_steps
responses = {}

@step(r'find transaction is called with the address:')
def find_transactions_from_address(step):
    logger.info('Finding milestones')
    node = tests.fetch_config('nodeId')       
    
    api = tests.prepare_api_call(node)
    transactions = api.find_transactions(addresses = [step.multiline])
    tests.fill_response('findTransactions',transactions)



