package com.iota.iri.crypto.batched;

/**
 * A Curl implementation which absorbs binary-encoded-ternary inputs.
 */
public class BCTCurl {

    private static final long HIGH_LONG_BITS = 0xFFFF_FFFF_FFFF_FFFFL;

    private int hashLength;
    private int numberOfRounds;
    private int stateLength;
    private BCTrinary state;

    /**
     * Creates a new {@link BCTCurl} with the given hash length and number of rounds.
     * @param hashLength the desired hash length
     * @param numberOfRounds the number of hashing rounds to apply
     */
    public BCTCurl(int hashLength, int numberOfRounds) {
        this.hashLength = hashLength;
        this.numberOfRounds = numberOfRounds;
        this.stateLength = 3 * hashLength;
        this.state = new BCTrinary(new long[3 * hashLength], new long[3 * hashLength]);
        reset();
    }

    /**
     * Resets the state of the hashing function.
     */
    public void reset() {
        for (int i = 0; i < stateLength; i++) {
            state.low[i] = HIGH_LONG_BITS;
            state.high[i] = HIGH_LONG_BITS;
        }
    }


    /**
     * Transforms the state of the hashing function.
     */
    public void transform() {
        long[] scratchPadLow = new long[stateLength];
        long[] scratchPadHigh = new long[stateLength];
        int scratchPadIndex = 0;

        for (int round = numberOfRounds; round > 0; round--) {
            System.arraycopy(state.low, 0, scratchPadLow, 0, state.low.length);
            System.arraycopy(state.high, 0, scratchPadHigh, 0, state.high.length);
            for (int stateIndex = 0; stateIndex < stateLength; stateIndex++) {
                long alpha = scratchPadLow[scratchPadIndex];
                long beta = scratchPadHigh[scratchPadIndex];

                if (scratchPadIndex < 365) {
                    scratchPadIndex += 364;
                } else {
                    scratchPadIndex -= 365;
                }

                long delta = beta ^ scratchPadLow[scratchPadIndex];
                state.low[stateIndex] = ~(delta & alpha);
                state.high[stateIndex] = (alpha ^ scratchPadHigh[scratchPadIndex]) | delta;
            }
        }
    }

    /**
     * Absorbs the given binary-encoded-ternary trits data.
     * @param bcTrits the binary-encoded-ternary trits to absorb
     */
    public void absorb(BCTrinary bcTrits) {
        int length = bcTrits.low.length;
        int offset = 0;

        for (; ; ) {
            int lengthToCopy;
            if (length < hashLength) {
                lengthToCopy = length;
            } else {
                lengthToCopy = hashLength;
            }

            System.arraycopy(bcTrits.low, offset, state.low, 0, lengthToCopy);
            System.arraycopy(bcTrits.high, offset, state.high, 0, lengthToCopy);
            transform();
            offset += lengthToCopy;
            length -= lengthToCopy;

            if (length <= 0) {
                break;
            }
        }
    }

    /**
     * Squeezes the desired length from the state of the hashing function.
     * @param tritCount the desired length of the result
     * @return the binary-encoded-trinary data
     */
    public BCTrinary squeeze(int tritCount) {
        BCTrinary result = new BCTrinary(new long[tritCount], new long[tritCount]);

        int hashCount = tritCount / hashLength;

        for (int i = 0; i < hashCount; i++) {
            System.arraycopy(state.low, 0, result.low, i * hashLength, hashLength);
            System.arraycopy(state.high, 0, result.high, i * hashLength, hashLength);
            transform();
        }

        int last = tritCount - hashCount * hashLength;

        System.arraycopy(state.low, 0, result.low, tritCount - last, last);
        System.arraycopy(state.high, 0, result.high, tritCount - last, last);
        if (tritCount % hashLength != 0) {
            transform();
        }
        return result;
    }

}
