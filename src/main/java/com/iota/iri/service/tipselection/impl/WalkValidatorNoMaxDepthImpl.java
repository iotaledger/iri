package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.storage.Tangle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link WalkValidator} that checks consistency of the ledger as part of validity checks.
 *
 * A transaction is only valid if:
 * <ol>
 * <li>it is a tail
 * <li>all the history of the transaction is present (is solid)
 * <li>the ledger is still consistent if the transaction is added (balances of all addresses are correct and all
 * signatures are valid)
 * </ol>
 */
public class WalkValidatorNoMaxDepthImpl implements WalkValidator {

    protected final Tangle tangle;
    protected final Logger log = LoggerFactory.getLogger(WalkValidator.class);
    protected final LedgerService ledgerService;

    protected Map<Hash, Long> myDiff;
    protected Set<Hash> myApprovedHashes;

    /**
     * Constructor of Walk Validator
     *
     * @param tangle        Tangle object which acts as a database interface.
     * @param ledgerService allows to perform ledger related logic.
     */
    public WalkValidatorNoMaxDepthImpl(Tangle tangle, LedgerService ledgerService) {
        this.tangle = tangle;
        this.ledgerService = ledgerService;

        myDiff = new HashMap<>();
        myApprovedHashes = new HashSet<>();
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
        } else if (!ledgerService.isBalanceDiffConsistent(myApprovedHashes, myDiff, transactionViewModel.getHash())) {
            log.debug("Validation failed: {} is not consistent", transactionHash);
            return false;
        }
        return true;
    }
}
