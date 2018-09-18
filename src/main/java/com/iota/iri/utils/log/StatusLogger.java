package com.iota.iri.utils.log;

import org.slf4j.Logger;

/**
 * This class represents a wrapper for the {@link Logger} used by IRI that implements a logic to regularly issue status
 * updates about a certain task.
 *
 * Instead of printing all messages immediately and unnecessarily spamming the console output, it rate limits the amount
 * of messages shown and only prints messages if the status has changed since the last output.
 */
public class StatusLogger {
    /**
     * Holds the interval in milliseconds that status messages get printed.
     */
    private static final int LOG_INTERVAL = 3000;

    /**
     * Holds a reference to the underlying logger.
     */
    private final Logger logger;

    /**
     * Holds the current status message that shall get printed to the console.
     */
    private String statusMessage = "";

    /**
     * Holds the last status message that was printed.
     */
    private String lastPrintedStatusMessage = "";

    /**
     * Holds a reference to the thread that takes care of scheduling the next status update.
     */
    private Thread outputThread = null;

    /**
     * Holds a timestamp value when the last message was printed.
     */
    private long lastLogTime = 0;

    /**
     * Constructor of the class.
     *
     * It simply stores the passed in parameters to be able to access them later on.
     *
     * @param logger {@link Logger} object that is used in combination with logback to issue messages in the console
     */
    public StatusLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * This method allows us to set a new status that shall get printed to the screen.
     *
     * It checks if the given message is new and then either prints the message immediately (if enough time has passed
     * since the last message) or spawns a {@link Thread} that will print the new status after the given timeout.
     *
     * When creating the {@link Thread} it copies its name so the output is "transparent" to the user and the effect
     * is the same as dumping the message manually through the {@link Logger} object itself.
     *
     * @param message status message that shall get printed
     */
    public void status(String message) {
        if (!message.equals(statusMessage)) {
            statusMessage = message;

            if (System.currentTimeMillis() - lastLogTime >= LOG_INTERVAL) {
                printStatusMessage();
            } else if (outputThread == null) {
                synchronized(this) {
                    if (outputThread == null) {
                        outputThread = new Thread(() -> {
                            try {
                                Thread.sleep(Math.max(LOG_INTERVAL - (System.currentTimeMillis() - lastLogTime), 1));
                            } catch(InterruptedException e) { /* do nothing */ }

                            printStatusMessage();

                            outputThread = null;
                        }, Thread.currentThread().getName());

                        outputThread.start();
                    }
                }
            }
        }
    }

    /**
     * This method issues an error message.
     *
     * Error messages will always get dumped immediately instead of scheduling their output. Since error messages are
     * usually a sign of some misbehaviour of the node we want to be able to see them when they appear (to be able to
     * track down bugs more easily).
     *
     * @param errorMessage error message that shall get dumped
     */
    public void error(String errorMessage) {
        logger.error(errorMessage);
    }

    /**
     * This method issues an error message and also prints the stack trace of the given exception.
     *
     * Error messages will always get dumped immediately instead of scheduling their output. Since error messages are
     * usually a sign of some misbehaviour of the node we want to be able to see them when they appear (to be able to
     * track down bugs more easily).
     *
     * @param errorMessage error message that shall get dumped
     * @param cause exception object that caused the error to happen (will be printed with the complete stacktrace)
     */
    public void error(String errorMessage, Throwable cause) {
        logger.error(errorMessage, cause);
    }

    /**
     * This method handles the actual output of messages through the {@link Logger} instance.
     *
     * It first checks if the message that shall get printed differs from the last message that was printed and then
     * issues the output of the message. After printing the message, it updates the internal variables to handle the
     * next message accordingly.
     */
    private void printStatusMessage() {
        if (!statusMessage.equals(lastPrintedStatusMessage)) {
            logger.info(statusMessage);

            lastLogTime = System.currentTimeMillis();
            lastPrintedStatusMessage = statusMessage;
        }
    }
}
