package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;

import java.util.*;

public class RatingOne implements RatingCalculator {

    private final Tangle tangle;

    public RatingOne(Tangle tangle) {
        this.tangle = tangle;
    }

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
    public Map<Hash, Integer> calculate(Hash entryPoint) throws Exception {
        Map<Hash, Integer> rating = new HashMap<>();

        Queue<Hash> queue = new LinkedList<>(Collections.singleton(entryPoint));
        rating.put(entryPoint, 1);

        Hash hash;

        //traverse all transactions that reference entryPoint
        while ((hash = queue.poll()) != null) {
            Set<Hash> approvers = ApproveeViewModel.load(tangle, hash).getHashes();
            for (Hash tx : approvers) {
                if (!rating.containsKey(tx)) {
                    //add to rating w/ value "1"
                    rating.put(tx, 1);
                    queue.add(tx);
                }
            }
        }
        return rating;
    }


}
