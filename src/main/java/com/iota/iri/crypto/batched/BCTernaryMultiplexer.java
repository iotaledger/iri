package com.iota.iri.crypto.batched;

import java.util.List;

/**
 * Multiplexes input trits data to a {@link BCTrinary}.
 */
public class BCTernaryMultiplexer {

    private List<byte[]> inputs;

    /**
     * Creates a new {@link BCTernaryMultiplexer} which multiplexes
     * the given trits data.
     * @param inputs the input trits data to multiplex
     */
    public BCTernaryMultiplexer(List<byte[]> inputs) {
        this.inputs = inputs;
    }

    /**
     * Multiplexes the input data into a binary-encoded ternary format.
     *
     * @return the extracted data in binary-encoded-ternary format
     */
    public BCTrinary extract() {
        final int trinariesCount = inputs.size();
        final int tritsCount = inputs.get(0).length;

        BCTrinary result = new BCTrinary(new long[tritsCount], new long[tritsCount]);
        for (int i = 0; i < tritsCount; i++) {
            BCTrit bcTrit = new BCTrit();

            for (int j = 0; j < trinariesCount; j++) {
                switch (inputs.get(j)[i]) {
                    case -1:
                        bcTrit.low |= 1L << j;
                        break;
                    case 1:
                        bcTrit.high |= 1L << j;
                        break;
                    case 0:
                        bcTrit.low |= 1L << j;
                        bcTrit.high |= 1L << j;
                        break;
                    default:
                        // do nothing
                }
            }

            result.low[i] = bcTrit.low;
            result.high[i] = bcTrit.high;
        }

        return result;
    }

}