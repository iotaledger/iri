#!/usr/bin/env python

# the code below is 'borrowed' almost verbatim from electrum,
# https://gitorious.org/electrum/electrum
# and is under the GPLv3.

import ecdsa
import base64
import hashlib
from ecdsa.util import string_to_number
import sys

VERBOSE = False
#VERBOSE = True

# secp256k1, http://www.oid-info.com/get/1.3.132.0.10
_p = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2FL
_r = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141L
_b = 0x0000000000000000000000000000000000000000000000000000000000000007L
_a = 0x0000000000000000000000000000000000000000000000000000000000000000L
_Gx = 0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798L
_Gy = 0x483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8L
curve_secp256k1 = ecdsa.ellipticcurve.CurveFp( _p, _a, _b )
generator_secp256k1 = ecdsa.ellipticcurve.Point( curve_secp256k1, _Gx, _Gy, _r )
oid_secp256k1 = (1,3,132,0,10)
SECP256k1 = ecdsa.curves.Curve("SECP256k1", curve_secp256k1, generator_secp256k1, oid_secp256k1)

addrtype = 0

# from http://eli.thegreenplace.net/2009/03/07/computing-modular-square-roots-in-python/

def modular_sqrt(a, p):
    """ Find a quadratic residue (mod p) of 'a'. p
    must be an odd prime.

    Solve the congruence of the form:
    x^2 = a (mod p)
    And returns x. Note that p - x is also a root.

    0 is returned is no square root exists for
    these a and p.

    The Tonelli-Shanks algorithm is used (except
    for some simple cases in which the solution
    is known from an identity). This algorithm
    runs in polynomial time (unless the
    generalized Riemann hypothesis is false).
    """
    # Simple cases
    #
    if legendre_symbol(a, p) != 1:
        return 0
    elif a == 0:
        return 0
    elif p == 2:
        return p
    elif p % 4 == 3:
        return pow(a, (p + 1) / 4, p)

    # Partition p-1 to s * 2^e for an odd s (i.e.
    # reduce all the powers of 2 from p-1)
    #
    s = p - 1
    e = 0
    while s % 2 == 0:
        s /= 2
        e += 1

    # Find some 'n' with a legendre symbol n|p = -1.
    # Shouldn't take long.
    #
    n = 2
    while legendre_symbol(n, p) != -1:
        n += 1

    # Here be dragons!
    # Read the paper "Square roots from 1; 24, 51,
    # 10 to Dan Shanks" by Ezra Brown for more
    # information
    #

    # x is a guess of the square root that gets better
    # with each iteration.
    # b is the "fudge factor" - by how much we're off
    # with the guess. The invariant x^2 = ab (mod p)
    # is maintained throughout the loop.
    # g is used for successive powers of n to update
    # both a and b
    # r is the exponent - decreases with each update
    #
    x = pow(a, (s + 1) / 2, p)
    b = pow(a, s, p)
    g = pow(n, s, p)
    r = e

    while True:
        t = b
        m = 0
        for m in xrange(r):
            if t == 1:
                break
            t = pow(t, 2, p)
        if m == 0:
            return x
        gs = pow(g, 2 ** (r - m - 1), p)
        g = (gs * gs) % p
        x = (x * gs) % p
        b = (b * g) % p
        r = m

def legendre_symbol(a, p):
    """ Compute the Legendre symbol a|p using
    Euler's criterion. p is a prime, a is
    relatively prime to p (if p divides
    a, then a|p = 0)

    Returns 1 if a has a square root modulo
    p, -1 otherwise.
    """
    ls = pow(a, (p - 1) / 2, p)
    return -1 if ls == p - 1 else ls

__b58chars = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'
__b58base = len(__b58chars)

def b58encode(v):
    """ encode v, which is a string of bytes, to base58.
    """

    long_value = 0L
    for (i, c) in enumerate(v[::-1]):
        long_value += (256**i) * ord(c)

    result = ''
    while long_value >= __b58base:
        div, mod = divmod(long_value, __b58base)
        result = __b58chars[mod] + result
        long_value = div
    result = __b58chars[long_value] + result

    # Bitcoin does a little leading-zero-compression:
    # leading 0-bytes in the input become leading-1s
    nPad = 0
    for c in v:
        if c == '\0': nPad += 1
        else: break

    return (__b58chars[0]*nPad) + result

