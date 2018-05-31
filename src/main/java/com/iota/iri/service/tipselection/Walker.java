package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;

/**
 * Walks the tangle from an entry point towards tips
 *
 */

public interface Walker {

    /**
     * Walk algorithm
     * <p>
     * Starts from given entry point to select valid transactions to be used
     * as tips. It will output a valid transaction as a tip.
     * </p>
     *
     * @param entryPoint  Transaction hash to start walk from.
     * @param ratings  Map of ratings for each transaction that references entryPoint.
     * @param walkValidator Used to validate consistency of tails.
     * @return  Transaction hash of tip.
     * @throws Exception If DB fails to retrieve transactions
     */
    Hash walk(Hash entryPoint, UnIterableMap<HashId, Integer> ratings, WalkValidator walkValidator) throws Exception;

}
