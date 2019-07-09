package com.iota.iri.storage;

public class CacheException extends Exception {

	/**
     * Initializes a new instance of the CacheException.
     */
    public CacheException() {
        super("Invalid parameters are passed");
    }

    /**
     * Initializes a new instance of the CacheException with the specified detail message.
     * @param msg message shown in exception details.
     */
    public CacheException(String msg) {
        super(msg);
    }
}