def b58decode(v, length):
    """ decode v into a string of len bytes."""
    long_value = 0L
    for (i, c) in enumerate(v[::-1]):
        long_value += __b58chars.find(c) * (__b58base**i)

    result = ''
    while long_value >= 256:
        div, mod = divmod(long_value, 256)
        result = chr(mod) + result
        long_value = div
    result = chr(long_value) + result

    nPad = 0
    for c in v:
        if c == __b58chars[0]: nPad += 1
        else: break

    result = chr(0)*nPad + result
    if length is not None and len(result) != length:
        return None

    return result

def msg_magic(message):
    return "\x18Bitcoin Signed Message:\n" + chr( len(message) ) + message

def Hash(data):
    return hashlib.sha256(hashlib.sha256(data).digest()).digest()

def hash_160(public_key):
    md = hashlib.new('ripemd160')
    md.update(hashlib.sha256(public_key).digest())
    return md.digest()

def hash_160_to_bc_address(h160):
    vh160 = chr(addrtype) + h160
    h = Hash(vh160)
    addr = vh160 + h[0:4]
    return b58encode(addr)

def public_key_to_bc_address(public_key):
    h160 = hash_160(public_key)
    return hash_160_to_bc_address(h160)

def encode_point(pubkey, compressed=False):
    order = generator_secp256k1.order()
    p = pubkey.pubkey.point
    x_str = ecdsa.util.number_to_string(p.x(), order)
    y_str = ecdsa.util.number_to_string(p.y(), order)
    if compressed:
        return chr(2 + (p.y() & 1)) + x_str
    else:
        return chr(4) + x_str + y_str

def sign_message(private_key, message, compressed=False):
    public_key = private_key.get_verifying_key()
    signature = private_key.sign_digest( Hash( msg_magic( message ) ), sigencode = ecdsa.util.sigencode_string )
    address = public_key_to_bc_address(encode_point(public_key, compressed))
    v = public_key.verify_digest( signature, Hash( msg_magic( message ) ), sigdecode = ecdsa.util.sigdecode_string)
    if not v:
        raise BaseException("error: verify digest error.")
    for i in range(4):
        nV = 27 + i
        if compressed:
            nV += 4
        sig = base64.b64encode( chr(nV) + signature )
        try:
            if verify_message( address, sig, message):
                break
        except Exception:
            print('warn: sign failed %d times with private_key:%s, message:%s, compressed:%s' % (nV,private_key,message,compressed))
    else:
        raise BaseException("error: cannot sign message")

    return sig

def verify_message(address, signature, message):
    """ See http://www.secg.org/download/aid-780/sec1-v2.pdf for the math """
    from ecdsa import numbertheory, ellipticcurve, util
    curve = curve_secp256k1
    G = generator_secp256k1
    order = G.order()
    # extract r,s from signature
    sig = base64.b64decode(signature)
    if len(sig) != 65: raise BaseException("Wrong encoding")
    r,s = util.sigdecode_string(sig[1:], order)
    nV = ord(sig[0])
    if nV < 27 or nV >= 35:
        return False
    if nV >= 31:
        compressed = True
        nV -= 4
    else:
        compressed = False
    recid = nV - 27
    # 1.1
    x = r + (recid/2) * order
    # 1.3
    alpha = ( x * x * x  + curve.a() * x + curve.b() ) % curve.p()
    beta = modular_sqrt(alpha, curve.p())
    y = beta if (beta - recid) % 2 == 0 else curve.p() - beta
    # 1.4 the constructor checks that nR is at infinity
    R = ellipticcurve.Point(curve, x, y, order)
    # 1.5 compute e from message:
    h = Hash( msg_magic( message ) )
    e = string_to_number(h)
    minus_e = -e % order
    # 1.6 compute Q = r^-1 (sR - eG)
    inv_r = numbertheory.inverse_mod(r,order)
    Q = inv_r * ( s * R + minus_e * G )
    public_key = ecdsa.VerifyingKey.from_public_point( Q, curve = SECP256k1 )
    # check that Q is the public key
    public_key.verify_digest( sig[1:], h, sigdecode = ecdsa.util.sigdecode_string)
    # check that we get the original signing address
    addr = public_key_to_bc_address(encode_point(public_key, compressed))
    if address == addr:
        return True
    else:
        #print addr
        return False


