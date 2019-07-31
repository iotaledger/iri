package com.iota.iri.service.tipselection;

import java.util.Map;

import com.iota.iri.model.Hash;

/**
 * Calculates the rating for a sub graph
 */
@FunctionalInterface
public interface RatingCalculator {

    /**
     * Rating calculator
     * <p>
     * Calculates the rating of all the transactions that reference
     * a given {@code entryPoint}.
     * </p>
     *
     * @param entryPoint  Transaction hash of a selected entry point.
     * @return Map of ratings for each transaction that references entryPoint.
     * @throws Exception If DB fails to retrieve transactions
     */

    Map<Hash, Integer> calculate(Hash entryPoint) throws Exception;
}
