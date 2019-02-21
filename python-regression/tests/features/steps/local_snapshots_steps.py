from aloe import step
from util.test_logic import api_test_logic as api_utils

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@step(r'Local Snapshot files were created in the "([^"]+)" directory')
def check_for_LS_files(step, node):
    command_response = api_utils.send_kube_command(node, "ls /iri/data/")

    assert "testnet.snapshot.meta" in command_response, "No 'testnet.snapshot.meta' file present"
    assert "testnet.snapshot.state" in command_response, "No 'testnet.snapshot.state' file present"
    logger.info("Local Snapshot Files created properly.")


@step(r'reading the local snapshot state file on "([^"]+)" returns with:')
def read_LS_state_file(step, node):
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)

    command_response = api_utils.send_kube_command(node, "cat /iri/data/testnet.snapshot.state")
    addresses_with_value = options['address']

    for address in addresses_with_value:
        assert address in command_response, "Provided address was not found in the .state file"
    logger.info("State File contains the provided addresses.")


@step(r'reading the local snapshot meta file on "([^"]+)" returns with:')
def read_LS_meta_file(step, node):
    arg_list = step.hashes

    options = {}
    api_utils.prepare_options(arg_list, options)

    command_response = api_utils.send_kube_command(node, "cat /iri/data/testnet.snapshot.meta")
    hashes = options['hashes']

    for hash_val in hashes:
        assert hash_val in command_response, "Provided hash: {} was not found in the .state file".format(hash_val)
    logger.info("Meta File contains the provided hashes.")
