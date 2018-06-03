package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.storage.Tangle;

import java.util.Optional;
import java.util.Set;

/**
 * Implementation of <tt>TailFinder</tt> that given a transaction hash finds the tail of the associated bundle.
 *
 */
public class TailFinderImpl implements TailFinder {

    private final Tangle tangle;

    public TailFinderImpl(Tangle tangle) {
        this.tangle = tangle;
    }

    @Override
    public Optional<Hash> findTail(Hash hash) throws Exception {
        TransactionViewModel tx = TransactionViewModel.fromHash(tangle, hash);
        final Hash bundleHash = tx.getBundleHash();
        long index = tx.getCurrentIndex();
        while (index-- > 0 && bundleHash.equals(tx.getBundleHash())) {
            Set<Hash> approvees = tx.getApprovers(tangle).getHashes();
            boolean foundApprovee = false;
            for (Hash approvee : approvees) {
                TransactionViewModel nextTx = TransactionViewModel.fromHash(tangle, approvee);
                if (nextTx.getCurrentIndex() == index && bundleHash.equals(nextTx.getBundleHash())) {
                    tx = nextTx;
                    foundApprovee = true;
                    break;
                }
            }
            if (!foundApprovee) {
                break;
            }
        }
        if (tx.getCurrentIndex() == 0) {
            return Optional.of(tx.getHash());
        }
        return Optional.empty();
    }
}
