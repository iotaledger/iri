package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.MilestoneTracker;
import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementation of {@link WalkValidator} that checks consistency of the ledger as part of validity checks.
 *
 *     A transaction is only valid if:
 *      <ol>
 *      <li>it is a tail
 *      <li>all the history of the transaction is present (is solid)
 *      <li>it does not reference an old unconfirmed transaction (not belowMaxDepth)
 *      <li>the ledger is still consistent if the transaction is added
 *          (balances of all addresses are correct and all signatures are valid)
 *      </ol>
 */
public class WalkValidatorImpl implements WalkValidator {

    private final Tangle tangle;
    private final Logger log = LoggerFactory.getLogger(WalkValidator.class);
    private final LedgerValidator ledgerValidator;
    private final MilestoneTracker milestoneTracker;
    private final TipSelConfig config;

    private Set<Hash> maxDepthOkMemoization;
    private Map<Hash, Long> myDiff;
    private Set<Hash> myApprovedHashes;

    /**
     * Constructor of Walk Validator
     * @param tangle Tangle object which acts as a database interface.
     * @param ledgerValidator instance of the ledger validator, used to check ledger consistency.
     * @param milestoneTracker instance of the milestone tracker, used by walk validator to check ledger consistency.
     * @param config configurations to set internal parameters.
     */
    public WalkValidatorImpl(Tangle tangle, LedgerValidator ledgerValidator, MilestoneTracker milestoneTracker, TipSelConfig config) {
        this.tangle = tangle;
        this.ledgerValidator = ledgerValidator;
        this.milestoneTracker = milestoneTracker;
        this.config = config;

        maxDepthOkMemoization = new HashSet<>();
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
        } else if (belowMaxDepth(transactionViewModel.getHash(),
                milestoneTracker.latestSolidSubtangleMilestoneIndex - config.getMaxDepth())) {
            log.debug("Validation failed: {} is below max depth", transactionHash);
            return false;
        } else if (!ledgerValidator.updateDiff(myApprovedHashes, myDiff, transactionViewModel.getHash())) {
            log.debug("Validation failed: {} is not consistent", transactionHash);
            return false;
        }
        return true;
    }

    private boolean belowMaxDepth(Hash tip, int lowerAllowedSnapshotIndex) throws Exception {
        //if tip is confirmed stop
        if (TransactionViewModel.fromHash(tangle, tip).snapshotIndex() >= lowerAllowedSnapshotIndex) {
            return false;
        }
        //if tip unconfirmed, check if any referenced tx is confirmed below maxDepth
        Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Set<Hash> analyzedTransactions = new HashSet<>();
        Hash hash;
        final int maxAnalyzedTransactions = config.getBelowMaxDepthTransactionLimit();
        while ((hash = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedTransactions.size() == maxAnalyzedTransactions) {
                log.debug("failed below max depth because of exceeding max threshold of {} analyzed transactions",
                        maxAnalyzedTransactions);
                return true;
            }

            if (analyzedTransactions.add(hash)) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                if ((transaction.snapshotIndex() != 0 || Objects.equals(Hash.NULL_HASH, transaction.getHash()))
                        && transaction.snapshotIndex() < lowerAllowedSnapshotIndex) {
                    log.debug("failed below max depth because of reaching a tx below the allowed snapshot index {}",
                            lowerAllowedSnapshotIndex);
                    return true;
                }
                if (transaction.snapshotIndex() == 0) {
                    if (!maxDepthOkMemoization.contains(hash)) {
                        nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                    }
                }
            }
        }
        maxDepthOkMemoization.add(tip);
        return false;
    }
}
