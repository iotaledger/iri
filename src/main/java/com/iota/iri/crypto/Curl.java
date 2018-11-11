package com.iota.iri.crypto;

import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * (c) 2016 Come-from-Beyond and Paul Handy
 *
 * Curl belongs to the sponge function family.
 *
 */
public class Curl implements Sponge {

    static final int NUMBER_OF_ROUNDSP81 = 81;
    static final int NUMBER_OF_ROUNDSP27 = 27;
    private final int numberOfRounds;
    private static final int STATE_LENGTH = 3 * HASH_LENGTH;

    private static final byte[] TRUTH_TABLE = {1, 0, -1, 2, 1, -1, 0, 2, -1, 1, 0};

    private final byte[] state;
    private final long[] stateLow;
    private final long[] stateHigh;

    private final byte[] scratchpad = new byte[STATE_LENGTH];


    protected Curl(SpongeFactory.Mode mode) {
        switch(mode) {
            case CURLP27: {
                numberOfRounds = NUMBER_OF_ROUNDSP27;
            } break;
            case CURLP81: {
                numberOfRounds = NUMBER_OF_ROUNDSP81;
            } break;
            default: throw new NoSuchElementException("Only Curl-P-27 and Curl-P-81 are supported.");
        }
        state = new byte[STATE_LENGTH];
        stateHigh = null;
        stateLow = null;
    }

    @Override
    public void absorb(final byte[] trits, int offset, int length) {

        do {
            System.arraycopy(trits, offset, state, 0, length < HASH_LENGTH ? length : HASH_LENGTH);
            transform();
            offset += HASH_LENGTH;
        } while ((length -= HASH_LENGTH) > 0);
    }

    @Override
    public void squeeze(final byte[] trits, int offset, int length) {

        do {
            System.arraycopy(state, 0, trits, offset, length < HASH_LENGTH ? length : HASH_LENGTH);
            transform();
            offset += HASH_LENGTH;
        } while ((length -= HASH_LENGTH) > 0);
    }

    /**
     * Performs {@code numberOfRounds} Transformations on the internal state.
     */
    private void transform() {

        int scratchpadIndex = 0;
        int prevScratchpadIndex = 0;
        for (int round = 0; round < numberOfRounds; round++) {
            System.arraycopy(state, 0, scratchpad, 0, STATE_LENGTH);
            for (int stateIndex = 0; stateIndex < STATE_LENGTH; stateIndex++) {
                prevScratchpadIndex = scratchpadIndex;
                if (scratchpadIndex < 365) {
                    scratchpadIndex += 364;
                } else {
                    scratchpadIndex += -365;
                }
                state[stateIndex] = TRUTH_TABLE[scratchpad[prevScratchpadIndex] + (scratchpad[scratchpadIndex] << 2) + 5];
            }
        }
    }
    public void reset() {
        Arrays.fill(state, (byte) 0);
    }


    // BCURLT - pair Curl implementation.
    ///////////////////////////////////////Code not in use////////////////////////////////////////////////////

    protected Curl(boolean pair, SpongeFactory.Mode mode) {
        switch(mode) {
            case CURLP27: {
                numberOfRounds = NUMBER_OF_ROUNDSP27;
            } break;
            case CURLP81: {
                numberOfRounds = NUMBER_OF_ROUNDSP81;
            } break;
            default: throw new NoSuchElementException("Only Curl-P-27 and Curl-P-81 are supported.");
        }
        if(pair) {
            stateHigh = new long[STATE_LENGTH];
            stateLow = new long[STATE_LENGTH];
            state = null;
            set();
        } else {
            state = new byte[STATE_LENGTH];
            stateHigh = null;
            stateLow = null;
        }
    }

    void reset(boolean pair) {
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
        for (int round = numberOfRounds; round-- > 0; ) {
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

    void absorb(final Pair<long[], long[]> pair, int offset, int length) {
        int o = offset, l = length, i = 0;
        do {
            System.arraycopy(pair.low, o, stateLow, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
            System.arraycopy(pair.hi, o, stateHigh, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
            pairTransform();
            o += HASH_LENGTH;
        } while ((l -= HASH_LENGTH) > 0);
    }

    Pair<long[], long[]> squeeze(Pair<long[], long[]> pair, int offset, int length) {
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
