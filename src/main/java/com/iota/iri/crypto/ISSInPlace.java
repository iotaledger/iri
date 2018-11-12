package com.iota.iri.crypto;

import java.util.Arrays;

/**
 * (c) 2016 Come-from-Beyond <br>
 *
 * IOTA Signature Scheme. <br>
 * Based on Winternitz One Time Signatures.<br>
 * Implemented in place - does not allocate memory.
 *
 * @see ISS ISS for popular usage
 */
public class ISSInPlace {

    public static final int NUMBER_OF_FRAGMENT_CHUNKS = 27;
    public static final int FRAGMENT_LENGTH = Kerl.HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS;
    public static final int TRYTE_WIDTH = 3;
    private static final int NUMBER_OF_SECURITY_LEVELS = 3;
    public static final int NORMALIZED_FRAGMENT_LENGTH = Kerl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS;
    private static final byte MIN_TRIT_VALUE = -1, MAX_TRIT_VALUE = 1;
    private static final byte MIN_TRYTE_VALUE = -13, MAX_TRYTE_VALUE = 13;

    public static void subseed(SpongeFactory.Mode mode, byte[] subseed, int index) {

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

    public static void key(SpongeFactory.Mode mode, final byte[] subseed, byte[] key) {

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

    public static void digests(SpongeFactory.Mode mode, final byte[] key, byte[] digests) {

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

    public static void address(SpongeFactory.Mode mode, final byte[] digests, byte[] address) {

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

    public static void digest(SpongeFactory.Mode mode, final byte[] normalizedBundleFragment, int nbOff,
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

    public static void normalizedBundle(final byte[] bundle, byte[] normalizedBundle) {
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
}
