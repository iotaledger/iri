package com.iota.iri.crypto.batched;

import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.SpongeFactory;

/**
 * Creates {@link BatchedHasher} objects based on the required type.
 */
public class BatchedHasherFactory {

    /**
     * The specific implementations of a {@link BatchedHasher}.
     */
    public enum Type {
        BCTCURL81,
        BCTCURL27,
        FakeBatchedCURL81,
        FakeBatchedCURL27,
    }

    /**
     * Creates a new {@link BatchedHasher} instances with a default
     * batch timeout of {@link BatchedHasher#DEFAULT_BATCH_TIMEOUT_MILLISECONDS}.
     *
     * @param type the specific implementation of the {@link BatchedHasher}
     * @return the BatchedHasher instance
     */
    public static BatchedHasher create(Type type) {
        return create(type, BatchedHasher.DEFAULT_BATCH_TIMEOUT_MILLISECONDS);
    }

    /**
     * Creates a new {@link BatchedHasher} instance.
     *
     * @param type the specific implementation of the {@link BatchedHasher}
     * @return the BatchedHasher instance
     */
    public static BatchedHasher create(Type type, int batchTimeoutMilliSecs) {
        switch (type) {
            case BCTCURL81:
                return new BatchedBCTCurl(Curl.HASH_LENGTH, 81, batchTimeoutMilliSecs);
            case BCTCURL27:
                return new BatchedBCTCurl(Curl.HASH_LENGTH, 27, batchTimeoutMilliSecs);
            case FakeBatchedCURL81:
                return new FakeBatchedCurl(Curl.HASH_LENGTH, SpongeFactory.Mode.CURLP81);
            case FakeBatchedCURL27:
                return new FakeBatchedCurl(Curl.HASH_LENGTH, SpongeFactory.Mode.CURLP27);
            default:
                return null;
        }
    }

}
