from iota import TryteString, trits_from_int


def int_to_trytestring(int_input, length):
    trits = trits_from_int(int(int_input))
    trytes = TryteString.from_trits(trits)
    if len(trytes) < length:
        trytes += '9' * (length - len(trytes))
    return trytes
