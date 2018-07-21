package com.iota.iri.hash;

import com.iota.iri.model.Hash;

import java.util.Arrays;

/**
 * (c) 2016 Come-from-Beyond
 */
public class ISS {

    public static final int NUMBER_OF_FRAGMENT_CHUNKS = 27;
    public static final int FRAGMENT_LENGTH = Curl.HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS;
    private static final int NUMBER_OF_SECURITY_LEVELS = 3;

    private static final int MIN_TRIT_VALUE = -1, MAX_TRIT_VALUE = 1;
    public static final int TRYTE_WIDTH = 3;
    private static final int MIN_TRYTE_VALUE = -13, MAX_TRYTE_VALUE = 13;
    public static final int NORMALIZED_FRAGMENT_LENGTH = Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS;

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

    public static byte[] digests(SpongeFactory.Mode mode, final byte[] key) {

        if (key.length == 0 || key.length % FRAGMENT_LENGTH != 0) {
            throw new RuntimeException("Invalid key length: " + key.length);
        }

        final byte[] digests = new byte[key.length / FRAGMENT_LENGTH * Curl.HASH_LENGTH];
        final Sponge hash = SpongeFactory.create(mode);

        for (int i = 0; i < key.length / FRAGMENT_LENGTH; i++) {

            final byte[] buffer = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH);
            for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

                for (int k = MAX_TRYTE_VALUE - MIN_TRYTE_VALUE; k-- > 0; ) {
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

    public static byte[] normalizedBundle(final byte[] bundle) {

        if (bundle.length != Curl.HASH_LENGTH) {
            throw new RuntimeException("Invalid bundleValidator length: " + bundle.length);
        }

        final byte[] normalizedBundle = new byte[Curl.HASH_LENGTH / TRYTE_WIDTH];

        ISSInPlace.normalizedBundle(bundle, normalizedBundle);
        return normalizedBundle;
    }

    public static byte[] signatureFragment(SpongeFactory.Mode mode, final byte[] normalizedBundleFragment, final byte[] keyFragment) {

        if (normalizedBundleFragment.length != NORMALIZED_FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.length);
        }
        if (keyFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid key fragment length: " + keyFragment.length);
        }

        final byte[] signatureFragment = Arrays.copyOf(keyFragment, keyFragment.length);
        final Sponge hash = SpongeFactory.create(mode);

        for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

            for (int k = MAX_TRYTE_VALUE - normalizedBundleFragment[j]; k-- > 0; ) {
                hash.reset();
                hash.absorb(signatureFragment, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                hash.squeeze(signatureFragment, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
            }
        }

        return signatureFragment;
    }

    public static byte[] digest(SpongeFactory.Mode mode, final byte[] normalizedBundleFragment, final byte[] signatureFragment) {

        if (normalizedBundleFragment.length != Curl.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) {
            throw new RuntimeException("Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.length);
        }
        if (signatureFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid signature fragment length: " + signatureFragment.length);
        }

        final byte[] digest = new byte[Curl.HASH_LENGTH];
        ISSInPlace.digest(mode, normalizedBundleFragment, 0, signatureFragment, 0, digest);
        return digest;
    }

    public static byte[] getMerkleRoot(SpongeFactory.Mode mode, byte[] hash, byte[] trits, int offset, final int indexIn, int size) {
        int index = indexIn;
        final Sponge curl = SpongeFactory.create(mode);
        for (int i = 0; i < size; i++) {
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
        if(index != 0) {
            return Hash.NULL_HASH.trits();
        }
        return hash;
    }
}