def sign_message_with_secret(secret, message, compressed=False):
    private_key = ecdsa.SigningKey.from_secret_exponent( secret, curve = SECP256k1 )

    public_key = private_key.get_verifying_key()
    signature = private_key.sign_digest( Hash( msg_magic( message ) ), sigencode = ecdsa.util.sigencode_string )
    address = public_key_to_bc_address(encode_point(public_key, compressed))
    if VERBOSE: print 'address:\n', address
    verify_result = public_key.verify_digest( signature, Hash( msg_magic( message ) ), sigdecode = ecdsa.util.sigdecode_string)
    if not verify_result:
        raise BaseException('error: verify digest error.')

    for i in range(4):
        nV = 27 + i
        if compressed:
            nV += 4
        sig = base64.b64encode( chr(nV) + signature )
        try:
            if verify_message( address, sig, message):
                break
        except Exception:
            print('warn: sign failed %d times with secret:%s, message:%s, compressed:%s' % (nV,secret,message,compressed))
    else:
        raise BaseException("error: cannot sign message")

    return sig


def sign_message_with_private_key(base58_priv_key, message, compressed=True):
    encoded_priv_key_bytes = b58decode(base58_priv_key, None)
    encoded_priv_key_hex_string = encoded_priv_key_bytes.encode('hex')

    secret_hex_string = ''
    if base58_priv_key[0] == 'L' or base58_priv_key[0] == 'K':
        if not len(encoded_priv_key_hex_string) == 76:
            raise BaseException("error: length of uncompressed hex private key is not 76")
        # strip leading 0x08, 0x01 compressed flag, checksum
        secret_hex_string = encoded_priv_key_hex_string[2:-10]
    elif base58_priv_key[0] == '5':
        if not len(encoded_priv_key_hex_string) == 74:
            raise BaseException("error: length of compressed hex private key is not 74")
        # strip leading 0x08 and checksum
        secret_hex_string = encoded_priv_key_hex_string[2:-8]
    else:
        raise BaseException("error: private must start with 5 if uncompressed or L/K for compressed")

    if VERBOSE: print 'secret_hex_string:\n', secret_hex_string
    secret = int(secret_hex_string, 16)

    checksum = Hash(encoded_priv_key_bytes[:-4])[:4].encode('hex')
    if VERBOSE: print 'checksum:\n', checksum
    if not checksum == encoded_priv_key_hex_string[-8:]: #make sure private key is valid
        raise BaseException("error: checksum error.")
    if VERBOSE: print 'secret:\n', secret
    return sign_message_with_secret(secret, message, compressed)


def sign_and_verify(wifPrivateKey, message, bitcoinaddress, compressed=True):
    sig = sign_message_with_private_key(wifPrivateKey, message, compressed)
    verify_result = verify_message(bitcoinaddress, sig, message)
    if not verify_result:
        raise BaseException("error: verify error.")

    if VERBOSE: print 'verify_message:', verify_message(bitcoinaddress, sig, message)
    return sig


def test_sign_messages():
    wif1 = '5KMWWy2d3Mjc8LojNoj8Lcz9B1aWu8bRofUgGwQk959Dw5h2iyw'
    compressedPrivKey1 = 'L41XHGJA5QX43QRG3FEwPbqD5BYvy6WxUxqAMM9oQdHJ5FcRHcGk'
    addressUncompressesed1 = '1HUBHMij46Hae75JPdWjeZ5Q7KaL7EFRSD'
    addressCompressesed1 = '14dD6ygPi5WXdwwBTt1FBZK3aD8uDem1FY'
    msg1 = 'test message'
    print 'sig:\n', sign_and_verify(wif1, msg1, addressUncompressesed1, False) # good
    print 'sig:\n', sign_and_verify(wif1, msg1, addressCompressesed1) # good
    #print 'sig:\n', sign_and_verify(wif1, msg1, addressUncompressesed1) # bad
    #print 'sig:\n', sign_and_verify(wif1, msg1, addressCompressesed1, False) # bad

    print 'sig:\n', sign_and_verify(compressedPrivKey1, msg1, addressCompressesed1) # good
    print 'sig:\n', sign_and_verify(compressedPrivKey1, msg1, addressUncompressesed1, False) # good
    #print 'sig:\n', sign_and_verify(compressedPrivKey1, msg1, addressUncompressesed1) # bad
    #print 'sig:\n', sign_and_verify(compressedPrivKey1, msg1, addressCompressesed1, False) # bad


