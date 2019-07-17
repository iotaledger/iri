package com.iota.iri.network;

import net.openhft.hashing.LongHashFunction;


public class TransactionCacheDigester {

    private static final LongHashFunction TX_CACHE_DIGEST_HASH_FUNC = LongHashFunction.xx();

    /**
     * Computes the digest of the given transaction data.
     *
     * @param txBytes The raw byte encoded transaction data
     * @return The the digest of the transaction data
     */
    public static long getTxCacheDigest(byte[] txBytes) {
        return TX_CACHE_DIGEST_HASH_FUNC.hashBytes(txBytes);
    }

}
