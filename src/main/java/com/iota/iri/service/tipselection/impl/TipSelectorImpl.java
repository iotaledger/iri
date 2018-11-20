package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.MilestoneTracker;
import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.tipselection.*;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;

import java.security.InvalidAlgorithmParameterException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of <tt>TipSelector</tt> that selects 2 tips,
 * based on cumulative weights and transition function alpha.
 *
 */
public class TipSelectorImpl implements TipSelector {

    public static final String REFERENCE_TRANSACTION_TOO_OLD = "reference transaction is too old";
    public static final String TIPS_NOT_CONSISTENT = "inconsistent tips pair selected";

    private final EntryPointSelector entryPointSelector;
    private final RatingCalculator ratingCalculator;
    private final Walker walker;

    private final LedgerService ledgerService;
    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final TipSelConfig config;

    public TipSelectorImpl(Tangle tangle,
                           SnapshotProvider snapshotProvider,
                           LedgerService ledgerService,
                           EntryPointSelector entryPointSelector,
                           RatingCalculator ratingCalculator,
                           Walker walkerAlpha,
                           TipSelConfig config) {

        this.entryPointSelector = entryPointSelector;
        this.ratingCalculator = ratingCalculator;

        this.walker = walkerAlpha;

        //used by walkValidator
        this.ledgerService = ledgerService;
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.config = config;
    }

    /**
     * Implementation of getTransactionsToApprove
     *
     * General process:
     * <ol>
     * <li><b>Preparation:</b> select <CODE>entryPoint</CODE> and calculate rating for all referencing transactions
     * <li><b>1st Random Walk:</b> starting from <CODE>entryPoint</CODE>.
     * <li><b>2nd Random Walk:</b> if <CODE>reference</CODE> exists and is in the rating calulationg, start from <CODE>reference</CODE>,
     *     otherwise start again from <CODE>entryPoint</CODE>.
     * <li><b>Validate:</b> check that both tips are not contradicting.
     * </ol>
     * @param depth  The depth that the transactions will be found from.
     * @param reference  An optional transaction hash to be referenced by tips.
     * @return  Transactions to approve
     * @throws Exception If DB fails to retrieve transactions
     */
    @Override
    public List<Hash> getTransactionsToApprove(int depth, Optional<Hash> reference) throws Exception {
        try {
            snapshotProvider.getLatestSnapshot().lockRead();

            //preparation
            Hash entryPoint = entryPointSelector.getEntryPoint(depth);
            UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(entryPoint);

            //random walk
            List<Hash> tips = new LinkedList<>();
            WalkValidator walkValidator = new WalkValidatorImpl(tangle, snapshotProvider, ledgerService, config);
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
            if (!ledgerService.tipsConsistent(tips)) {
                throw new IllegalStateException(TIPS_NOT_CONSISTENT);
            }

            return tips;
        } finally {
            snapshotProvider.getLatestSnapshot().unlockRead();
        }
    }

    private void checkReference(HashId reference, UnIterableMap<HashId, Integer> rating)
            throws InvalidAlgorithmParameterException {
        if (!rating.containsKey(reference)) {
            throw new InvalidAlgorithmParameterException(REFERENCE_TRANSACTION_TOO_OLD);
        }
    }
}
