package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.tipselection.TipSelSolidifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.iota.iri.service.validation.TransactionSolidifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncTipSelSolidifier implements TipSelSolidifier {


    private static final Logger log = LoggerFactory.getLogger(AsyncTipSelSolidifier.class);

    private final TransactionSolidifier transactionSolidifier;
    private final Set<Hash> transactionToSolidify = ConcurrentHashMap.newKeySet();
    private final ExecutorService solidExecutor;

    public AsyncTipSelSolidifier(TransactionSolidifier transactionSolidifier) {
        this.transactionSolidifier = transactionSolidifier;
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(this.getClass().getSimpleName() + " %d")
                .build();
        solidExecutor = Executors.newCachedThreadPool(threadFactory);
    }


    @Override
    public void solidify(Hash transactionHash) {
        // Don't try to solidify transactions that are in the process of being solidified
        //Problem: we are not checking transactions in the past cone here
        if (transactionToSolidify.add(transactionHash)) {
            solidExecutor.submit(() -> {
                try {
                    log.debug("attempting to solidify transaction {}", transactionHash);
                    transactionSolidifier.checkSolidity(transactionHash, MilestoneSolidifier.SOLIDIFICATION_TRANSACTIONS_LIMIT);
                } catch (Exception e) {
                    log.error("Failed to solidify transaction during a walk", e);
                }
                transactionToSolidify.remove(transactionHash);
            });
        }
    }
}
