package com.iota.iri.service.tipselection;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.collections.impl.BoundedHashSet;
import com.iota.iri.utils.collections.interfaces.BoundedSet;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Validates consistency of tails.
 */
@FunctionalInterface
public interface WalkValidator {

    int MAX_CACHE_SIZE = 2_000_000;
    //As long as tip selection is synchronized we are fine with the collection not being thread safe
    BoundedSet<Hash> FAILED_BELOW_MAX_DEPTH_CACHE = new BoundedHashSet<>(20_000, MAX_CACHE_SIZE);

    /**
     * Validation
     * <p>
     * Checks if a given transaction is a valid tail.
     * </p>
     *
     * @param transactionHash  Transaction hash to validate consistency of.
     * @return  True iff tail is valid.
     * @throws Exception If Validation fails to execute
     */
    boolean isValid(Hash transactionHash) throws Exception;

}
