package com.iota.iri.hash;

import com.iota.iri.hash.keys.Tuple;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * (c) 2016 Come-from-Beyond and Paul Handy
 *
 * Curl belongs to the sponge function family.
 *
 */
public class Curl {

    public static final int HASH_LENGTH = 243;
    private static final int STATE_LENGTH = 3 * HASH_LENGTH;
    private static final int HALF_LENGTH = 364;

    private static final int NUMBER_OF_ROUNDS = 27;
    private static final int[] TRUTH_TABLE = {1, 0, -1, 1, -1, 0, -1, 1, 0};
    private static final Tuple[] TRANSFORM_INDICES = IntStream.range(0, STATE_LENGTH).mapToObj(i -> {
        Tuple t = new Tuple();
        t.low = i == 0 ? 0 : (((i - 1) % 2) + 1) * HALF_LENGTH - ((i - 1) >> 1);
        t.hi = ((i % 2) + 1) * HALF_LENGTH - ((i) >> 1);
        return t;
    }).toArray(Tuple[]::new);

    private final int[] state = new int[STATE_LENGTH];

    public void absorb(final int[] trits, int offset, int length) {

        do {
            System.arraycopy(trits, offset, state, 0, length < HASH_LENGTH ? length : HASH_LENGTH);
            transform();
            offset += HASH_LENGTH;
        } while ((length -= HASH_LENGTH) > 0);
    }


    public void squeeze(final int[] trits, int offset, int length) {

        do {
            System.arraycopy(state, 0, trits, offset, length < HASH_LENGTH ? length : HASH_LENGTH);
            transform();
            offset += HASH_LENGTH;
        } while ((length -= HASH_LENGTH) > 0);
    }

    private void transform() {

        final int[] scratchpad = new int[STATE_LENGTH];
        int scratchpadIndex = 0;
        for (int round = 0; round < NUMBER_OF_ROUNDS; round++) {
            System.arraycopy(state, 0, scratchpad, 0, STATE_LENGTH);
            for (int stateIndex = 0; stateIndex < STATE_LENGTH; stateIndex++) {
                state[stateIndex] = TRUTH_TABLE[scratchpad[scratchpadIndex] + scratchpad[scratchpadIndex += (scratchpadIndex < 365 ? 364 : -365)] * 3 + 4];
            }
        }
    }

    public void reset() {
        for (int stateIndex = 0; stateIndex < STATE_LENGTH; stateIndex++) {
            state[stateIndex] = 0;
        }
    }

    private static Tuple[] transform(final Tuple[] state) {
        final Tuple[] scratchpad = new Tuple[STATE_LENGTH];
        for (int round = 0; round < NUMBER_OF_ROUNDS; round++) {
            System.arraycopy(state, 0, scratchpad, 0, STATE_LENGTH);
            for(int i = 0; i < STATE_LENGTH; i++) {
                Tuple tuple = new Tuple();
                final int alpha = scratchpad[TRANSFORM_INDICES[i].low].low;
                final int beta = scratchpad[TRANSFORM_INDICES[i].low].hi;
                final int gamma = scratchpad[TRANSFORM_INDICES[i].hi].hi;
                final int delta = (alpha | (~gamma)) & (scratchpad[TRANSFORM_INDICES[i].hi].low ^ beta);
                tuple.low = ~delta;
                tuple.hi = (alpha ^ gamma) | delta;
                state[i] = tuple;
            }
        }
        return state;
    }

    public static Tuple[] state() {
        Tuple[] state = new Tuple[Curl.STATE_LENGTH];
        Arrays.fill(state, new Tuple());
        return state;
    }

    public static Function<Tuple[], Function<Integer, Function<Integer, Function<Boolean, Function<Tuple[], Tuple[]>>>>> sponge = dest -> offset -> length -> absorb -> src ->  {
        int o = offset, l = length, i = 0;
        do {
            System.arraycopy(src, o, dest, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
            transform(absorb? dest: src);
            o += HASH_LENGTH;
        } while ((l -= HASH_LENGTH) > 0);
        return dest;
    };
    public static Function<Tuple[], Tuple[]> absorb(final Tuple[] trits, int offset, int length) {
        return (state) -> Curl.absorb(state, trits, offset, length);
    }
    public static Tuple[] absorb(final Tuple[] state, final Tuple[] trits, int offset, int length) {
        int o = offset, l = length, i = 0;
        do {
            System.arraycopy(trits, o, state, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
            transform(state);
            o += HASH_LENGTH;
        } while ((l -= HASH_LENGTH) > 0);
        return state;
    }

    public static Function<Tuple[], Tuple[]> squeeze(final Tuple[] trits, int offset, int length) {
        return (state) -> Curl.squeeze(state, trits, offset, length);
    }
    public static Tuple[] squeeze(final Tuple[] state, final Tuple[] trits, int offset, int length) {
        int o = offset,
                l = length;
        do {
            System.arraycopy(state, 0, trits, o, l < HASH_LENGTH ? l : HASH_LENGTH);
            transform(state);
            o += HASH_LENGTH;
        } while ((l -= HASH_LENGTH) > 0);
        return trits;
    }
}
