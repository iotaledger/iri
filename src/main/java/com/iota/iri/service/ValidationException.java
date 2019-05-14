package com.iota.iri.service;

public class ValidationException extends RuntimeException {

    /**
     * Initializes a new instance of the ValidationException.
     */
    public ValidationException() {
        super("Invalid parameters are passed");
    }

    /**
     * Initializes a new instance of the ValidationException with the specified detail message.
     * @param msg message shown in exception details.
     */
    public ValidationException(String msg) {
        super(msg);
    }
}
