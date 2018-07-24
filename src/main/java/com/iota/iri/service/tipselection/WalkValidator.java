package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;

/**
 * Validates consistency of tails.
 */
@FunctionalInterface
public interface WalkValidator {

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
