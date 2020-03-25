from iota.crypto.signing import KeyGenerator

from iota import Iota, ProposedTransaction, Address, Bundle, TransactionHash, \
    Transaction, TryteString, Tag, ProposedBundle

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def create_incomplete_bundle_trytes(address, tag):
    """
    Creates an incomplete bundle by leaving out the first transaction

    :param address: The address we will use with the transaction
    :param tag:  The tag that will be associated with the transaction
    """
    bundle = ProposedBundle()
    bundle.add_transaction(ProposedTransaction(
        address=Address(static.TEST_EMPTY_ADDRESS),
        tag=Tag("INCOMPLETE9TAG"),
        value=0
    ))
    bundle.add_transaction(ProposedTransaction(
        address=Address(static.TEST_EMPTY_ADDRESS),
        tag=Tag("INCOMPLETE9TAG"),
        value=0
    ))
    bundle.finalize()
    return [bundle.as_tryte_strings()[1]]
    
def create_double_spend_bundles(seedFrom, addressFrom, address1, address2, tag, value):
    """
    Create 2 bundles with conflicting value transfers

    :param seedFrom: The seed used for signing the budles
    :param addressFrom: The address which we will use for input
    :param address1: The address we will use with the first bundle
    :param address2: The address we will use with the second bundle
    :param tag:  The tag that will be associated with the transaction
    :param value: The value we will send
    """

    bundle1 = ProposedBundle()
    bundle1.add_transaction(ProposedTransaction(
        address = Address(address1),
        tag = Tag(tag),
        value = value
    ))
    bundle1.add_inputs([Address(
        addressFrom,
        balance = addressFrom.balance,
        key_index = addressFrom.key_index,
        security_level = addressFrom.security_level
      ),
    ])
    bundle1.send_unspent_inputs_to(Address(addressFrom))
    bundle1.finalize()
    bundle1.sign_inputs(KeyGenerator(seedFrom))
    
    bundle2 = ProposedBundle()
    bundle2.add_transaction(ProposedTransaction(
        address = Address(address2),
        tag = Tag(tag),
        value = value
    ))
    bundle2.add_inputs([Address(
        addressFrom,
        balance = addressFrom.balance,
        key_index = addressFrom.key_index,
        security_level = addressFrom.security_level
      ),
    ])
    bundle2.send_unspent_inputs_to(Address(addressFrom))
    bundle2.finalize()
    bundle2.sign_inputs(KeyGenerator(seedFrom))
    return [bundle1, bundle2]

def create_fake_transfer_bundle(api, seed, tag, value):
    """
    Create a bundle that sends the specified value back and forth.

    :param api: The seed used to generate the bundle, addresses and signing
    :param seed: The seed used to generate the bundle, addresses and signing
    :param tag: The tag that will be associated with the transaction
    :param value: The value of the transaction
    """
    gna_result = api.get_new_addresses(count=2, security_level=1)
    addresses = gna_result['addresses']
    bundle = ProposedBundle()
    bundle.add_transaction(ProposedTransaction(
        address=Address(addresses[0]),
        tag=Tag(tag),
        value=value
    ))
    bundle.add_transaction(ProposedTransaction(
        address=Address(addresses[1]),
        tag=Tag(tag),
        value=value
    ))
    bundle.add_inputs([Address(addresses[0], value, 0, 1), Address(addresses[1], value, 1, 1)])
    bundle.finalize()
    bundle.sign_inputs(KeyGenerator(seed))
    return bundle