package com.iota.iri.hash;

import com.iota.iri.hash.keys.TupleArray;
import com.iota.iri.hash.keys.TupleKeyArray;
import com.iota.iri.hash.keys.MerkleNode;
import com.iota.iri.hash.keys.Tuple;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * (c) 2016 Come-from-Beyond and Paul Handy
 */
public class ISS {

    public static final int NUMBER_OF_FRAGMENT_CHUNKS = 27;
    private static final int FRAGMENT_LENGTH = Curl.HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS;
    private static final int NUMBER_OF_SECURITY_LEVELS = 3;

    private static final int NUMBER_OF_KEYS_PER_TUPLE = 27;
    private static final int NUMBER_OF_COMBINATIONS_PER_KEY = (NUMBER_OF_KEYS_PER_TUPLE + 1)/2;
    private static final int[] OFFSET = new int[] {
            0b011011011011011011011011011,
            0b110110110110110110110110110,
            0b000111111000111111000111111,
            0b111111000111111000111111000,
            0b000000000111111111111111111,
            0b111111111111111111000000000,
    };
    //private static final int MERKLE_SHIFT_MASK = 0b001001001001001001001001001;
    private static final int MERKLE_SHIFT_MASK = 0b0101010101010101010101010101;

    private static final int[] INCREMENT_MASK = IntStream.iterate(1, i -> 1 + (i << 1)).limit(NUMBER_OF_KEYS_PER_TUPLE).toArray();
    private static final int SUBSEED_MASK = 0b111111111111111111111111111;
    private static final int OFFSET_STR_LENGTH = 81;

    private static final int MIN_TRIT_VALUE = -1, MAX_TRIT_VALUE = 1;
    private static final int TRYTE_WIDTH = 3;
    private static final int MIN_TRYTE_VALUE = -13, MAX_TRYTE_VALUE = 13;

