package com.iota.iri.service.tipselection.impl;

import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.storage.Tangle;

/**
 * Does the same thing as {@link WalkValidatorImpl} except that it does not check for max depth
 */
public class WalkerValidatorNoMaxDepthImpl extends WalkValidatorImpl {

    /**
     * Constructor of Walk Validator
     *
     * @param tangle           Tangle object which acts as a database interface.
     * @param snapshotProvider grants access to snapshots od the ledger state.
     * @param ledgerService    allows to perform ledger related logic.
     * @param config           configurations to set internal parameters.
     */
    public WalkerValidatorNoMaxDepthImpl(Tangle tangle, SnapshotProvider snapshotProvider, LedgerService ledgerService, TipSelConfig config) {
        super(tangle, snapshotProvider, ledgerService, config);
    }

    @Override
    public boolean isValid(Hash transactionHash) throws Exception {
        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, transactionHash);
        if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
            log.debug("Validation failed: {} is missing in db", transactionHash);
            return false;
        } else if (transactionViewModel.getCurrentIndex() != 0) {
            log.debug("Validation failed: {} not a tail", transactionHash);
            return false;
        } else if (!transactionViewModel.isSolid()) {
            log.debug("Validation failed: {} is not solid", transactionHash);
            return false;
        }else if (!ledgerService.isBalanceDiffConsistent(myApprovedHashes, myDiff, transactionViewModel.getHash())) {
            log.debug("Validation failed: {} is not consistent", transactionHash);
            return false;
        }
        return true;
    }
}
