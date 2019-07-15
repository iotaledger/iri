from iota import BundleHash, Fragment
from iota.crypto import HASH_LENGTH
from iota.crypto.kerl import Kerl


def finalize(bundle):
    sponge = Kerl()
    last_index = len(bundle) - 1

    for (i, txn) in enumerate(bundle):
        txn.current_index = i
        txn.last_index = last_index
        sponge.absorb(txn.get_signature_validation_trytes().as_trits())

    bundle_hash_trits = [0] * HASH_LENGTH
    sponge.squeeze(bundle_hash_trits)

    bundle_hash = BundleHash.from_trits(bundle_hash_trits)

    for txn in bundle:
        txn.bundle_hash = bundle_hash
        txn.signature_message_fragment = Fragment(txn.message or b'')