def sign_input_message(address, message, base58_priv_key):

    """
    address = '14dD6ygPi5WXdwwBTt1FBZK3aD8uDem1FY'
    message = 'test message'
    base58_priv_key = 'L41XHGJA5QX43QRG3FEwPbqD5BYvy6WxUxqAMM9oQdHJ5FcRHcGk'
    #"""
    compressed = True
    if base58_priv_key[0] == 'L' or base58_priv_key[0] == 'K':
        compressed = True
    elif base58_priv_key[0] == '5':
        compressed = False
    else:
        raise BaseException("error: private must start with 5 if uncompressed or L/K for compressed")
    return sign_and_verify(base58_priv_key, message, address, compressed)


def verify_input_message(address, message, signature):

    """
    address = '14dD6ygPi5WXdwwBTt1FBZK3aD8uDem1FY'
    message = 'test message'
    signature = 'IPn9bbEdNUp6+bneZqE2YJbq9Hv5aNILq9E5eZoMSF3/fBX4zjeIN6fpXfGSGPrZyKfHQ/c/kTSP+NIwmyTzMfk='
    #"""
    return verify_message(address, signature, message)

def generate_address(base58_priv_key):
    compressed = True
    if base58_priv_key[0] == 'L' or base58_priv_key[0] == 'K':
        compressed = True
    elif base58_priv_key[0] == '5':
        compressed = False
    else:
        raise BaseException("error: private must start with 5 if uncompressed or L/K for compressed")
    encoded_priv_key_bytes = b58decode(base58_priv_key, None)
    encoded_priv_key_hex_string = encoded_priv_key_bytes.encode('hex')

    secret_hex_string = ''
    if base58_priv_key[0] == 'L' or base58_priv_key[0] == 'K':
        len_assert = len(encoded_priv_key_hex_string) == 76
        if not len_assert:
            raise BaseException("error: length of uncompressed hex private key is not 76.")
        # strip leading 0x08, 0x01 compressed flag, checksum
        secret_hex_string = encoded_priv_key_hex_string[2:-10]
    elif base58_priv_key[0] == '5':
        len_assert = len(encoded_priv_key_hex_string) == 74
        if not len_assert:
            raise BaseException("error: length of compressed hex private key is not 74. ")
        # strip leading 0x08 and checksum
        secret_hex_string = encoded_priv_key_hex_string[2:-8]
    else:
        raise BaseException("error: private must start with 5 if uncompressed or L/K for compressed")
    if VERBOSE: print 'secret_hex_string:\n', secret_hex_string
    secret = int(secret_hex_string, 16)

    checksum = Hash(encoded_priv_key_bytes[:-4])[:4].encode('hex')
    if VERBOSE: print 'checksum:\n', checksum
    if not checksum == encoded_priv_key_hex_string[-8:]: #make sure private key is valid
        raise BaseException("error: checksum checked failed.")
    if VERBOSE: print 'secret:\n', secret
    private_key = ecdsa.SigningKey.from_secret_exponent( secret, SECP256k1 )
    public_key = private_key.get_verifying_key()
    address = public_key_to_bc_address(encode_point(public_key, compressed))
    return address


def main():
    argv = sys.argv
    if argv[1] == "sign":
        print sign_input_message(argv[2], argv[3], argv[4])
    elif argv[1] == "verify":
        print verify_input_message(argv[2], argv[3], argv[4])


if __name__ == '__main__':
    #test_sign_messages()
    # main()
    print generate_address('L41XHGJA5QX43QRG3FEwPbqD5BYvy6WxUxqAMM9oQdHJ5FcRHcGk')