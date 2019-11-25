package com.iota.iri.service.tipselection.impl;

import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;


/**
 * Does the same thing as {@link WalkValidatorNoMaxDepthImpl} except it adds a below max depth check
 */
public class WalkValidatorImpl extends WalkValidatorNoMaxDepthImpl {

    private final SnapshotProvider snapshotProvider;
    private final TipSelConfig config;

    private Set<Hash> maxDepthOkMemoization;

    /**
     * Constructor of Walk Validator
     * @param tangle Tangle object which acts as a database interface.
     * @param snapshotProvider grants access to snapshots od the ledger state.
     * @param ledgerService allows to perform ledger related logic.
     * @param config configurations to set internal parameters.
     */
    public WalkValidatorImpl(Tangle tangle, SnapshotProvider snapshotProvider, LedgerService ledgerService,
            TipSelConfig config) {

        super(tangle, ledgerService);
        this.snapshotProvider = snapshotProvider;
        this.config = config;

        maxDepthOkMemoization = new HashSet<>();
        myDiff = new HashMap<>();
        myApprovedHashes = new HashSet<>();
    }

    @Override
    public boolean isValid(Hash transactionHash) throws Exception {

        if (!super.isValid(transactionHash)) {
            return false;
        }
        if (belowMaxDepth(transactionHash,
                snapshotProvider.getLatestSnapshot().getIndex() - config.getMaxDepth())) {
            log.debug("Validation failed: {} is below max depth", transactionHash);
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
                if ((transaction.snapshotIndex() != 0 || snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(transaction.getHash()))
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
