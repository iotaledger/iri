package com.iota.iri.hash;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * (c) 2016 Come-from-Beyond and Paul Handy
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

    private static int[] privateKey(final int[] subseed, final int keySize) {
        final int[] key = new int[keySize];

        final Curl hash = new Curl();
        hash.absorb(subseed, 0, subseed.length);
        hash.squeeze(key, 0, key.length);
        return key;
    }

    public static int[] key(final int[] subseed, final int numberOfFragments) {

        if (subseed.length != Curl.HASH_LENGTH) {
            throw new RuntimeException("Invalid subseed length: " + subseed.length);
        }
        if (numberOfFragments <= 0) {
            throw new RuntimeException("Invalid number of key fragments: " + numberOfFragments);
        }
        return privateKey(subseed, FRAGMENT_LENGTH * numberOfFragments);
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

    public static int[] publicKey(final int[] digests, final int keySize) {
        if (digests.length == 0 || digests.length % Curl.HASH_LENGTH != 0) {
            throw new RuntimeException("Invalid digests length: " + digests.length);
        }

        final int[] publicKey = new int[keySize];

        final Curl hash = new Curl();
        hash.absorb(digests, 0, digests.length);
        hash.squeeze(publicKey, 0, publicKey.length);

        return publicKey;
    }
    public static int[] address(final int[] digests) {
        return publicKey(digests, Curl.HASH_LENGTH);
    }

    public static int[] normalizedBundle(final int[] bundle) {

        if (bundle.length != Curl.HASH_LENGTH) {
            throw new RuntimeException("Invalid bundle length: " + bundle.length);
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
            throw new RuntimeException("Invalid normalized bundle fragment length: " + normalizedBundleFragment.length);
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
            throw new RuntimeException("Invalid normalized bundle fragment length: " + normalizedBundleFragment.length);
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

    public static MerkleHash merkleTree(int[] seed, int startingIndex, int numberOfKeysToCreate, final int merkleKeySize) {
        if(merkleKeySize % 81 != 0)
            throw new RuntimeException("Invalid key Size: " + merkleKeySize);
        int hashSize;
        Object[] keys = IntStream.range(0, numberOfKeysToCreate).parallel()
                .mapToObj(i -> MerkleHash.create(
                        publicKey(
                                digests(
                                        privateKey(
                                                subseed(seed,i + startingIndex),
                                                merkleKeySize)),
                                merkleKeySize))).toArray();
        hashSize = 1 << (31 - Integer.numberOfLeadingZeros(keys.length));
        while(hashSize > 1) {
            Object[] finalKeys = keys;
            //keys = IntStream.range(0, hashSize).parallel()
            keys = IntStream.iterate(0, i -> i + 2).limit(hashSize).parallel()
                    .mapToObj(i -> {
                        //if(finalKeys.length <= i * 2) {
                        if(finalKeys.length <= i) {
                            return MerkleHash.create(new int[merkleKeySize]);
                        } else {
                            Curl hash = new Curl();
                            int[] hashTrits = new int[merkleKeySize];
                            //MerkleHash second = i*2 + 1 == finalKeys.length ? MerkleHash.create(new int[merkleKeySize]): (MerkleHash) finalKeys[i*2+1];
                            MerkleHash second = i + 1 == finalKeys.length ? MerkleHash.create(new int[merkleKeySize]): (MerkleHash) finalKeys[i+1];
                            //hash.absorb(ArrayUtils.addAll(((MerkleHash)finalKeys[i*2]).value,second.value),
                            hash.absorb(ArrayUtils.addAll(((MerkleHash)finalKeys[i]).value,second.value),
                                    0,
                                    merkleKeySize*2);
                            hash.squeeze(hashTrits, 0, merkleKeySize);
                            MerkleHash mHash = MerkleHash.create(hashTrits);
                            //mHash.first = (MerkleHash)finalKeys[i*2];
                            mHash.first = (MerkleHash)finalKeys[i];
                            mHash.second = second;
                            return mHash;
                        }
                    }).toArray();
            hashSize = 1 << (30 - Integer.numberOfLeadingZeros(keys.length));
        }
        return (MerkleHash) keys[0];
    }

    /*
        Vernam Cipher for trinary logic
        plain (+) key = cipher
     */
    public static Function<int[], int[]> encrypt(int[] key) {
        return (plainTrits) -> {
            assert key.length >= plainTrits.length;
            return IntStream.range(0, plainTrits.length).parallel()
                    .map(i -> Trinary.sum(plainTrits[i]).apply(key[i]))
                    .toArray();
        };
    }
    /*
        cipher (+) !key = plain
     */
    public static Function<int[], int[]> decrypt(int[] key) {
        return (cipherTrits) -> {
            assert key.length >= cipherTrits.length;
            return IntStream.range(0, cipherTrits.length).parallel()
                    .map(i -> Trinary.sum(cipherTrits[i]).apply( - key[i]))
                    .toArray();
        };
    }
}
