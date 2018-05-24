package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;

/**
 * This interface is used to enforce usage of the calculate() method
 * which is in charge of calculating the cumulative rating of
 * transactions with connection to the entry point.
 */
@FunctionalInterface
public interface RatingCalculator {

    /**
     * Cumulative rating calculator
     * <p>
     * Calculates the cumulative rating of the transactions that reference
     * a given entry point.
     * </p>
     *
     * @param entryPoint  Transaction ID of selected milestone.
     * @return  Hash Map of cumulative ratings.
     */

    UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception;
}
