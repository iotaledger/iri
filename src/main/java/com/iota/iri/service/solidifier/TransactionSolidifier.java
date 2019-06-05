package com.iota.iri.service.solidifier;

/**
 * The {@link TransactionSolidifier} continuously tries to solidify transactions.
 */
public interface TransactionSolidifier {

    /**
     * Starts the background worker that continuously tries to solidify transactions.
     */
    void start();

    /**
     * Solidifies transactions. Which transactions and how are getting solidified is an implementation detail.
     */
    void solidify();

    /**
     * Stops the background worker that continuously solidifies transactions.
     */
    void shutdown();

}
