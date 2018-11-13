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

        final byte[] subseed = Arrays.copyOf(seed, seed.length);
        subseedInPlace(mode, subseed, index);
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

        keyInPlace(mode, subseed, key);
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

        digestsInPlace(mode, key, digests);
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

        addressInPlace(mode, digests, address);
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

        normalizedBundleInPlace(bundle, normalizedBundle);
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

        final byte[] signatureFragment = new byte[keyFragment.length];

        signatureFragmentInPlace(mode, normalizedBundleFragment, keyFragment, signatureFragment);
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
        digestInPlace(mode, normalizedBundleFragment, 0, signatureFragment, 0, digest);
        return digest;
    }

    /**
     * Calculates the {@code root} of a Merkle tree, given a leaf and Merkle path<br>
     * @see <a href="https://en.wikipedia.org/wiki/Merkle_tree">https://en.wikipedia.org/wiki/Merkle_tree</a><br>
     *
     * @param mode Hash function to be used
     * @param leaf leaf of Merkle tree
     * @param trits Merkle path, the siblings of the leaf concatenated.
     * @param offset starting position in {@code trits}
     * @param indexIn leaf index (used to determine order of concatenation)
     * @param depth depth of Merkle tree
     * @return the root, the consecutive hashing of the leaf and the Merkle path.
     */
    public static byte[] getMerkleRoot(SpongeFactory.Mode mode, byte[] leaf, byte[] trits, int offset,
                                       final int indexIn, int depth) {

        byte[] root = new byte[leaf.length];
        getMerkleRootInPlace(mode, leaf, trits, offset, indexIn, depth, root);
        return root;
    }


    /////////////////////////////////////////////// In Place ////////////////////////////////////////////////////

    /**
     * recalculate the digest of the public key used to sign {@code signatureFragment}. <br>
     *     result is written to {@code digest}
     * @see #digest(SpongeFactory.Mode, byte[], byte[])
     * @param mode Hash function to be used
     * @param normalizedBundleFragment normalized hash of the message signed
     * @param nbOff normalized hash starting position
     * @param signatureFragment signature
     * @param sfOff signature starting position
     * @param digest digest of the public key used (result)
     */
    public static void digestInPlace(SpongeFactory.Mode mode, final byte[] normalizedBundleFragment, int nbOff,
                                     final byte[] signatureFragment, int sfOff, byte[] digest) {

        if (normalizedBundleFragment.length - nbOff < (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS)) {
            throw new RuntimeException(
                    "Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.length);
        }
        if (signatureFragment.length - sfOff < FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid signature fragment length: " + signatureFragment.length);
        }

        if (digest.length != Curl.HASH_LENGTH) {
            throw new IllegalArgumentException("Invalid digest array length.");
        }

        final byte[] buffer = Arrays.copyOfRange(signatureFragment, sfOff, sfOff + FRAGMENT_LENGTH);
        final Sponge hash = SpongeFactory.create(mode);

        for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

            for (int k = normalizedBundleFragment[nbOff + j] - MIN_TRYTE_VALUE; k-- > 0;) {
                hash.reset();
                hash.absorb(buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                hash.squeeze(buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
            }
        }
        hash.reset();
        hash.absorb(buffer, 0, buffer.length);
        hash.squeeze(digest, 0, digest.length);
    }

    /**
     * Deterministically Normalize the bundle hash. <br>
     *     result is written to {@code normalizedBundle}
     * @see #normalizedBundle(byte[])
     *
     * @param bundle hash to be normalized
     * @param normalizedBundle normalized hash (result)
     */
    public static void normalizedBundleInPlace(final byte[] bundle, byte[] normalizedBundle) {
        if (bundle.length != Curl.HASH_LENGTH) {
            throw new RuntimeException("Invalid bundleValidator length: " + bundle.length);
        }

        for (int i = 0; i < NUMBER_OF_SECURITY_LEVELS; i++) {
            int sum = 0;
            for (int j = i * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS);
                 j < (i + 1) * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j++) {

                normalizedBundle[j] = (byte) (bundle[j * TRYTE_WIDTH] + bundle[j * TRYTE_WIDTH + 1] * 3
                        + bundle[j * TRYTE_WIDTH + 2] * 9);
                sum += normalizedBundle[j];
            }
            if (sum > 0) {
                while (sum-- > 0) {

                    for (int j = i * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS);
                         j < (i + 1) * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j++) {

                        if (normalizedBundle[j] > MIN_TRYTE_VALUE) {
                            normalizedBundle[j]--;
                            break;
                        }
                    }
                }
            } else {
                while (sum++ < 0) {
                    for (int j = i * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS);
                         j < (i + 1) * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j++) {

                        if (normalizedBundle[j] < MAX_TRYTE_VALUE) {
                            normalizedBundle[j]++;
                            break;
                        }
                    }
                }
            }
        }
    }

    static void subseedInPlace(SpongeFactory.Mode mode, byte[] subseed, int index) {

        if (index < 0) {
            throw new RuntimeException("Invalid subseed index: " + index);
        }

        if (subseed.length != Kerl.HASH_LENGTH) {
            throw new IllegalArgumentException("Subseed array is not of HASH_LENGTH");
        }

        while (index-- > 0) {

            for (int i = 0; i < subseed.length; i++) {

                if (++subseed[i] > MAX_TRIT_VALUE) {
                    subseed[i] = MIN_TRIT_VALUE;
                } else {
                    break;
                }
            }
        }

        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(subseed, 0, subseed.length);
        hash.squeeze(subseed, 0, subseed.length);
    }

    static void keyInPlace(SpongeFactory.Mode mode, final byte[] subseed, byte[] key) {

        if (subseed.length != Kerl.HASH_LENGTH) {
            throw new RuntimeException("Invalid subseed length: " + subseed.length);
        }

        if ((key.length % FRAGMENT_LENGTH) != 0) {
            throw new IllegalArgumentException("key length must be multiple of fragment length");
        }

        int numberOfFragments = key.length / FRAGMENT_LENGTH;

        if (numberOfFragments <= 0) {
            throw new RuntimeException("Invalid number of key fragments: " + numberOfFragments);
        }

        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(subseed, 0, subseed.length);
        hash.squeeze(key, 0, key.length);
    }

    static void digestsInPlace(SpongeFactory.Mode mode, final byte[] key, byte[] digests) {

        if (key.length == 0 || key.length % FRAGMENT_LENGTH != 0) {
            throw new RuntimeException("Invalid key length: " + key.length);
        }

        if (digests.length != (key.length / FRAGMENT_LENGTH * Kerl.HASH_LENGTH)) {
            throw new IllegalArgumentException("Invalid digests length");
        }

        final Sponge hash = SpongeFactory.create(mode);

        for (int i = 0; i < key.length / FRAGMENT_LENGTH; i++) {

            final byte[] buffer = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH);
            for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

                for (int k = MAX_TRYTE_VALUE - MIN_TRYTE_VALUE; k-- > 0;) {
                    hash.reset();
                    hash.absorb(buffer, j * Kerl.HASH_LENGTH, Kerl.HASH_LENGTH);
                    hash.squeeze(buffer, j * Kerl.HASH_LENGTH, Kerl.HASH_LENGTH);
                }
            }
            hash.reset();
            hash.absorb(buffer, 0, buffer.length);
            hash.squeeze(digests, i * Kerl.HASH_LENGTH, Kerl.HASH_LENGTH);
        }
    }

    static void addressInPlace(SpongeFactory.Mode mode, final byte[] digests, byte[] address) {

        if (digests.length == 0 || digests.length % Kerl.HASH_LENGTH != 0) {
            throw new RuntimeException("Invalid digests length: " + digests.length);
        }

        if (address.length != Kerl.HASH_LENGTH) {
            throw new IllegalArgumentException("Invalid address length");
        }

        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(digests, 0, digests.length);
        hash.squeeze(address, 0, address.length);
    }

    static void signatureFragmentInPlace(SpongeFactory.Mode mode, final byte[] normalizedBundleFragment,
                                           final byte[] keyFragment, byte[] signatureFragment) {

        if (normalizedBundleFragment.length != NORMALIZED_FRAGMENT_LENGTH) {
            throw new RuntimeException(
                    "Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.length);
        }
        if (keyFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid key fragment length: " + keyFragment.length);
        }

        System.arraycopy(keyFragment, 0, signatureFragment, 0, keyFragment.length);

        final Sponge hash = SpongeFactory.create(mode);

        for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

            for (int k = MAX_TRYTE_VALUE - normalizedBundleFragment[j]; k-- > 0;) {
                hash.reset();
                hash.absorb(signatureFragment, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                hash.squeeze(signatureFragment, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
            }
        }
    }

    static void getMerkleRootInPlace(SpongeFactory.Mode mode, byte[] leaf, byte[] trits, int offset,
                                     int index, int depth, byte[] hash) {

        System.arraycopy(leaf, 0 , hash, 0 , leaf.length);
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
            Arrays.fill(hash, (byte) 0);
        }
    }
}
