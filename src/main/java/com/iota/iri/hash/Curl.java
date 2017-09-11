package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;

import java.util.Arrays;

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
    private static final int[] TRUTH_TABLE = {1, 0, -1, 2, 1, -1, 0, 2, -1, 1, 0};
    /*
    private static final IntPair[] TRANSFORM_INDICES = IntStream.range(0, STATE_LENGTH)
            .mapToObj(i -> new IntPair(i == 0 ? 0 : (((i - 1) % 2) + 1) * HALF_LENGTH - ((i - 1) >> 1),
                    ((i % 2) + 1) * HALF_LENGTH - ((i) >> 1)))
            .toArray(IntPair[]::new);
            */

    private final int[] state;
    private final long[] stateLow;
    private final long[] stateHigh;

    private final int[] scratchpad = new int[STATE_LENGTH];


    protected Curl() {
        state = new int[STATE_LENGTH];
        stateHigh = null;
        stateLow = null;
    }

    public Curl(boolean pair) {
        if(pair) {
            stateHigh = new long[STATE_LENGTH];
            stateLow = new long[STATE_LENGTH];
            state = null;
            set();
        } else {
            state = new int[STATE_LENGTH];
            stateHigh = null;
            stateLow = null;
        }
    }

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

        //final int[] scratchpad = new int[STATE_LENGTH];
        int scratchpadIndex = 0;
        int prev_scratchpadIndex = 0;
        for (int round = 0; round < NUMBER_OF_ROUNDS; round++) {
            System.arraycopy(state, 0, scratchpad, 0, STATE_LENGTH);
            for (int stateIndex = 0; stateIndex < STATE_LENGTH; stateIndex++) {
                prev_scratchpadIndex = scratchpadIndex;
                if (scratchpadIndex < 365) {
                    scratchpadIndex += 364;
                } else {
                    scratchpadIndex += -365;
                }
                state[stateIndex] = TRUTH_TABLE[scratchpad[prev_scratchpadIndex] + (scratchpad[scratchpadIndex] << 2) + 5];
            }
        }
    }
    public void reset() {
        Arrays.fill(state, 0);
    }
    public void reset(boolean pair) {
        if(pair) {
            set();
        } else {
            reset();
        }
    }
    private void set() {
        Arrays.fill(stateLow, Converter.HIGH_LONG_BITS);
        Arrays.fill(stateHigh, Converter.HIGH_LONG_BITS);
    }

    private void pairTransform() {
        final long[] curlScratchpadLow = new long[STATE_LENGTH];
        final long[] curlScratchpadHigh = new long[STATE_LENGTH];
        int curlScratchpadIndex = 0;
        for (int round = 27; round-- > 0; ) {
            System.arraycopy(stateLow, 0, curlScratchpadLow, 0, STATE_LENGTH);
            System.arraycopy(stateHigh, 0, curlScratchpadHigh, 0, STATE_LENGTH);
            for (int curlStateIndex = 0; curlStateIndex < STATE_LENGTH; curlStateIndex++) {
                final long alpha = curlScratchpadLow[curlScratchpadIndex];
                final long beta = curlScratchpadHigh[curlScratchpadIndex];
                final long gamma = curlScratchpadHigh[curlScratchpadIndex += (curlScratchpadIndex < 365 ? 364 : -365)];
                final long delta = (alpha | (~gamma)) & (curlScratchpadLow[curlScratchpadIndex] ^ beta);
                stateLow[curlStateIndex] = ~delta;
                stateHigh[curlStateIndex] = (alpha ^ gamma) | delta;
            }
        }
    }

    public void absorb(final Pair<long[], long[]> pair, int offset, int length) {
        int o = offset, l = length, i = 0;
        do {
            System.arraycopy(pair.low, o, stateLow, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
            System.arraycopy(pair.hi, o, stateHigh, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
            pairTransform();
            o += HASH_LENGTH;
        } while ((l -= HASH_LENGTH) > 0);
    }

    public Pair<long[], long[]> squeeze(Pair<long[], long[]> pair, int offset, int length) {
        int o = offset, l = length, i = 0;
        long[] low = pair.low;
        long[] hi = pair.hi;
        do {
            System.arraycopy(stateLow, 0, low, o, l < HASH_LENGTH ? l : HASH_LENGTH);
            System.arraycopy(stateHigh, 0, hi, o, l < HASH_LENGTH ? l : HASH_LENGTH);
            pairTransform();
            o += HASH_LENGTH;
        } while ((l -= HASH_LENGTH) > 0);
        return new Pair<>(low, hi);
    }

}
