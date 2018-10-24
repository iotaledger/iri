package com.iota.iri.service.tipselection;

import java.util.Optional;

import com.iota.iri.model.Hash;

/**
 * Finds the tail of a bundle
 */

@FunctionalInterface
public interface TailFinder {
    /**
     *Method for finding tails of bundles
     *
     * <p>
     *  This method is used to find a tail (current_index=0) of a bundle,
     *  given any transaction hash in the bundle.
     * </p>
     *
     * @param hash The transaction hash of any transaction in the bundle.
     * @return  Hash of the tail transaction.
     * @throws Exception If DB fails to retrieve transactions
     */
    Optional<Hash> findTail(Hash hash) throws Exception;

}
