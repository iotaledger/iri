package com.iota.iri.cache;

/**
 * Invalid cache configuration Exception
 */
public class InvalidCacheConfigurationException extends RuntimeException {

    /**
     * Constructor
     * 
     * @param message Message
     */
    public InvalidCacheConfigurationException(String message) {
        super(message);
    }
}
