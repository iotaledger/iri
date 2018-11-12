package com.iota.iri.crypto;

import java.util.Arrays;

import com.iota.iri.model.Hash;

/**
 * (c) 2016 Come-from-Beyond <br>
 * 
 * IOTA Signature Scheme. <br>
 * Based on Winternitz One Time Signatures.<br>
 *
 * Popular usages:<br>
 * <b>Address(subseed)</b>
 * <pre>
 * key = key(subseed)
 * digests = digests(key)
 * return address(digests)
 * </pre>
 *
 * <b>Sign(message, subseed)</b>
 *  <pre>
 *  key = key(subseed)
 *  messageHash = sponge.hash(message)
 *  normalizedHash = normalizedBundle(messageHash)
 *  return signatureFragment(normalizedHash, key)
 *  </pre>
 *
 * <b>Verify(messageHash, signature, address)</b>
 *  <pre>
 *  normalizedHash = normalizedBundle(messageHash)
 *  signatureDigests = digest(normalizedHash, signature)
 *  return (address == address(signatureDigests))
 *  </pre>
 *
 */
public class ISS {

    public static final int NUMBER_OF_FRAGMENT_CHUNKS = 27;
    public static final int FRAGMENT_LENGTH = Curl.HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS;
    private static final int NUMBER_OF_SECURITY_LEVELS = 3;

    private static final int MIN_TRIT_VALUE = -1, MAX_TRIT_VALUE = 1;
    public static final int TRYTE_WIDTH = 3;
    private static final int MIN_TRYTE_VALUE = -13, MAX_TRYTE_VALUE = 13;
    public static final int NORMALIZED_FRAGMENT_LENGTH = Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS;


    ///////////////////////////////////////////////Key generation////////////////////////////////////////////////////

    /**
     * Calculates a {@code subSeed} from a given {@code seed} and {@code index}
     * <pre>
     * subSeed = Hash(seed + index)
     * </pre>
     *
     * @param mode Hash function to be used
     * @param seed trit array of master seed
     * @param index incremental index of the subseed
     * @return the subseed
     */
    public static byte[] subseed(SpongeFactory.Mode mode, final byte[] seed, int index) {

        if (index < 0) {
            throw new RuntimeException("Invalid subseed index: " + index);
        }

        final byte[] subseedPreimage = Arrays.copyOf(seed, seed.length);

        while (index-- > 0) {

            for (int i = 0; i < subseedPreimage.length; i++) {

                if (++subseedPreimage[i] > MAX_TRIT_VALUE) {
                    subseedPreimage[i] = MIN_TRIT_VALUE;
                } else {
                    break;
                }
            }
        }

        final byte[] subseed = new byte[Curl.HASH_LENGTH];

        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(subseedPreimage, 0, subseedPreimage.length);
        hash.squeeze(subseed, 0, subseed.length);
        return subseed;
    }

    /**
     * Derives a secret key of length {@code FRAGMENT_LENGTH} * {@code numberOfFragments} from subseed. <br>
     * <pre>
     *     key = squeeze(absorb(subseed), length = FRAGMENT_LENGTH * numberOfFragments)
     * </pre>
     *
     * @param mode Hash function to be used
     * @param subseed secret seed
     * @param numberOfFragments desired security level, [1, 2, 3]
     * @return secret key
     */
    public static byte[] key(SpongeFactory.Mode mode, final byte[] subseed, final int numberOfFragments) {

        if (subseed.length != Curl.HASH_LENGTH) {
            throw new RuntimeException("Invalid subseed length: " + subseed.length);
        }
        if (numberOfFragments <= 0) {
            throw new RuntimeException("Invalid number of key fragments: " + numberOfFragments);
        }

        final byte[] key = new byte[FRAGMENT_LENGTH * numberOfFragments];

        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(subseed, 0, subseed.length);
        hash.squeeze(key, 0, key.length);
        return key;
    }

