package com.iota.iri.service;

public class ValidationException extends Exception {

    /**
     * Initializes a new instance of the ArgumentException.
     */
    public ValidationException() {
        super("Wrong arguments passed to function");
    }

    /**
     * Initializes a new instance of the ArgumentException.
     */
    public ValidationException(String msg) {
        super(msg);
    }
}
