package com.iota.iri.service.restserver;

/**
 * 
 * Connector interface which contains logic for starting and stopping a REST server
 *
 */
public interface RestConnector {

    /**
     * Initializes the REST server.
     * 
     * @param processFunction the function/class we call after dependency specific handling
     */
    void init(ApiProcessor processFunction);

    /**
     * Starts the server.
     * If {@link #init(ApiProcessor)} has not been called, nothing happens
     */
    void start();

    /**
     * Stops the REST server, so no more API calls can be made
     */
    void stop();
}
