package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.HashPrefix;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.utils.collections.impl.TransformingBoundedHashSet;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.impl.TransformingMap;
import com.iota.iri.utils.collections.interfaces.BoundedSet;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementation of <tt>RatingCalculator</tt> that gives the cumulative for each transaction referencing entryPoint.
 * Used to create a weighted random walks.
 *
 * 
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

