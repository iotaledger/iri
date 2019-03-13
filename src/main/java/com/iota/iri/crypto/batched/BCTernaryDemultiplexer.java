package com.iota.iri.crypto.batched;

/**
 * Demultiplexes long values into byte arrays.
 */
public class BCTernaryDemultiplexer {

    private BCTrinary bcTrinary;

    /**
     * Creates a new {@link BCTernaryDemultiplexer} with the given
     * binary-encoded-ternary data to demultiplex.
     * @param bcTrinary the binary-encoded-trinary objet to demultiplex
     */
    public BCTernaryDemultiplexer(BCTrinary bcTrinary) {
        this.bcTrinary = bcTrinary;
    }

    /**
     * Constructs the demultiplexed version of a given column index.
     * @param index the column index to demultiplex
     * @return the byte array at the given column index
     */
    public byte[] get(int index) {
        int length = bcTrinary.low.length;
        byte[] result = new byte[length];

        for (int i = 0; i < length; i++) {
            long low = (bcTrinary.low[i] >> index) & 1;
            long high = (bcTrinary.high[i] >> index) & 1;

            if (low == 1 && high == 0) {
                result[i] = -1;
                continue;
            }

            if (low == 0 && high == 1) {
                result[i] = 1;
                continue;
            }

            result[i] = 0;
        }
        return result;
    }

}