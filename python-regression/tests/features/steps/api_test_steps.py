from aloe import *
from util import static_vals
from util.test_logic import api_test_logic as api_utils
from util.threading_logic import pool_logic as pool
from util.neighbor_logic import neighbor_logic as neighbors
from util.response_logic import response_handling as responses
from time import sleep, time

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

testAddress = static_vals.TEST_ADDRESS

world.config = {}
world.responses = {}


@step(r'"([^"]+)" is called on "([^"]+)" with:')
def api_method_is_called(step, api_call, node_name):
    """
    This is the general api calling function. There are 3 inputs

    :param api_call:     The api call that will be requested
    :param node_name:    The name identifying the node you would like to make this request on
    :param step.hashes:  A gherkin table outlining any arguments needed for the call
                        (See tests/features/machine1/1_api_tests.feature for examples)

        The table parameter is unique in that there are several input types available depending on the call
        being made.
            :type string: Basic string argument, will be taken as is
            :type int: Basic integer argument, will be converted to int before call is made
            :type nodeAddress: Node name identifier, will create address from node configuration
            :type staticValue: Static name identifier, will fetch value from util/static_vals.py
            :type staticList: Same as staticValue, except it places the results into a list
            :type responseValue: Identifier for api call response value
            :type responseList: Same as responseValue, ecept it places the results into a list
            :type configValue: Identifier for a value stored in world.config
            :type bool: Bool argument, returns True or False
    """
    logger.info('%s is called on %s', api_call, node_name)
    world.config['apiCall'] = api_call
    world.config['nodeId'] = node_name
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)

    api = api_utils.prepare_api_call(node_name)
    response = api_utils.fetch_call(api_call, api, options)

    assert type(response) is dict, 'There may be something wrong with the response format: {}'.format(response)
    world.responses[api_call] = {}
    world.responses[api_call][node_name] = response


# This method is identical to the method above, but creates a new thread
@step(r'"([^"]+)" is called in parallel on "([^"]+)" with:')
def threaded_call(step, api_call, node):
    """
    Makes an asynchronous API call on the specified node and stores the future result reference in the
    world.config variable.

    :param api_call: The API call you would like to make.
    :param node: The identifier for the node you would like to run the call on.
    :param step.hashes: A gherkin table present in the feature file specifying the
                        arguments and the associated type.
    """
    logger.info("Creating thread for {}".format(api_call))
    world.config['apiCall'] = api_call
    world.config['nodeId'] = node
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)
    api = api_utils.prepare_api_call(node)

    def make_call(node, arg_list):
        response = api_utils.fetch_call(api_call, arg_list['api'], arg_list['options'])
        arg_list['responses'][api_call] = {}
        arg_list['responses'][api_call][node] = response
        return response

    args = {node: {'api': api, 'options': options, 'responses': world.responses}}
    future_results = pool.start_pool(make_call, 1, args)

    if 'future_results' not in world.config:
        world.config['future_results'] = {}
    world.config['future_results'][api_call] = future_results

    
@step(r'we wait "(\d+)" second/seconds')
def wait_for_step(step, time):
    """
    Wait a specified number of seconds before continuing.

    :param time: The number of seconds you would like the step to wait for.
    """
    logger.info('Waiting for {} seconds'.format(time))
    sleep(int(time))


@step(r'the "([^"]+)" parallel call should return with:')
def compare_thread_return(step, api_call):
    """
    Prepare response list for comparison.

    :param api_call: The API call you would like to find a response for
    :param step.hashes: A gherkin table present in the feature file specifying the
                        values and the associated type to be found in the response.
    """
    logger.debug(world.responses)
    future_results = world.config['future_results'][api_call]

    for result in future_results:
        response_list = pool.fetch_results(result, 5)
        # Exclude duration from response list
        if 'duration' in response_list:
            del response_list['duration']
        if 'info' in response_list:
            del response_list['info']
        response_keys = response_list.keys()

        expected_values = {}
        api_utils.prepare_options(step.hashes,expected_values)
        keys = expected_values.keys()

        # Confirm that the lists are of equal length before comparing
        assert len(keys) == len(response_keys), \
            'Response: {} does not contain the same number of arguments: {}'.format(keys, response_keys)

        for count in range(len(keys)):
            response_key = response_keys[count]
            response_value = response_list[response_key]
            expected_value = expected_values[response_key]
            assert response_value == expected_value, \
                'Returned: {} does not match the expected value: {}'.format(response_value, expected_value)


@step(r'"([^"]*)" is called (\d+) times on "([^"]*)" with:')
def spam_call(step, api_call, num_tests, node):
    """
    Spams an API call a number of times among the specified nodes in a cluster

    :param api_call: The API call you would like to make
    :param num_tests: The number of iterations you would like to run
    :param node: The node that the call will be sent to. This can be set to 'all nodes' and it will run the test
                 on all the available nodes.
    :param step.hashes: A gherkin table present in the feature file specifying the
                        arguments and the associated type.
    """
    start = time()
    world.config['apiCall'] = api_call
    arg_list = step.hashes
    nodes = {}
    response_val = []

    options = {}
    api_utils.prepare_options(arg_list, options)

    # See if call will be made on one node or all
    api_utils.assign_nodes(node, nodes)
    node = world.config['nodeId']

    def run_call(node, api):
        logger.debug('Running Thread on {}'.format(node))
        response = api.get_transactions_to_approve(depth=3)
        return response

    args = nodes
    future_results = pool.start_pool(run_call, num_tests, args)

    responses.fetch_future_results(future_results, num_tests, response_val)

    world.responses[api_call] = {}
    world.responses[api_call][node] = response_val

    end = time()
    time_spent = end - start
    logger.info('Time spent on loop: {}'.format(time_spent))


@step(r'"([^"]+)" and "([^"]+)" are neighbors')
def make_neighbors(step, node1, node2):
    """
    Ensures that the specified nodes are neighbored with one another.

    :param node1: The identifier for the first node (ie nodeA)
    :param node2: The identifier for the second node (ie nodeB)
    """
    neighbor_candidates = [node1, node2]
    neighbor_info = {}

    for node in neighbor_candidates:
        host = world.machine['nodes'][node]['podip']
        port = world.machine['nodes'][node]['clusterip_ports']['gossip-udp']
        api = api_utils.prepare_api_call(node)
        response = api.get_neighbors()
        neighbor_info[node] = {
            'api': api,
            'node_neighbors': list(response['neighbors']),
            'address': str(host) + ":" + str(port)
        }

    logger.info('Checking neighbors for {}'.format(node1))
    neighbors.check_if_neighbors(neighbor_info[node1]['api'],
                                 neighbor_info[node1]['node_neighbors'], neighbor_info[node2]['address'])

    logger.info('Checking neighbors for {}'.format(node2))
    neighbors.check_if_neighbors(neighbor_info[node2]['api'],
                                 neighbor_info[node2]['node_neighbors'], neighbor_info[node1]['address'])



