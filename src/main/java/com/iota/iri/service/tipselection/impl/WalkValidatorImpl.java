package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.impl.BoundedSetWrapper;
import com.iota.iri.utils.collections.interfaces.BoundedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of <tt>WalkValidator</tt> that checks consistency of the ledger as part of validity checks.
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

    public static final int INITIAL_CACHE_CAPACITY = 10_000;
    //As long as tip selection is synchronized we are fine with the collection not being thread safe
    static BoundedSet<Hash> failedBelowMaxDepthCache;
    private int maxAnalyzedTxs;

    private final Tangle tangle;
    private final Logger log = LoggerFactory.getLogger(WalkValidator.class);
    private final LedgerValidator ledgerValidator;
    private final TransactionValidator transactionValidator;
    private final Milestone milestone;

    private final int maxDepth;

    private Set<Hash> maxDepthOkMemoization;
    private Map<Hash, Long> myDiff;
    private Set<Hash> myApprovedHashes;

    public WalkValidatorImpl(Tangle tangle, LedgerValidator ledgerValidator, TransactionValidator transactionValidator,
                             Milestone milestone, int maxDepth, int maxAnalyzedTxs, int cacheSize) {
        this.tangle = tangle;
        this.ledgerValidator = ledgerValidator;
        this.transactionValidator = transactionValidator;
        this.milestone = milestone;
        this.maxDepth = maxDepth;
        this.maxAnalyzedTxs = maxAnalyzedTxs;

        failedBelowMaxDepthCache = fetchCache(cacheSize);
        maxDepthOkMemoization = new HashSet<>();
        myDiff = new HashMap<>();
        myApprovedHashes = new HashSet<>();
    }

    private BoundedSet<Hash> fetchCache(int cacheSize) {
        if (failedBelowMaxDepthCache == null) {
            failedBelowMaxDepthCache = new BoundedSetWrapper<>(
                    ConcurrentHashMap.newKeySet(INITIAL_CACHE_CAPACITY), cacheSize);
        }
        return failedBelowMaxDepthCache;
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
        } else if (belowMaxDepth(transactionViewModel.getHash(), milestone.latestSolidSubtangleMilestoneIndex - maxDepth)) {
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
        while ((hash = nonAnalyzedTransactions.poll()) != null) {
            if (failedBelowMaxDepthCache.contains(hash)) {
                log.debug("failed below max depth because of a previously failed tx cache hit");
                updateCache(analyzedTransactions);
                return true;
            }
            if (analyzedTransactions.size() == maxAnalyzedTxs) {
                log.debug("failed below max depth because of exceeding max threshold of {} analyzed transactions",
                        maxAnalyzedTxs);
                updateCache(analyzedTransactions);
                return true;
            }

            if (analyzedTransactions.add(hash)) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                if ((transaction.snapshotIndex() != 0 || Objects.equals(Hash.NULL_HASH, transaction.getHash()))
                        && transaction.snapshotIndex() < lowerAllowedSnapshotIndex) {
                    updateCache(analyzedTransactions);
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

    private void updateCache(Set<Hash> txsToBeAdded) {
        failedBelowMaxDepthCache.addAll(txsToBeAdded);
    }

}
