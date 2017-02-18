package com.iota.iri.hash;

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
        int scratchpadIndex = 0;
        for (int round = 0; round < NUMBER_OF_ROUNDS; round++) {

            System.arraycopy(IntStream.range(0, STATE_LENGTH).parallel().mapToObj(i -> {
                        Tuple tuple = new Tuple();
                        final int t0, t1;
                        t0 = i == 0 ? 0 : (((i - 1) % 2) + 1) * HALF_LENGTH - ((i - 1) >> 1);
                        t1 = ((i % 2) + 1) * HALF_LENGTH - ((i) >> 1);

                        final int alpha = state[t0].low;
                        final int beta = state[t0].hi;
                        final int gamma = state[t1].hi;
                        final int delta = (alpha | (~gamma)) & (state[t1].low ^ beta);
                        tuple.low = ~delta;
                        tuple.hi = (alpha ^ gamma) | delta;
                        return tuple;
                    }).toArray(Tuple[]::new)
                    , 0, state, 0, STATE_LENGTH);
        }
        return state;
    }

    public static Tuple[] state() {
        return IntStream.range(0, Curl.STATE_LENGTH).parallel().mapToObj(i -> new Tuple()).toArray(Tuple[]::new);
    }
    public static Function<Tuple[], Tuple[]> absorb(final Tuple[] trits, int offset, int length) {
        return (state) -> {
            int o = offset, l = length, i = 0;
            do {
                System.arraycopy(trits, o, state, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
                state = transform(state);
                o += HASH_LENGTH;
            } while ((l -= HASH_LENGTH) > 0);
            return state;
        };
    }
    public static Function<Tuple[], Tuple[]> squeeze(final Tuple[] trits, int offset, int length) {
        return (state) -> {
            int o = offset,
                    l = length;
            do {
                System.arraycopy(state, 0, trits, o, l < HASH_LENGTH ? l : HASH_LENGTH);
                transform(state);
                o += HASH_LENGTH;
            } while ((l -= HASH_LENGTH) > 0);
            return trits;
        };
    }
}
