package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.TransactionValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.service.tipselection.*;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import com.iota.iri.zmq.MessageQ;

import java.security.SecureRandom;
import java.util.*;

public class TipSelectorImpl implements TipSelector {

    public static final String REFERENCE_TRANSACTION_TOO_OLD = "reference transaction is too old";
    public static final String TIPS_NOT_CONSISTENT = "inconsistent tips pair selected";

    private final EntryPointSelector entryPointSelector;
    private final RatingCalculator ratingCalculator;
    private final Walker walker;

    private final int maxDepth;
    private final LedgerValidator ledgerValidator;
    private final TransactionValidator transactionValidator;
    private final Tangle tangle;
    private final Milestone milestone;

    @Override
    public int getMaxDepth() {
        return maxDepth;
    }

    public TipSelectorImpl(Tangle tangle,
                           LedgerValidator ledgerValidator,
                           TransactionValidator transactionValidator,
                           Milestone milestone,
                           int maxDepth,
                           MessageQ messageQ,
                           boolean testnet,
                           int milestoneStartIndex,
                           double alpha) {

        this.entryPointSelector = new EntryPointSelectorImpl(tangle, milestone, testnet, milestoneStartIndex);
        this.ratingCalculator = new CumulativeWeightCalculator(tangle);

        this.walker = new WalkerAlpha(alpha, new SecureRandom(), tangle, messageQ, new TailFinderImpl(tangle));

        //used by walkValidator
        this.maxDepth = maxDepth;
        this.ledgerValidator = ledgerValidator;
        this.transactionValidator = transactionValidator;
        this.tangle = tangle;
        this.milestone = milestone;
    }

    @Override
    public List<Hash> getTransactionsToApprove(Optional<Hash> reference, int depth) throws Exception {
        try {
            milestone.latestSnapshot.rwlock.readLock().lock();

            //preparation
            Hash entryPoint = entryPointSelector.getEntryPoint(depth);
            UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(entryPoint);

            //random walk
            List<Hash> tips = new LinkedList<>();
            WalkValidator walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, transactionValidator, milestone,
                    maxDepth);
            Hash tip = walker.walk(entryPoint, rating, walkValidator);
            tips.add(tip);

            if (reference.isPresent()) {
                checkReference(reference.get(), rating);
                entryPoint = reference.get();
            }

            //passing the same walkValidator means that the walks will be consistent with each other
            tip = walker.walk(entryPoint, rating, walkValidator);
            tips.add(tip);

            //validate
            if (!ledgerValidator.checkConsistency(tips)) {
                throw new RuntimeException(TIPS_NOT_CONSISTENT);
            }

            return tips;
        } finally {
            milestone.latestSnapshot.rwlock.readLock().unlock();
        }
    }

    private void checkReference(HashId reference, UnIterableMap<HashId, Integer> rating) {
        if (!rating.containsKey(reference)) {
            throw new RuntimeException(REFERENCE_TRANSACTION_TOO_OLD);
        }
    }


}
