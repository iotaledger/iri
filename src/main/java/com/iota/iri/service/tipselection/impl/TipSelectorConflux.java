package com.iota.iri.service.tipselection.impl;

import com.iota.iri.validator.LedgerValidator;
import com.iota.iri.validator.MilestoneTracker;
import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.service.tipselection.*;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;

import java.security.InvalidAlgorithmParameterException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of <tt>TipSelector</tt> that selects 2 tips,
 * based on Conflux total order and MCMC
 *
 */
public class TipSelectorConflux implements TipSelector {

    public static final String REFERENCE_TRANSACTION_TOO_OLD = "reference transaction is too old";
    public static final String TIPS_NOT_CONSISTENT = "inconsistent tips pair selected";

    private final EntryPointSelector entryPointSelector;
    private final RatingCalculator ratingCalculator;
    private final Walker walker;

    private final LedgerValidator ledgerValidator;
    private final Tangle tangle;
    private final MilestoneTracker milestoneTracker;
    private final TipSelConfig config;

    public TipSelectorConflux(Tangle tangle,
                           LedgerValidator ledgerValidator,
                           EntryPointSelector entryPointSelector,
                           RatingCalculator ratingCalculator,
                           Walker walkerAlpha,
                           MilestoneTracker milestoneTracker,
                           TipSelConfig config) {

        this.entryPointSelector = entryPointSelector;
        this.ratingCalculator = ratingCalculator;

        this.walker = walkerAlpha;

        //used by walkValidator
        this.ledgerValidator = ledgerValidator;
        this.tangle = tangle;
        this.milestoneTracker = milestoneTracker;
        this.config = config;
    }

    @Override
    public List<Hash> getTransactionsToApprove(int depth, Optional<Hash> reference) throws Exception {
        List<Hash> tips = new LinkedList<>(); 

        // Parental tip
        Hash parentTip = tangle.getLastPivot();
        tips.add(parentTip);

        // Reference tip
        Hash entryPoint = entryPointSelector.getEntryPoint(depth);

        UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(entryPoint);

        WalkValidator walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, milestoneTracker, config);
        if(BaseIotaConfig.getInstance().getWalkValidator().equals("NULL")) {
            walkValidator = new WalkValidatorNull();
        }

        Hash refTip;
        refTip = walker.walk(entryPoint, rating, walkValidator);
        tips.add(refTip);

        // TODO validate UTXO etc.

        return tips;
    }
}
