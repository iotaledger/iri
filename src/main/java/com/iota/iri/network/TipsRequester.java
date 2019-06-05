package com.iota.iri.network;

/**
 * The {@link TipsRequester} requests tips from all neighbors in a given interval.
 */
public interface TipsRequester {

    /**
     * Issues random tip requests to all connected neighbors.
     */
    void requestTips();

    /**
     * Starts the background worker that automatically calls {@link #requestTips()} periodically to request
     * tips from neighbors.
     */
    void start();

    /**
     * Stops the background worker that requests tips from the neighbors.
     */
    void shutdown();

}
