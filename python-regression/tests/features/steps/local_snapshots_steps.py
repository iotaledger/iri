from aloe import step
from util.test_logic import api_test_logic as api_utils
import os

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


@step(r'the spent addresses are exported from "([^"]+)"')
def export_spent_addresses(step, node):
    """
    Uses an ixi module to export the spent addresses from the current node to a text file.

    :param node: The node that the spent addresses will be pulled from
    """
    command = {"command": "export-spent.generateSpentAddressesFile"}
    request_return = api_utils.send_ixi_request(node, command)
    assert 'ixi' in request_return, "Error: {}".format(request_return['error'])

    num_exports = request_return['ixi']['amount']
    checksum = request_return['ixi']['checksum']

    # Change these assertion value with the appropriate ones if you have used a custom db and not the provided one
    expected_num_exports = 74
    expected_checksum = "919432abf85f939057f020d1a589f4d5513ff8890b0fb6b8e2242f90fa9158bc"

    assert num_exports == expected_num_exports, \
        "Expected to export {} addresses, instead exported {}".format(expected_num_exports, num_exports)
    assert checksum == expected_checksum, \
        "The checksum {} does not match the expected: {}".format(checksum, expected_checksum)

    logger.info("Spent addresses file exported as: {}".format(request_return['ixi']['fileName']))


@step(r'the spent addresses are imported on "([^"]+)"')
def merge_spent_addresses_file(step, node):
    """
    Reads the spentAddresses.txt file that should be present in the python regression IXI directory, and merges it into
    the provided node. Then the return values for the IXI call are checked to make sure no failures occurred.

    :param node: The node that the spent addresses will be merged into
    """
    spent_addresses_file = '/iri/data/ixi/spentAddresses.txt'
    command = {"command": "merge-spent.mergeSpentAddresses", "fileNames": [spent_addresses_file]}
    request_return = api_utils.send_ixi_request(node, command)
    assert 'ixi' in request_return, "Error: {}".format(request_return['error'])

    failures = request_return['ixi']['errors']
    assert len(failures) == 0, "There were failures in the merge request: {}".format(failures)


@step(r'reading the exported spent addresses file on "([^"]+)" returns with:')
def read_spent_addresses_file(step, node):
    """
    Reads the spentAddresses.txt file that should be present on the node, and ensures the expected values are there.

    :param step.hashes: The pointer to the expected values that should be present in the file
    :param node: The node whose exported spent addresses file will be read
    """
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)

    file_name = '/iri/data/spentAddresses.txt'
    lines = [line.rstrip() for line in open(file_name)]

    for x in options['addresses']:
        assert x in lines, "Value was not found in file"


