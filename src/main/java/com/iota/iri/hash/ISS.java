package com.iota.iri.hash;

import java.util.Arrays;

/**
 * (c) 2016 Come-from-Beyond
 */
public class ISS {

    public static final int NUMBER_OF_FRAGMENT_CHUNKS = 27;
    private static final int FRAGMENT_LENGTH = Curl.HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS;
    private static final int NUMBER_OF_SECURITY_LEVELS = 3;

    private static final int MIN_TRIT_VALUE = -1, MAX_TRIT_VALUE = 1;
    private static final int TRYTE_WIDTH = 3;
    private static final int MIN_TRYTE_VALUE = -13, MAX_TRYTE_VALUE = 13;

    public static int[] subseed(final int[] seed, int index) {

        if (index < 0) {
            throw new RuntimeException("Invalid subseed index: " + index);
        }

        final int[] subseedPreimage = Arrays.copyOf(seed, seed.length);

        while (index-- > 0) {

            for (int i = 0; i < subseedPreimage.length; i++) {

                if (++subseedPreimage[i] > MAX_TRIT_VALUE) {
                    subseedPreimage[i] = MIN_TRIT_VALUE;
                } else {
                    break;
                }
            }
        }

        final int[] subseed = new int[Curl.HASH_LENGTH];

        final Curl hash = new Curl();
        hash.absorb(subseedPreimage, 0, subseedPreimage.length);
        hash.squeeze(subseed, 0, subseed.length);
        return subseed;
    }

    public static int[] key(final int[] subseed, final int numberOfFragments) {

        if (subseed.length != Curl.HASH_LENGTH) {
            throw new RuntimeException("Invalid subseed length: " + subseed.length);
        }
        if (numberOfFragments <= 0) {
            throw new RuntimeException("Invalid number of key fragments: " + numberOfFragments);
        }

        final int[] key = new int[FRAGMENT_LENGTH * numberOfFragments];

        final Curl hash = new Curl();
        hash.absorb(subseed, 0, subseed.length);
        hash.squeeze(key, 0, key.length);
        return key;
    }

    public static int[] digests(final int[] key) {

        if (key.length == 0 || key.length % FRAGMENT_LENGTH != 0) {

            throw new RuntimeException("Invalid key length: " + key.length);
        }

        final int[] digests = new int[key.length / FRAGMENT_LENGTH * Curl.HASH_LENGTH];

        for (int i = 0; i < key.length / FRAGMENT_LENGTH; i++) {

            final int[] buffer = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH);
            for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

                for (int k = MAX_TRYTE_VALUE - MIN_TRYTE_VALUE; k-- > 0; ) {
                    final Curl hash = new Curl();
                    hash.absorb(buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                    hash.squeeze(buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                }
            }
            final Curl hash = new Curl();
            hash.absorb(buffer, 0, buffer.length);
            hash.squeeze(digests, i * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
        }

        return digests;
    }

    public static int[] address(final int[] digests) {

        if (digests.length == 0 || digests.length % Curl.HASH_LENGTH != 0) {
            throw new RuntimeException("Invalid digests length: " + digests.length);
        }

        final int[] address = new int[Curl.HASH_LENGTH];

        final Curl hash = new Curl();
        hash.absorb(digests, 0, digests.length);
        hash.squeeze(address, 0, address.length);

        return address;
    }

    public static int[] normalizedBundle(final int[] bundle) {

        if (bundle.length != Curl.HASH_LENGTH) {
            throw new RuntimeException("Invalid bundleValidator length: " + bundle.length);
        }

        final int[] normalizedBundle = new int[Curl.HASH_LENGTH / TRYTE_WIDTH];

        for (int i = 0; i < NUMBER_OF_SECURITY_LEVELS; i++) {

            int sum = 0;
            for (int j = i * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j < (i + 1) * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j++) {

                normalizedBundle[j] = bundle[j * TRYTE_WIDTH] + bundle[j * TRYTE_WIDTH + 1] * 3 + bundle[j * TRYTE_WIDTH + 2] * 9;
                sum += normalizedBundle[j];
            }
            if (sum > 0) {

                while (sum-- > 0) {

                    for (int j = i * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j < (i + 1) * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j++) {

                        if (normalizedBundle[j] > MIN_TRYTE_VALUE) {
                            normalizedBundle[j]--;
                            break;
                        }
                    }
                }

            } else {

                while (sum++ < 0) {

                    for (int j = i * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j < (i + 1) * (Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS); j++) {

                        if (normalizedBundle[j] < MAX_TRYTE_VALUE) {
                            normalizedBundle[j]++;
                            break;
                        }
                    }
                }
            }
        }

        return normalizedBundle;
    }

    public static int[] signatureFragment(final int[] normalizedBundleFragment, final int[] keyFragment) {

        if (normalizedBundleFragment.length != Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) {
            throw new RuntimeException("Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.length);
        }
        if (keyFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid key fragment length: " + keyFragment.length);
        }

        final int[] signatureFragment = Arrays.copyOf(keyFragment, keyFragment.length);

        for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

            for (int k = MAX_TRYTE_VALUE - normalizedBundleFragment[j]; k-- > 0; ) {

                final Curl hash = new Curl();
                hash.absorb(signatureFragment, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                hash.squeeze(signatureFragment, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
            }
        }

        return signatureFragment;
    }

    public static int[] digest(final int[] normalizedBundleFragment, final int[] signatureFragment) {

        if (normalizedBundleFragment.length != Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) {
            throw new RuntimeException("Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.length);
        }
        if (signatureFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid signature fragment length: " + signatureFragment.length);
        }

        final int[] digest = new int[Curl.HASH_LENGTH];

        final int[] buffer = Arrays.copyOf(signatureFragment, FRAGMENT_LENGTH);
        for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

            for (int k = normalizedBundleFragment[j] - MIN_TRYTE_VALUE; k-- > 0; ) {

                final Curl hash = new Curl();
                hash.absorb(buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                hash.squeeze(buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
            }
        }
        final Curl hash = new Curl();
        hash.absorb(buffer, 0, buffer.length);
        hash.squeeze(digest, 0, digest.length);

        return digest;
    }

    public static int[] getMerkleRoot(int[] hash, int[] trits, int offset, int index, int size) {
        for (int i = 0; i < size; i++) {
            final Curl curl = new Curl();
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
        return hash;
    }
}
