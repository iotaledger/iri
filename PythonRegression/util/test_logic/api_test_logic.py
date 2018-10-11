from aloe import world
from iota import Iota
from util import static_vals

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)



def prepare_api_call(nodeName):
    logger.info('Preparing api call')
    host = world.machine['nodes'][nodeName]['host']
    port = world.machine['nodes'][nodeName]['ports']['api']
    address ="http://"+ host + ":" + str(port)
    api = Iota(address)
    logger.info('API call prepared for %s',address)
    return api


def check_responses_for_call(apiCall):
    steps = import_steps()
    if len(steps.responses[apiCall]) > 0:
        return True
    else:
        return False
    
def fetch_response(apiCall):
    return world.responses[apiCall]

def place_response(apiCall,node,response):
    world.responses[apiCall][node] = response


def fetch_config(key):
    return world.config[key]


def check_neighbors(step,node):
    steps = import_steps()
    api = prepare_api_call(node)
    response = api.getNeighbors()
    logger.info('Response: %s',response)
    containsNeighbor = [False,False]
    
    for i in response:
        expectedNeighbors = step.hashes
        if type(response[i]) != int:
            for x in range(len(response[i])):    
                if expectedNeighbors[0]['neighbors'] == response[i][x]['address']:
                    containsNeighbor[0] = True  
                if expectedNeighbors[1]['neighbors'] == response[i][x]['address']:
                    containsNeighbor[1] = True  
    
    return containsNeighbor

def import_steps():
    import tests.features.steps.api_test_steps as steps
    return steps


def prepare_options(args,optionList):
    for x in range(len(args)):
        if len(args) != 0:
            key = args[x]['keys']
            value = args[x]['values']
            arg_type = args[x]['type']

            if arg_type == "int":
                value = int(value)
            elif arg_type == "nodeAddress":
                host = world.machine['nodes'][value]['host']
                port = world.machine['nodes'][value]['ports']['gossip-udp']
                address = "udp://" + host + ":" + str(port)
                value = [address.decode()]
            elif arg_type == "staticValue":
                value = getattr(static_vals,value)
            elif arg_type == "staticList":
                address = getattr(static_vals,value)
                value = [address]
            elif arg_type == "bool":
                if value == "False":
                    value = False
                else:
                    value = True
            elif arg_type == "responseValue":
                config = fetch_config('nodeId')
                response = fetch_response(value)
                value = response[config]
            elif arg_type == "responseList":
                config = fetch_config('nodeId')
                response = fetch_response(value)
                value = [response[config]]

            optionList[key] = value

def fetch_call(apiCall,api,options):
    callList = {
        'getNodeInfo': api.get_node_info,
        'getNeighbors': api.get_neighbors,
        'getTips': api.get_tips,
        'getTrytes':api.get_trytes,
        'getTransactionsToApprove': api.get_transactions_to_approve,
        'getBalances': api.get_balances,
        'addNeighbors': api.add_neighbors,
        'removeNeighbors': api.remove_neighbors,
        'wereAddressesSpentFrom': api.were_addresses_spent_from,
        'getInclusionStates': api.get_inclusion_states,
        'storeTransactions': api.store_transactions,
        'broadcastTransactions': api.broadcast_transactions,
        'findTransactions': api.find_transactions,
        'attachToTangle': api.attach_to_tangle,
        'checkConsistency': api.check_consistency,
        'interruptAttachingToTangle': api.interrupt_attaching_to_tangle,
    }

    response = callList[apiCall](**options)

    return response

