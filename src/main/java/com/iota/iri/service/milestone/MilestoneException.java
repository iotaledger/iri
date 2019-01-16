package com.iota.iri.service.milestone;

/**
 * This class is used to wrap exceptions that are specific to the milestone logic.
 *
 * It allows us to distinct between the different kinds of errors that can happen during the execution of the code.
 */
public class MilestoneException extends Exception {
    /**
     * Constructor of the exception which allows us to provide a specific error message and the cause of the error.
     *
     * @param message reason why this error occurred
     * @param cause wrapped exception that caused this error
     */
    public MilestoneException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor of the exception which allows us to provide a specific error message without having an underlying
     * cause.
     *
     * @param message reason why this error occurred
     */
    public MilestoneException(String message) {
        super(message);
    }

    /**
     * Constructor of the exception which allows us to wrap the underlying cause of the error without providing a
     * specific reason.
     *
     * @param cause wrapped exception that caused this error
     */
    public MilestoneException(Throwable cause) {
        super(cause);
    }
}