    private static int[] subseedPreiamge(final int[] seed, int index) {
        if (index < 0) {
            throw new RuntimeException("Invalid subseed index: " + index);
        }

        final int[] subseedPreimage = Arrays.copyOf(seed, seed.length);

        while (index-- > 0) {
            Converter.increment(subseedPreimage, subseedPreimage.length);
        }

        return subseedPreimage;
    }
    public static int[] subseed(final int[] seed, int index) {

        final int[] subseedPreimage = subseedPreiamge(seed, index);

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


    /*
        Generate 27 seeds at once
     */

    private static Tuple[] offsetSubseed(int[] seed) {
        int initialValue;
        initialValue = (int) Converter.longValue(seed, 0, 3);

        final int split;
        final int[] offset;

        final Tuple[] subseedPreimage = Converter.tuple(seed);

        if(initialValue > MIN_TRYTE_VALUE) {
            split = initialValue - MIN_TRYTE_VALUE;
            offset = Arrays.stream(OFFSET)
                    .map(i -> (((i & ~INCREMENT_MASK[split]) << (MAX_TRYTE_VALUE - split))) |
                            (((i >> (split)) & INCREMENT_MASK[split]) & SUBSEED_MASK))
                    .toArray();
            Converter.increment(seed, seed.length);
            Tuple[] next = Converter.tuple(seed);
            for(int i = 0; i < subseedPreimage.length; i++) {
                subseedPreimage[i].low = (subseedPreimage[i].low & INCREMENT_MASK[split])| (next[i].low & (SUBSEED_MASK & ~INCREMENT_MASK[split]));
                subseedPreimage[i].hi = (subseedPreimage[i].low & INCREMENT_MASK[split])| (next[i].hi & (SUBSEED_MASK & ~INCREMENT_MASK[split]));
            }
        } else {
            offset = OFFSET;
        }
        for(int i = 0; i < 3; i++) {
            subseedPreimage[i].low = offset[i*2];
            subseedPreimage[i].hi = offset[i*2+1];
        }
        return subseedPreimage;
    }

    public static Tuple[] subseeds(final int[] seed, int index) {
        final int[] subseedPreimage = subseedPreiamge(seed, index);

        final Tuple[] subseedTupleImage = offsetSubseed(subseedPreimage);

        return Curl.squeeze(
                Curl.absorb(
                        Curl.state(), subseedTupleImage, 0, subseedTupleImage.length),
                new Tuple[Curl.HASH_LENGTH], 0, Curl.HASH_LENGTH);
    }

    private static Tuple[] privateKeys(final Tuple[] subseed, final int keySize) {
        final Tuple[] key = new Tuple[keySize];
        return Curl.squeeze(Curl.absorb(Curl.state(), subseed, 0, subseed.length),
                new Tuple[keySize], 0, keySize);
    }

    public static Tuple[] digests(final Tuple[] key) {

        if (key.length == 0 || key.length % FRAGMENT_LENGTH != 0) {

            throw new RuntimeException("Invalid key length: " + key.length);
        }

        final Tuple[] digests = new Tuple[key.length / FRAGMENT_LENGTH * Curl.HASH_LENGTH];
        int i, j, k;
        for(i = 0; i < key.length / FRAGMENT_LENGTH; i++) {
            final Tuple[] buffer = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH);
            for(j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {
                for(k = 0; k < MAX_TRYTE_VALUE - MIN_TRYTE_VALUE; k++) {
                    Curl.squeeze(
                            Curl.absorb(Curl.state(), buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH),
                            buffer, j * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                }

            }
            System.arraycopy(
                    Curl.squeeze(Curl.absorb(Curl.state(), buffer, 0, buffer.length),
                            digests, i * Curl.HASH_LENGTH, Curl.HASH_LENGTH),
                    0, digests, 0, digests.length);
        }
        return digests;
    }
    public static Tuple[] publicKeys(final Tuple[] digests, final int keySize) {
        if (digests.length == 0 || digests.length % Curl.HASH_LENGTH != 0) {
            throw new RuntimeException("Invalid digests length: " + digests.length);
        }

        return Curl.squeeze(
                Curl.absorb(Curl.state(), digests, 0, digests.length),
                new Tuple[keySize], 0, keySize);
    }

    /*
        grabs every other bit and shifts it
     */

    private static int compressValueRight(int val, int index) {
        return (val & INCREMENT_MASK[index]) | ((val & ~INCREMENT_MASK[index])>>1);
    }

    private static Tuple compressTupleRight(Tuple tuple, int size, int index) {
        int i;
        for(i = 0; i < size; i++) {
            tuple.low = compressValueRight(tuple.low, i);
            tuple.hi = compressValueRight(tuple.hi, i);
        }
        tuple.low <<= index*size;
        tuple.hi <<= index*size;
        return tuple;
    }

    private static int doubleShiftMask(int val, int index) {
        return (val & (MERKLE_SHIFT_MASK << index)) >> index;
    }

    private static Tuple[] doubleShift(Tuple[] tuples, int index) {
        return Arrays.stream(tuples).map(t ->
                new Tuple(doubleShiftMask(t.low, index), doubleShiftMask(t.hi, index))
        ).toArray(Tuple[]::new);
    }

    private static Tuple[] compressThirds(Tuple[] tuple, int index) {
        return Arrays.stream(tuple).map(t -> compressTupleRight(t, NUMBER_OF_COMBINATIONS_PER_KEY, index)).toArray(Tuple[]::new);
    }

    private static void combineArrays(Tuple[] first, Tuple[] second) {
        for(int i = 0; i < first.length; i++ ) {
            first[i].low |= second[i].low;
            first[i].hi |= second[i].hi;
        }
    }
    private static Tuple combineTuples(Tuple first, Tuple second) {
        return new Tuple(first.low | second.low, first.hi | second.hi);
    }

    private static TupleArray combineArrays(TupleArray[] keys) {
        int i;
        if(keys[1] != null) {
            for(i = 0; i < keys[0].keys.length; i++) {
                keys[0].keys[i] = combineTuples(
                        compressTupleRight(keys[0].keys[i], NUMBER_OF_COMBINATIONS_PER_KEY, 0),
                        compressTupleRight(keys[1].keys[i], NUMBER_OF_COMBINATIONS_PER_KEY, 1));
            }
        }
        return keys[0];
    }

    private static int unshiftValue(int source, int dest, int index) {
        return (dest << 1) | ((source & (1 << index)) >> index);
    }

    private static void unshiftTupleArray(TupleArray source, TupleArray destination, int index) {
        assert source.keys.length == destination.keys.length;
        int i;
        for(i = 0; i < destination.keys.length; i++) {
            destination.keys[i] = new Tuple(
                    unshiftValue(source.keys[i].low ,destination.keys[i].low, index),
                    unshiftValue(source.keys[i].hi ,destination.keys[i].hi, index)
            );
        }
    }

    private static TupleKeyArray getNextLevel(TupleKeyArray level, int count, int size) {
        int numberOfTupleArrays = count / NUMBER_OF_KEYS_PER_TUPLE + (count % NUMBER_OF_KEYS_PER_TUPLE == 0? 0:1);
        int j, k, start, end;
        TupleKeyArray tupleKeyArray = TupleKeyArray.create(new TupleArray[numberOfTupleArrays]);
        TupleArray[] midLevel = new TupleArray[2];

        for(j = 0; j < numberOfTupleArrays; j++) {
            start = j*2;
            end = (start > level.keys.length- 2 ? level.keys.length: start + 2);
            k = start;
            while(k < end) {
                midLevel[k-start] = TupleArray.create(ArrayUtils.addAll(doubleShift(level.keys[k].keys, 0), doubleShift(level.keys[k].keys, 1)));
                k++;
                if(k < level.keys.length) {
                    unshiftTupleArray(level.keys[k - 1], level.keys[k], NUMBER_OF_KEYS_PER_TUPLE-1);
                }
            }
            Tuple[] keys = combineArrays(midLevel).keys;
            tupleKeyArray.keys[j] = TupleArray.create(Curl.squeeze(
                    Curl.absorb(Curl.state(), keys, 0, keys.length),
                    new Tuple[size], 0, size));
        }
        return tupleKeyArray;
    }

    public static MerkleNode[] nodesFromTuples(TupleKeyArray keys, final int keyCount) {
        MerkleNode[] individualKeys = new MerkleNode[keyCount];
        int i = 0, j = 0, k = 0;
        while(k < keyCount) {
            individualKeys[k++] = MerkleNode.create(Converter.trits(keys.keys[i].keys, j));
            j++;
            if(j == NUMBER_OF_KEYS_PER_TUPLE + 1) {
                j = 0;
                i++;
            }
        }
        return individualKeys;
    }

    public static TupleKeyArray[] createNKeys(int[] seed, int startingIndex, int numberOfKeysToCreate, final int securityLevel) {
        int i, numberOfTupleArrays;
        numberOfTupleArrays = numberOfKeysToCreate / NUMBER_OF_KEYS_PER_TUPLE + (numberOfKeysToCreate % NUMBER_OF_KEYS_PER_TUPLE == 0? 0:1);
        TupleKeyArray privateKeys = TupleKeyArray.create(numberOfTupleArrays);
        TupleKeyArray publicKeys = TupleKeyArray.create(numberOfTupleArrays);
        for(i = 0; i < numberOfTupleArrays; i ++) {
            privateKeys.keys[i] = TupleArray.create(privateKeys(subseeds(seed,i*NUMBER_OF_KEYS_PER_TUPLE + startingIndex),securityLevel*NUMBER_OF_FRAGMENT_CHUNKS*NUMBER_OF_SECURITY_LEVELS));
            publicKeys.keys[i] = TupleArray.create(publicKeys(digests(privateKeys.keys[i].keys), securityLevel * NUMBER_OF_SECURITY_LEVELS));
        }
        return new TupleKeyArray[]{privateKeys, publicKeys};
    }

    private static MerkleNode[] getPublicKeyNodes(TupleKeyArray[] privPubkeys, int numberOfKeysToCreate) {
        int i;
        MerkleNode[] merkleKeys = nodesFromTuples(privPubkeys[0], numberOfKeysToCreate);
        MerkleNode[] merklePubKeys = nodesFromTuples(privPubkeys[1], numberOfKeysToCreate);
        for(i = 0; i < merklePubKeys.length; i++) {
            merklePubKeys[i].children = new MerkleNode[]{merkleKeys[i]};
        }
        return merklePubKeys;
    }

    public static MerkleNode merkleTree(int[] seed, int startingIndex, int numberOfKeysToCreate, final int securityLevel) {
        if(securityLevel % 81 != 0)
            throw new RuntimeException("Invalid key Size: " + securityLevel);
        int hashSize, i, j, k, start, sz, numberOfLevels;
        hashSize = numberOfKeysToCreate;
        numberOfLevels = 32 - Integer.numberOfLeadingZeros(numberOfKeysToCreate);// + 1;//(int) Math.ceil(Math.log(numberOfKeysToCreate)/Math.log(2)) + 1;
        TupleKeyArray[] keys = createNKeys(seed, startingIndex, numberOfKeysToCreate, securityLevel);
        TupleKeyArray currentLevel = keys[1];
        MerkleNode[] currentLevelKeys, prevLevelKeys;
        prevLevelKeys = getPublicKeyNodes(keys, numberOfKeysToCreate);

        for(i = 1; i < numberOfLevels; i++) {
            hashSize = hashSize / 2 + (hashSize % 2 == 0 ? 0 : 1);
            currentLevel = getNextLevel(currentLevel, hashSize, securityLevel*NUMBER_OF_SECURITY_LEVELS);
            currentLevelKeys = nodesFromTuples(currentLevel, hashSize);
            for(j = 0; j < currentLevelKeys.length; j++) {
                start = j*2;
                sz = start+2 >= prevLevelKeys.length? prevLevelKeys.length-start:2;
                currentLevelKeys[j].children = new MerkleNode[sz];
                for(k = 0; k < sz; k++) {
                    if(j*2+k >= prevLevelKeys.length) break;
                    currentLevelKeys[j].children[k] = prevLevelKeys[j*2 + k];
                }
            }
            prevLevelKeys = currentLevelKeys;
        }
        return prevLevelKeys[0];
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
