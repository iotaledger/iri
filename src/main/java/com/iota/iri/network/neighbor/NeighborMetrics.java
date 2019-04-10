package com.iota.iri.network.neighbor;

/**
 * Defines the metrics of a {@link Neighbor}.
 */
public interface NeighborMetrics {

    /**
     * Returns the number of all transactions.
     * 
     * @return the number of all transactions
     */
    long getAllTransactionsCount();

    /**
     * Increments the all transactions count.
     * 
     * @return the number of all transactions
     */
    long incrAllTransactionsCount();

    /**
     * Gets the number of invalid transctions.
     * 
     * @return the number of invalid transactions
     */
    long getInvalidTransactionsCount();

    /**
     * Increments the invalid transaction count.
     * 
     * @return the number of invalid transactions
     */
    long incrInvalidTransactionsCount();

    /**
     * Gets the number of stale transactions.
     * 
     * @return the number of stale transactions
     */
    long getStaleTransactionsCount();

    /**
     * Increments the number of stale transactions.
     * 
     * @return the number of stale transactions
     */
    long incrStaleTransactionsCount();

    /**
     * Gets the number of new transactions.
     * 
     * @return the number of new transactions
     */
    long getNewTransactionsCount();

    /**
     * Increments the new transactions count.
     * 
     * @return the number of new transactions
     */
    long incrNewTransactionsCount();

    /**
     * Gets the number of random transactions.
     * 
     * @return the number of random transactions
     */
    long getRandomTransactionRequestsCount();

    /**
     * Increments the random transactions count.
     * 
     * @return the number of random transactions
     */
    long incrRandomTransactionRequestsCount();

    /**
     * Gets the number of send transactions.
     * 
     * @return the number of send transactions
     */
    long getSentTransactionsCount();

    /**
     * Increments the send transactions count.
     * 
     * @return the number of send transactions
     */
    long incrSentTransactionsCount();

    /**
     * Gets the number of packets received with an unknown message type.
     * 
     * @return the number of packets received with an unknown message type
     */
    long getUnknownMessageTypePacketsCount();

    /**
     * Increments the count of packets received with an unknown message type.
     * 
     * @return the number of packets received with an unknown message type
     */
    long incrUnknownMessageTypePacketsCount();

    /**
     * Gets the number of packets received with an incompatible protocol version.
     *
     * @return the number of packets received with an incompatible protocol version
     */
    long getIncompatiblePacketsCount();

    /**
     * Increments the count of packets received with an incompatible protocol version.
     *
     * @return the number of packets received with an incompatible protocol version
     */
    long incrIncompatiblePacketsCount();

    /**
     * Gets the number of packets received with a too big advertised message size.
     * 
     * @return the number of packets received with a too big advertised message size
     */
    long getMessageTooBigPacketsCount();

    /**
     * Increments the count of packets received with a too big advertised message size.
     *
     * @return the number of packets received with a too big advertised message size
     */
    long incrMessageTooBigPacketsCount();
}
