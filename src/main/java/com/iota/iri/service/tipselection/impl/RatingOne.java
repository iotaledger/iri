package com.iota.iri.service.tipselection.impl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;

/**
 * Implementation of <tt>RatingCalculator</tt> that gives a uniform rating of 1 to each transaction.
 * Used to create uniform random walks.
 */
public class RatingOne implements RatingCalculator {

    private final Tangle tangle;

    public RatingOne(Tangle tangle) {
        this.tangle = tangle;
    }

    @Override
    public Map<Hash, Integer> calculate(Hash entryPoint) throws Exception {
        Map<Hash, Integer> rating = new HashMap<>();

        Queue<Hash> queue = new LinkedList<>();
        queue.add(entryPoint);
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