    /**
     * Calculates the digest of the public key <br>
     * <pre>
     *     n = NUMBER_OF_FRAGMENT_CHUNKS
     *     pub_keys(1, .. , n) = (Hash^27(key_1), .. , Hash^27(key_n))
     *     digest = Hash(pub_keys)
     *
     *     *process repeats for each security level (implicit from key.length)
     * </pre>
     * @param mode Hash function to be used
     * @param key secret key
     * @return digests of the public key
     */
    public static byte[] digests(SpongeFactory.Mode mode, final byte[] key) {

        if (key.length == 0 || key.length % FRAGMENT_LENGTH != 0) {
            throw new RuntimeException("Invalid key length: " + key.length);
        }

        final byte[] digests = new byte[key.length / FRAGMENT_LENGTH * Curl.HASH_LENGTH];
        final Sponge hash = SpongeFactory.create(mode);

        for (int i = 0; i < key.length / FRAGMENT_LENGTH; i++) {

            final byte[] buffer = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH);
            for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

                for (int k = MAX_TRYTE_VALUE - MIN_TRYTE_VALUE; k-- > 0;) {
                    hash.reset();
                    hash.absorb(buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                    hash.squeeze(buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                }
            }
            hash.reset();
            hash.absorb(buffer, 0, buffer.length);
            hash.squeeze(digests, i * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
        }

        return digests;
    }

    /**
     * Calculates the public address <br>
     * <pre>
     *     address = hash(digests)
     * </pre>
     * @param mode Hash function to be used
     * @param digests digests of the public key
     * @return public address
     */
    public static byte[] address(SpongeFactory.Mode mode, final byte[] digests) {

        if (digests.length == 0 || digests.length % Curl.HASH_LENGTH != 0) {
            throw new RuntimeException("Invalid digests length: " + digests.length);
        }

        final byte[] address = new byte[Curl.HASH_LENGTH];

        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(digests, 0, digests.length);
        hash.squeeze(address, 0, address.length);

        return address;
    }

/////////////////////////////////////////////////Signing////////////////////////////////////////////////////////

    /**
     * Deterministically Normalize the bundle hash. <br>
     *     <ol>
     *         <li>map each tryte in {@code bundle} to balanced base-27 {@code [-13 , 13]} </li>
     *         <li>sum all mapped trytes together</li>
     *         <li>if sum != 0, start inc/dec each tryte till sum equals 0</li>
     *     </ol>
     *
     * @param bundle hash to be normalized
     * @return normalized hash
     */
    public static byte[] normalizedBundle(final byte[] bundle) {

        if (bundle.length != Curl.HASH_LENGTH) {
            throw new RuntimeException("Invalid bundleValidator length: " + bundle.length);
        }

        final byte[] normalizedBundle = new byte[Curl.HASH_LENGTH / TRYTE_WIDTH];

        ISSInPlace.normalizedBundle(bundle, normalizedBundle);
        return normalizedBundle;
    }

    /**
     * Generates a signature for {@code normalizedBundleFragment} <br>
     * <pre>
     *     M = normalizedBundleFragment
     *     sig(1, .. , n) = (Hash^(M_1)(key_1), .. , Hash^(M_n)(key_n))
     * </pre>
     *
     * @param mode  Hash function to be used
     * @param normalizedBundleFragment normalized hash of the message to be signed
     * @param keyFragment secret key
     * @return signature
     */
    public static byte[] signatureFragment(SpongeFactory.Mode mode, final byte[] normalizedBundleFragment,
                                           final byte[] keyFragment) {

        if (normalizedBundleFragment.length != NORMALIZED_FRAGMENT_LENGTH) {
            throw new RuntimeException(
                    "Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.length);
        }
        if (keyFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid key fragment length: " + keyFragment.length);
        }

        final byte[] signatureFragment = Arrays.copyOf(keyFragment, keyFragment.length);
        final Sponge hash = SpongeFactory.create(mode);

        for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

            for (int k = MAX_TRYTE_VALUE - normalizedBundleFragment[j]; k-- > 0;) {
                hash.reset();
                hash.absorb(signatureFragment, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                hash.squeeze(signatureFragment, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
            }
        }

        return signatureFragment;
    }

////////////////////////////////////////////Validation//////////////////////////////////////////////////
    /**
     * recalculate the digest of the public key used to sign {@code signatureFragment}. <br>
     * <pre>
     *     n = NUMBER_OF_FRAGMENT_CHUNKS
     *     M = normalizedBundleFragment
     *
     *     pub_keys'(1, .. , n) = (Hash^(27-M_1)(sig_1), .. , Hash^(27-M_n)(sig_n))
     *     *process repeats for each security level (implicit from key.length)
     *
     *     return Hash(pub_keys')
     * </pre>
     *
     * @param mode Hash function to be used
     * @param normalizedBundleFragment normalized hash of the message signed
     * @param signatureFragment signature
     * @return digest of the public key used
     */
    public static byte[] digest(SpongeFactory.Mode mode, final byte[] normalizedBundleFragment,
                                final byte[] signatureFragment) {

        if (normalizedBundleFragment.length != Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) {
            throw new RuntimeException(
                    "Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.length);
        }
        if (signatureFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid signature fragment length: " + signatureFragment.length);
        }

        final byte[] digest = new byte[Curl.HASH_LENGTH];
        ISSInPlace.digest(mode, normalizedBundleFragment, 0, signatureFragment, 0, digest);
        return digest;
    }

    /**
     * Calculates the {@code root} of a Merkle tree, given a leaf and Merkle path<br>
     * @see <a href="https://en.wikipedia.org/wiki/Merkle_tree">https://en.wikipedia.org/wiki/Merkle_tree</a><br>
     *
     * @param mode Hash function to be used
     * @param hash leaf of Merkle tree
     * @param trits Merkle path, the siblings of the leaf concatenated.
     * @param offset starting position in {@code trits}
     * @param indexIn leaf index (used to determine order of concatenation)
     * @param depth depth of Merkle tree
     * @return the root, the consecutive hashing of the leaf and the Merkle path.
     */
    public static byte[] getMerkleRoot(SpongeFactory.Mode mode, byte[] hash, byte[] trits, int offset,
                                       final int indexIn, int depth) {
        int index = indexIn;
        final Sponge curl = SpongeFactory.create(mode);
        for (int i = 0; i < depth; i++) {
            curl.reset();
            if ((index & 1) == 0) {
                curl.absorb(hash, 0, hash.length);
                curl.absorb(trits, offset + i * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
            } else {
                curl.absorb(trits, offset + i * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                curl.absorb(hash, 0, hash.length);
            }
            curl.squeeze(hash, 0, hash.length);

            index >>= 1;
        }
        if (index != 0) {
            return Hash.NULL_HASH.trits();
        }
        return hash;
    }
}
