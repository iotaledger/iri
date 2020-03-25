from iota.crypto.signing import KeyGenerator

from iota import Iota, ProposedTransaction, Address, Bundle, TransactionHash, \
    Transaction, TryteString, Tag, ProposedBundle

import logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

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

def create_split_bundles(api, seedFrom, addressFrom, addressTo, addressRest, tag, value, reference):
    """
    Create a bundle that reuses the last transaction form another bundle in its own

    :param api: Api used to create and attach the first bundle
    :param seedfrom: The seed used for signing, addressFrom should be created form this
    :param addressFrom: The address we will use as input
    :param addressTo: The address we will use to send value to
    :param addressRest: Where the rest of the balance gets send to, if any
    :param tag:  The tag that will be associated with the transaction
    :param value: The value we will send
    :param reference: The reference we use to connect the trunk with
    """
    bundle1 = ProposedBundle()
    bundle1.add_transaction(ProposedTransaction(
        address = Address(addressTo),
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
    bundle1.send_unspent_inputs_to(Address(addressRest))
    bundle1.finalize()
    bundle1.sign_inputs(KeyGenerator(seedFrom))
    
    gtta_response = api.get_transactions_to_approve(3)

    trunk = reference
    branch = gtta_response.get('branchTransaction')

    attached_original_trytes = api.attach_to_tangle(trunk, branch, bundle1.as_tryte_strings()).get('trytes')

    # So we have the original bundle attached, time to construct the new one
    # We need to re-attach, but take special care, so we dont use the api, rather we do it ourself

    re_attached_trytes = custom_attach(attached_original_trytes, 14)

    original_bundle = Bundle.from_tryte_strings(attached_original_trytes)

    re_attached_bundle = Bundle.from_tryte_strings(re_attached_trytes)
    return [original_bundle, re_attached_bundle]
    
def custom_attach(trytes, mwm):
    """
    Custom attach to to tangle.

    Takes already attached bundle trytes, and except for the the head transaction,
    updates `attachment_timestamp` and re-does the pow, resulting in a new
    nonce and transaction hash.

    The head transaction remains the same as in the original bundle.

    :param trytes: List[TransactionTrytes]
    :param mwm: int
    """
    # Install the pow package together with pyota:
    # $ pip install pyota[pow]
    from pow.ccurl_interface import get_powed_tx_trytes, get_hash_trytes, \
        get_current_ms

    previoustx = None

    # Construct bundle object
    bundle = Bundle.from_tryte_strings(trytes)

    # and we need the head tx first
    for txn in reversed(bundle.transactions):
        if (not previoustx): # this is the head transaction
            # head tx stays the same, it is the original
            previoustx = txn.hash
            continue

        # It is not a head transaction
        # only updae the trunk reference
        txn.trunk_transaction_hash = previoustx # the previous transaction
        txn.attachment_timestamp = get_current_ms()
        
        # Let's do the pow locally
        txn_string = txn.as_tryte_string().__str__()
        # returns a python unicode string
        powed_txn_string = get_powed_tx_trytes(txn_string, mwm)
        # construct trytestring from python string
        powed_txn_trytes = TryteString(powed_txn_string)
        # compute transaction hash
        hash_string = get_hash_trytes(powed_txn_string)
        hash_trytes = TryteString(hash_string)
        hash_= TransactionHash(hash_trytes)

        # Create powed txn object
        powed_txn = Transaction.from_tryte_string(
            trytes=powed_txn_trytes,
            hash_=hash_
        )

        previoustx = powed_txn.hash
        # put that back in the bundle
        bundle.transactions[txn.current_index] = powed_txn
    return bundle.as_tryte_strings()