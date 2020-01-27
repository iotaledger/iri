from aloe import world, step
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
def check_zmq_response(step, node):
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

    scan_sockets = True
    while scan_sockets:
        if len(poller.poll(timeout=1000)) != 0:
            received = socket.recv().split()
            contains_response = False
            for arg in keys:
                if received[0].decode() == arg:
                    contains_response = True

            assert contains_response is True, \
                "The expected response in the zmq subscription '{}' is missing".format(arg)
        else:
            scan_sockets = False
