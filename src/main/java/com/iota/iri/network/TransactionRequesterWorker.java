package com.iota.iri.network;

/**
 * Creates a background worker that tries to work through the request queue by sending random tips along the requested
 * transactions.<br />
 * <br />
 * This massively increases the sync speed of new nodes that would otherwise be limited to requesting in the same rate
 * as new transactions are received.<br />
 */
public interface TransactionRequesterWorker {
    
    /**
     * Works through the request queue by sending a request alongside a random tip to each of our neighbors.<br />
     * @return <code>true</code> when we have send the request to our neighbors, otherwise <code>false</code>
     */
    boolean processRequestQueue();

    /**
     * Starts the background worker that automatically calls {@link #processRequestQueue()} periodically to process the
     * requests in the queue.<br />
     */
    void start();

    /**
     * Stops the background worker that automatically works through the request queue.<br />
     */
    void shutdown();
}
