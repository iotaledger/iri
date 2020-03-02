from aloe import world, step
from util.test_logic import api_test_logic as api_utils
import zmq

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

context = zmq.Context()
socket = context.socket(zmq.SUB)
poller = zmq.Poller()


@step(r'"([^"]+)" is subscribed to the following zmq topics:')
def subscribe_zmq(step, node):
    """
    Subscribe to the given topics on the indicated node.

    :param step.hashes:     List of topics to subscribe to
    :param node:            The node to subscribe to
    """
    arg_list = step.hashes

    host = world.machine['nodes'][node]['podip']
    port = world.machine['nodes'][node]['clusterip_ports']['zmq-feed']
    socket.connect("tcp://{}:{}".format(host, port))

    for arg in arg_list:
        socket.setsockopt_string(zmq.SUBSCRIBE, arg['keys'])

    poller.register(socket, zmq.POLLIN)


@step(r'the zmq stream for "([^"]+)" contains a response for following topics:')
def check_zmq_stream(step, node):
    """"
    Read the zmq stream on the indicated node, and ensure that all the provided topics are present in the
    stream response.

    :param step.hashes:     List of topics to check response for
    :param node:            The node the stream is being read from (Not used in test, provided for clarity)
    """
    arg_list = step.hashes

    keys = []
    for arg in arg_list:
        keys.append(arg['keys'])

    contains_response = False
    checked_args = []
    while len(poller.poll(timeout=1000)) != 0:
            received = socket.recv().split()
            contains_response = False
            for arg in keys:
                if arg in checked_args:
                    contains_response = True
                    break

                if received[0].decode() == arg:
                    contains_response = True
                    checked_args.append(arg)
                    break

            assert contains_response is True, \
                "The expected response in the zmq subscription '{}' is missing".format(arg)

    assert contains_response is True, "Expected ZMQ data not found"
    logger.info("Expected ZMQ data was found")


@step(r'the zmq stream for "([^"]+)" contains a response for following responses:')
def check_zmq_responses(step, node):
    """"
    Read the zmq stream on the indicated node, and ensure that all the provided responses are present in the
    stream response.

    :param step.hashes:     List of topics to check response for
    :param node:            The node the stream is being read from (Not used in test, provided for clarity)
    """
    expected_values = {}
    args = step.hashes
    api_utils.prepare_options(args, expected_values)

    keys = []
    for arg in args:
        keys.append(arg['keys'])

    contains_response = False
    checked_args = []

    while len(poller.poll(timeout=1000)) != 0:
        received = socket.recv().split()
        contains_response = False
        for arg in keys:
            if arg in checked_args:
                contains_response = True
                break

            if received[0].decode() == arg:
                value = fetch_value_from_response(received, arg)
                if value == expected_values[arg]:
                    contains_response = True
                    checked_args.append(arg)
                    break

        assert contains_response is True, \
            "The expected response in the zmq subscription '{}' is missing".format(arg)

    assert contains_response is True, "Expected ZMQ data not found"
    logger.info("Expected ZMQ data was found")


def fetch_value_from_response(response, arg):
    first_arg = ['tx', 'tx_trytes', 'sn_trytes', 'lmhs', 'mctn']
    second_arg = ['lmi', 'lmsi', 'sn']

    if arg in first_arg:
        value = response[1].decode()
    elif arg in second_arg:
        value = response[2].decode()
    else:
        value = response[1].decode()

    return value
