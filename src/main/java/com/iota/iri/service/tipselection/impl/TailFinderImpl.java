package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.storage.Tangle;

import java.util.Optional;
import java.util.Set;

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
        boolean foundApprovee = false;
        while (index-- > 0 && tx.getBundleHash().equals(bundleHash)) {
            Set<Hash> approvees = tx.getApprovers(tangle).getHashes();
            for (Hash approvee : approvees) {
                TransactionViewModel nextTx = TransactionViewModel.fromHash(tangle, approvee);
                if (nextTx.getBundleHash().equals(bundleHash)) {
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
