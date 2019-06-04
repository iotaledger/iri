from aloe import step
from util.test_logic import api_test_logic as api_utils

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@step(r'A local snapshot was taken on "([^"]+)" at index (\d+)')
def check_ls_indexes(step, node, index):
    '''
    Uses an ixi module to check the index of the latest snapshot file. It checks to make sure that the initialIndex is
    not equal to 0. If it is, that means that a local snapshot has not been taken. If it passes this check it then
    ensures that the index registered in the node's snapshot provider is equal to the index provided.

    :param node: The node that the IXI request will be made on
    :param index: The expected index of the Local Snapshot
    '''
    command ={"command": "LocalSnapshots.getIndexes"}
    request_return = api_utils.send_ixi_request(node, command)
    assert 'ixi' in request_return, "Error: {}".format(request_return['error'])
    snapshot_index = request_return['ixi']['initialIndex']

    assert int(snapshot_index) != 0, "Local Snapshot not generated."
    assert int(snapshot_index) == int(index), "Snapshot index {} does not match the expected {}."\
        .format(snapshot_index, index)
    logger.info("Local Snapshot Index matches expected value")


@step(r'reading the local snapshot state on "([^"]+)" returns with:')
def read_ls_state(step, node):
    """
    Uses an ixi module to check the current snapshot state of the node. It cycles through a provided list of addresses
    to make sure the snapshot state contains them.

    :param step.hashes: A pointer to the list of addresses that should be present in the snapshot state
    :param node: The node that the IXI request will be made on
    """
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)

    command = {"command": "LocalSnapshots.getState"}
    request_return = api_utils.send_ixi_request(node, command)
    assert 'ixi' in request_return, "Error: {}".format(request_return['error'])
    node_state = request_return['ixi']['state']
    addresses_with_value = options['address']

    for address in addresses_with_value:
        assert address in node_state, "Provided address: {} was not found in the snapshot state".format(address)
    logger.info("Snapshot State contains the provided addresses.")


@step(r'reading the local snapshot metadata on "([^"]+)" returns with:')
def read_ls_metadata(step, node):
    """
    Uses an ixi module to check the current snapshot state of the node. It cycles through a provided list of addresses
    to make sure the snapshot state contains them.

    :param step.hashes: A pointer to the list of milestone hashes that should be present in the snapshot metadata
    :param node: The node that the IXI request will be made on
    """
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)

    command = {"command": "LocalSnapshots.getMetaData"}
    request_return = api_utils.send_ixi_request(node, command)
    assert 'ixi' in request_return, "Error: {}".format(request_return['error'])
    node_metadata = request_return['ixi']['metaData']
    hashes = options['hashes']

    for hash_val in hashes:
        assert hash_val in node_metadata, "Provided hash: {} was not found in the snapshot metadata".format(hash_val)
    logger.info("Snapshot Metadata contains the provided hashes.")
