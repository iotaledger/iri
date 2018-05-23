package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.service.tipselection.Walker;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class walkValidatorImpl implements WalkValidator {

    private final Tangle tangle;
    private final MessageQ messageQ;
    private final Logger log = LoggerFactory.getLogger(WalkValidator.class);
    private final LedgerValidator ledgerValidator;
    private final TransactionValidator transactionValidator;

    private final int maxIndex;

    private Set<Hash> maxDepthOkMemoization;
    private Map<Hash, Long> myDiff;
    private Set<Hash> myApprovedHashes;

    public walkValidatorImpl(Tangle tangle, MessageQ messageQ, LedgerValidator ledgerValidator, TransactionValidator transactionValidator, int maxDepth) {
        this.tangle = tangle;
        this.messageQ = messageQ;
        this.ledgerValidator = ledgerValidator;
        this.transactionValidator = transactionValidator;
        this.maxIndex = maxDepth;
        //TODO: calculate diff depth

        maxDepthOkMemoization = new HashSet<>();
        myDiff = new HashMap<>();
        myApprovedHashes = new HashSet<>();
    }

    @Override
    public boolean isValid(Hash transactionId) {
        try {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, transactionId);
            if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                log.debug("Reason to stop: transactionViewModel == null");
                messageQ.publish("rtsn %s", transactionViewModel.getHash());
                return false;
            } else if (!transactionValidator.checkSolidity(transactionViewModel.getHash(), false)) {
                log.debug("Reason to stop: !checkSolidity");
                messageQ.publish("rtss %s", transactionViewModel.getHash());
                return false;
            } else if (belowMaxDepth(transactionViewModel.getHash(), maxIndex)) {
                log.debug("Reason to stop: belowMaxDepth");
                return false;
            } else if (!ledgerValidator.updateDiff(myApprovedHashes, myDiff, transactionViewModel.getHash())) {
                log.debug("Reason to stop: !LedgerValidator");
                messageQ.publish("rtsv %s", transactionViewModel.getHash());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Exception while validating", e);
            throw new RuntimeException(e);
        }
    }

    private boolean belowMaxDepth(Hash tip, int depth) throws Exception {
        //if tip is confirmed stop
        if (TransactionViewModel.fromHash(tangle, tip).snapshotIndex() >= depth) {
            return false;
        }
        //if tip unconfirmed, check if any referenced tx is confirmed below maxDepth
        Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Set<Hash> analyzedTransactions = new HashSet<>();
        Hash hash;
        while ((hash = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedTransactions.add(hash)) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                if (transaction.snapshotIndex() != 0 && transaction.snapshotIndex() < depth) {
                    return true;
                }
                if (transaction.snapshotIndex() == 0) {
                    if (maxDepthOkMemoization.contains(hash)) {
                        //log.info("Memoization!");
                    }
                    else {
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
