package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.RatingCalculator;

import java.util.Map;

public class RatingCumulativeWeight implements RatingCalculator {

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
    Map<Hash, Long> calculate(Hash entryPoint){

    }


}
