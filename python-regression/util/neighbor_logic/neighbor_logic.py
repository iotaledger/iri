import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def check_if_neighbors(api, neighbors, expected_neighbor):
    """
    This method is used to determine if a node contains the neighbors specified in the steps feature list

    :param api: The api target you would like to check the neighbors of
    :param neighbors: The list of neighbors returned from a getNeighbors API call
    :param expected_neighbor: The neighbor address you expect to find in the list
    :return: A list of two bools

    If the return contains a False response, then the neighbor associated with that bool will be added in the remaining
    methods in the step.
    """
    is_neighbor = False
    for neighbor in enumerate(neighbors):
        if expected_neighbor == neighbors[neighbor]['address']:
            logger.info("Already a neighbor")
            is_neighbor = True
        else:
            logger.info('Adding neighbor')

    if is_neighbor is False:
        udp_address = "udp://" + expected_neighbor
        logger.info('Adding {} as neighbor'.format(udp_address))
        api.add_neighbors([udp_address.decode()])
        logger.info('{} added as neighbor'.format(udp_address))
