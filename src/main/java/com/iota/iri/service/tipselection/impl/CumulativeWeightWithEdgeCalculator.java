package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.HashPrefix;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Used to create a weighted random walks.
 */
public class CumulativeWeightWithEdgeCalculator extends CumulativeWeightCalculator {

    private static final Logger log = LoggerFactory.getLogger(CumulativeWeightWithEdgeCalculator.class);

    public CumulativeWeightWithEdgeCalculator(Tangle tangle) {
        super(tangle);
    }

    @Override
    public UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception {
        return super.calculate(entryPoint);
    }
}

