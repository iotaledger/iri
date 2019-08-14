package com.iota.iri.network.neighbor.impl;

import com.iota.iri.network.neighbor.NeighborMetrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements {@link NeighborMetrics} using {@link AtomicLong}s.
 */
public class NeighborMetricsImpl implements NeighborMetrics {

    private AtomicLong allTxsCount = new AtomicLong();
    private AtomicLong invalidTxsCount = new AtomicLong();
    private AtomicLong staleTxsCount = new AtomicLong();
    private AtomicLong randomTxsCount = new AtomicLong();
    private AtomicLong sentTxsCount = new AtomicLong();
    private AtomicLong newTxsCount = new AtomicLong();
    private AtomicLong droppedSendPacketsCount = new AtomicLong();

    @Override
    public long getAllTransactionsCount() {
        return allTxsCount.get();
    }

    @Override
    public long incrAllTransactionsCount() {
        return allTxsCount.incrementAndGet();
    }

    @Override
    public long getInvalidTransactionsCount() {
        return invalidTxsCount.get();
    }

    @Override
    public long incrInvalidTransactionsCount() {
        return invalidTxsCount.incrementAndGet();
    }

    @Override
    public long getStaleTransactionsCount() {
        return staleTxsCount.get();
    }

    @Override
    public long incrStaleTransactionsCount() {
        return staleTxsCount.incrementAndGet();
    }

    @Override
    public long getNewTransactionsCount() {
        return newTxsCount.get();
    }

    @Override
    public long incrNewTransactionsCount() {
        return newTxsCount.incrementAndGet();
    }

    @Override
    public long getRandomTransactionRequestsCount() {
        return randomTxsCount.get();
    }

    @Override
    public long incrRandomTransactionRequestsCount() {
        return randomTxsCount.incrementAndGet();
    }

    @Override
    public long getSentTransactionsCount() {
        return sentTxsCount.get();
    }

    @Override
    public long incrSentTransactionsCount() {
        return sentTxsCount.incrementAndGet();
    }

    @Override
    public long getDroppedSendPacketsCount() {
        return droppedSendPacketsCount.get();
    }

    @Override
    public long incrDroppedSendPacketsCount() {
        return droppedSendPacketsCount.incrementAndGet();
    }
}
