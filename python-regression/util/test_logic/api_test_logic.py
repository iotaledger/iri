from aloe import world
from iota import Iota,Address,Tag,TryteString
from copy import deepcopy
from . import value_fetch_logic as value_fetch


import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def prepare_api_call(node_name, **seed):
    """
    Prepares an api target as an entry point for API calls on a specified node.

    :param node_name: The node reference you would like the api target point to be created for
    :return: The api target point for the specified node
    """

    logger.info('Preparing api call')
    host = world.machine['nodes'][node_name]['host']
    port = world.machine['nodes'][node_name]['ports']['api']
    address = "http://" + host + ":" + str(port)
    api = Iota(address, **seed)
    logger.info('API call prepared for %s', address)
    return api


def check_responses_for_call(api_call):
    steps = import_steps()
    if len(steps.responses[api_call]) > 0:
        return True
    else:
        return False


def place_response(api_call, node, response):
    world.responses[api_call][node] = response


def check_neighbors(step, node):
    api = prepare_api_call(node)
    response = api.getNeighbors()
    logger.info('Response: %s', response)
    contains_neighbor = [False, False]

    for i in response:
        expected_neighbors = step.hashes
        if type(response[i]) != int:
            for x in range(len(response[i])):
                if expected_neighbors[0]['neighbors'] == response[i][x]['address']:
                    contains_neighbor[0] = True
                if expected_neighbors[1]['neighbors'] == response[i][x]['address']:
                    contains_neighbor[1] = True

    return contains_neighbor


def import_steps():
    import tests.features.steps.api_test_steps as steps
    return steps


def prepare_options(args, option_list):
    """
    Prepares key dictionary for comparison with response values. The argument and type are contained in
    a gherkin table, stored beneath the step definition in the associated feature file. This function
    converts the argument values to the appropriate format.

    :param args: The gherkin table arguments from the feature file
    :param option_list: The list dictionary that the arguments will be placed into
    """

    for x in range(len(args)):
        if len(args) != 0:
            key = args[x]['keys']
            value = args[x]['values']
            arg_type = args[x]['type']

            fetch_list = {
                'int': value_fetch.fetch_int,
                'string': value_fetch.fetch_string,
                'list': value_fetch.fetch_list,
                'nodeAddress': value_fetch.fetch_node_address,
                'staticValue': value_fetch.fetch_static_value,
                'staticList': value_fetch.fetch_static_list,
                'bool': value_fetch.fetch_bool,
                'responseValue': value_fetch.fetch_response_value,
                'responseList': value_fetch.fetch_response_list,
                'responseHashes': value_fetch.fetch_response_value_hashes,
                'configValue': value_fetch.fetch_config_value,
                'configList': value_fetch.fetch_config_list,
                # TODO: remove the need for this logic
                'ignore': value_fetch.fetch_string
            }

            option = fetch_list[arg_type](value)

            """
            Fills option_list with the fetched value. Excludes seed as an option, as it's only there for value 
            transactions and is not required as an argument for any api calls. 
            """
            if key != 'seed':
                option_list[key] = option


def fetch_call(api_call, api, options):
    """
    Fetch the provided API call target using the provided arguments and return the response.

    :param api_call: The API call you would like to fetch
    :param api: A provided node api target for making the call
    :param options: The arguments needed for the API call
    :return: Response for API Call
    """

    call_list = {
        'getNodeInfo': api.get_node_info,
        'getNeighbors': api.get_neighbors,
        'getTips': api.get_tips,
        'getTrytes': api.get_trytes,
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

    response = call_list[api_call](**options)

    return response


def assign_nodes(node, node_list):
    """
    This method determines if the node specified is equal to "all nodes". If it is,
    it stores all available nodes in the node list. If not, it stores only the
    specified node. It also updates the current world.config['nodeId'] to either
    the specified node, or the first node in the world.machine variable.

    :param node: The specified node (or "all nodes")
    :param node_list: The list to store the usable nodes
    """
    if node == 'all nodes':
        for current_node in world.machine['nodes']:
            api = prepare_api_call(current_node)
            node_list[current_node] = {'api': api}
        node = next(iter(world.machine['nodes']))
        world.config['nodeId'] = node
    else:
        api = prepare_api_call(node)
        node_list[node] = {'api': api}
        world.config['nodeId'] = node


def make_api_call(api, options, q):
    responses = q.get()
    config = q.get()

    api_call = config['api']
    node = config['nodeId']

    response = fetch_call(api_call, api, options)
    responses[api_call] = {}
    responses[api_call][node] = response
    return response


def check_if_empty(value):
    if len(value) == 0:
        is_empty = True
    else:
        is_empty = False
    return is_empty


def prepare_transaction_arguments(arg_list):
    for key in arg_list:
        if key == 'address':
            arg_list[key] = Address(arg_list[key])
        elif key == 'tag':
            arg_list[key] = Tag(arg_list[key])
        elif key == 'message':
            arg_list[key] = TryteString.from_unicode(arg_list[key])



def duplicate_arguments(arg_list):
    """
    Duplicates the step arguments, providing a copy for storage and comparison.

    :param arg_list: The original step arguments you would like to copy.
    :return: Copy of the original argument list.
    """

    stored_values = deepcopy(arg_list)
    stored_value_list = {}
    for index, value in enumerate(stored_values):
        stored_value_list[index] = value

    return stored_value_list

