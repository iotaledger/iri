package com.iota.iri.network.neighbor;

public interface NeighborMetrics {

    long getAllTransactionsCount();

    long incrAllTransactionsCount();

    long getInvalidTransactionsCount();

    long incrInvalidTransactionsCount();

    long getStaleTransactionsCount();

    long incrStaleTransactionsCount();

    long getNewTransactionsCount();

    long incrNewTransactionsCount();

    long getRandomTransactionRequestsCount();

    long incrRandomTransactionRequestsCount();

    long getSentTransactionsCount();

    long incrSentTransactionsCount();

    long getUnknownMessageTypePacketsCount();

    long incrUnknownMessageTypePacketsCount();

    long getIncompatiblePacketsCount();

    long incrIncompatiblePacketsCount();

    long getMessageTooBigPacketsCount();

    long incrMessageTooBigPacketsCount();
}
