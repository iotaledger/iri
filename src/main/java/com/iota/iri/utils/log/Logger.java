package com.iota.iri.utils.log;

/**
 * Represents a wrapper for the logback Logger, which allows us to add aditional features to the logging capabilities of
 * IRI.
 */
public interface Logger {
    /**
     * This method allows us to set a new info message that shall get printed to the screen.
     *
     * @param message info message that shall get printed
     * @return the logger itself to allow the chaining of calls
     */
    Logger info(String message);

    /**
     * This method allows us to set a new debug message that shall get printed to the screen.
     *
     * @param message debug message that shall get printed
     * @return the logger itself to allow the chaining of calls
     */
    Logger debug(String message);

    /**
     * This method allows us to set a new error message that shall get printed to the screen.
     *
     * @param message debug message that shall get printed
     * @return the logger itself to allow the chaining of calls
     */
    Logger error(String message);

    /**
     * This method allows us to set a new error message that shall get printed to the screen.
     *
     * @param message debug message that shall get printed
     * @param cause exception object that caused the error to happen (will be printed with the complete stacktrace)
     */
    Logger error(String message, Throwable cause);

    /**
     * This method is the getter for the enabled flag of the logger.
     *
     * If the logger is disabled it will simply omit showing log messages.
     *
     * @return true if the logger shall output messages and false otherwise (the logger is enabled by default)
     */
    boolean getEnabled();

    /**
     * This method is the setter for the enabled flag of the logger.
     *
     * If the logger is disabled it will simply omit showing log messages.
     *
     * @param enabled true if the logger shall output messages and false otherwise (the logger is enabled by default)
     * @return the logger itself to allow the chaining of calls
     */
    Logger setEnabled(boolean enabled);
}
