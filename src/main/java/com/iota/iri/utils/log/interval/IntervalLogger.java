package com.iota.iri.utils.log.interval;

import com.iota.iri.utils.log.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class represents a wrapper for the {@link org.slf4j.Logger} used by IRI that implements a logic to rate limits
 * the output on the console.
 *
 * Instead of printing all messages immediately and unnecessarily spamming the console, it only prints messages every
 * few seconds and if the message has changed since the last output.
 */
public class IntervalLogger implements Logger {
    /**
     * Holds the default interval in milliseconds that status messages get printed.
     */
    private static final int DEFAULT_LOG_INTERVAL = 3000;

    /**
     * Holds a reference to the underlying logback logger.
     */
    private final org.slf4j.Logger delegate;

    /**
     * Holds the interval in milliseconds that status messages get printed.
     */
    private final int logInterval;

    /**
     * Holds the last message that was received by the logger and shall get printed to the console.
     */
    private Message lastReceivedMessage;

    /**
     * Holds the last status message that was printed.
     */
    private Message lastPrintedMessage;

    /**
     * Holds a flag that is used to determine if an output was scheduled already.
     */
    private AtomicBoolean outputScheduled = new AtomicBoolean(false);

    /**
     * Holds a reference to the scheduled job which allows us to cancel its execution if we force the output before.
     */
    private Future<?> scheduledJob;

    /**
     * Holds the manager for the {@link Thread} that schedules the delayed output.
     */
    private final ScheduledExecutorService outputScheduler = Executors.newScheduledThreadPool(1);

    /**
     * Holds a timestamp value when the last message was printed.
     */
    private long lastLogTime = 0;

    /**
     * Flag that indicates if the output is enabled.
     */
    private boolean enabled = true;

    /**
     * Does the same as {@link #IntervalLogger(Class, int)} but defaults to the {@link #DEFAULT_LOG_INTERVAL} for the
     * interval.
     *
     * @param clazz class that is used to identify the origin of the log messages
     */
    public IntervalLogger(Class<?> clazz) {
        this(clazz, DEFAULT_LOG_INTERVAL);
    }

    /**
     * Does the same as {@link #IntervalLogger(org.slf4j.Logger, int)} but defaults to the {@link #DEFAULT_LOG_INTERVAL}
     * for the interval.
     *
     * @param delegate logback logger for issuing the messages
     */
    public IntervalLogger(org.slf4j.Logger delegate) {
        this(delegate, DEFAULT_LOG_INTERVAL);
    }

    /**
     * Does the same as {@link #IntervalLogger(org.slf4j.Logger, int)} but creates a new logback logger for the given
     * class.
     *
     * @param clazz class that is used to identify the origin of the log messages
     * @param logInterval time in milliseconds between log messages
     */
    public IntervalLogger(Class<?> clazz, int logInterval) {
        this(LoggerFactory.getLogger(clazz), logInterval);
    }

    /**
     * Creates a {@link Logger} for the given class that prints messages only every {@code logInterval} milliseconds.
     *
     * It simply stores the passed in parameters in its private properties to be able to access them later on.
     *
     * @param delegate logback logger for issuing the messages
     * @param logInterval time in milliseconds between log messages
     */
    public IntervalLogger(org.slf4j.Logger delegate, int logInterval) {
        this.delegate = delegate;
        this.logInterval = logInterval;
    }

    /**
     * This method returns the underlying logback Logger.<br />
     * <br />
     * It can be used to issue log entries directly to the underlying logger without interfering with the logic of this
     * class.<br />
     *
     * @return the underlying logback Logger
     */
    public org.slf4j.Logger delegate() {
        return delegate;
    }

    /**
     * {@inheritDoc}
     *
     * It checks if the given message is new and then triggers the output.
     *
     * @param message info message that shall get printed
     */
    @Override
    public IntervalLogger info(String message) {
        Message newMessage = new InfoMessage(message);

        if (!newMessage.equals(lastReceivedMessage)) {
            lastReceivedMessage = newMessage;

            triggerOutput();
        }

        return this;
    }

    /**
     * {@inheritDoc}
     *
     * It checks if the given message is new and then triggers the output.
     */
    @Override
    public IntervalLogger debug(String message) {
        Message newMessage = new DebugMessage(message);

        if (!newMessage.equals(lastReceivedMessage)) {
            lastReceivedMessage = newMessage;

            triggerOutput();
        }

        return this;
    }

    /**
     * {@inheritDoc}
     *
     * It checks if the given message is new and then triggers the output.
     *
     * Error messages will always get dumped immediately instead of scheduling their output. Since error messages are
     * usually a sign of some misbehaviour of the node we want to be able to see them when they appear (to be able to
     * track down bugs more easily).
     */
    @Override
    public IntervalLogger error(String message) {
        return error(message, null);
    }

    /**
     * {@inheritDoc}
     *
     * It checks if the given message is new and then triggers the output.
     *
     * Error messages will always get dumped immediately instead of scheduling their output. Since error messages are
     * usually a sign of some misbehaviour of the node we want to be able to see them when they appear (to be able to
     * track down bugs more easily).
     */
    @Override
    public IntervalLogger error(String message, Throwable cause) {
        Message newMessage = new ErrorMessage(message, cause);

        if (!newMessage.equals(lastReceivedMessage)) {
            lastReceivedMessage = newMessage;

            triggerOutput(true);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntervalLogger setEnabled(boolean enabled) {
        this.enabled = enabled;

        return this;
    }

    /**
     * Does the same as {@link #triggerOutput(boolean)} but defaults to letting the logger decide when to print the
     * message.
     */
    public void triggerOutput() {
        triggerOutput(false);
    }

    /**
     * Triggers the output of the last received message.
     *
     * It either prints the message immediately (if enough time has passed since the last output or requested by the
     * caller) or schedules it for the next interval.
     *
     * @param printImmediately flag indicating if the messages should be scheduled or printed immediately
     */
    public void triggerOutput(boolean printImmediately) {
        if (lastReceivedMessage != null && (printImmediately ||
                System.currentTimeMillis() - lastLogTime >= logInterval)) {

            lastReceivedMessage.output();
        } else {
            scheduleOutput();
        }
    }

    /**
     * This method schedules the output of the last received message by spawning a {@link Thread} that will print the
     * new message after the given timeout.
     *
     * When creating the {@link Thread} it copies its name so the output is "transparent" to the user and the effect
     * is the same as dumping the message manually through the {@link org.slf4j.Logger} object itself.
     */
    private void scheduleOutput() {
        if (outputScheduled.compareAndSet(false, true)) {
            String currentThreadName = Thread.currentThread().getName();

            scheduledJob = outputScheduler.schedule(() -> {
                Thread.currentThread().setName(currentThreadName);

                triggerOutput(true);
            }, Math.max(logInterval - (System.currentTimeMillis() - lastLogTime), 1), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Represents the basic logic that is shared by all message types that are supported by this logger.
     */
    private abstract class Message {
        /**
         * Holds the message that shall get printed.
         */
        protected final String message;

        /**
         * Creates a message that gets managed by this logger.
         *
         * It simply stores the provided message in the internal property.
         *
         * @param message message that shall get printed
         */
        public Message(String message) {
            this.message = message;
        }

        /**
         * This method triggers the output of the given message.
         *
         * It first cancels any scheduled job because the latest provided message will be printed by this call already
         * and then triggers the printing of the message through the instance specific {@link #print()} method.
         *
         * We only print the message if the same method was not printed already, before.
         */
        public void output() {
            if (scheduledJob != null) {
                scheduledJob.cancel(true);
            }

            if (!this.equals(lastPrintedMessage)) {
                if (enabled) {
                    print();
                }

                lastPrintedMessage = this;
                lastLogTime = System.currentTimeMillis();
            }

            outputScheduled.set(false);
        }

        /**
         * This method allows us to use the Messages in HashMaps and other collections that use the hashCode method to
         * identify objects. Even tho this is not actively used we anyway want to implement it to be on the safe side
         * if we ever implement something like this.
         *
         * @return a unique int identifier that can be used to address equal objects
         */
        @Override
        public int hashCode() {
            return Objects.hash(getClass(), message);
        }

        /**
         * This method allows us to compare different {@link Message}s for equality and check if we have received or
         * processed the same {@link Message} already.
         *
         * @param obj object that shall be compared to this instance
         * @return true if it represents the same message or false otherwise
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj == null || !getClass().equals(obj.getClass())) {
                return false;
            }

            return Objects.equals(message, ((Message) obj).message);
        }

        /**
         * This method handles the actual output of messages through the {@link org.slf4j.Logger} instance.
         *
         * It first checks if the message that shall get printed differs from the last message that was printed and then
         * issues the output of the message. After printing the message, it updates the internal variables to handle the
         * next message accordingly.
         */
        protected abstract void print();
    }

    /**
     * Represents a message for the "info logging level".
     */
    private class InfoMessage extends Message {
        /**
         * Creates a message that gets dumped of the "info logging level".
         *
         * @param message message that shall get printed
         */
        public InfoMessage(String message) {
            super(message);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void print() {
            delegate().info(message);
        }
    }

    /**
     * Represents a message for the "debug logging level".
     */
    private class DebugMessage extends Message {
        /**
         * Creates a message that gets dumped of the "debug logging level".
         *
         * @param message message that shall get printed
         */
        public DebugMessage(String message) {
            super(message);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void print() {
            delegate().debug(message);
        }
    }

    /**
     * Represents a message for the "error logging level".
     */
    private class ErrorMessage extends Message {
        /**
         * Holds the cause of the error message.
         */
        private final Throwable cause;

        /**
         * Creates a message that gets dumped of the "error logging level".
         *
         * @param message message that shall get printed
         * @param cause exception object that caused the error to happen (will be printed with the complete stacktrace)
         */
        public ErrorMessage(String message, Throwable cause) {
            super(message);

            this.cause = cause;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(getClass(), message, cause);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && cause == ((ErrorMessage) obj).cause;
        }

        /**
         * {@inheritDoc}
         *
         * It also includes a stack trace of the cause of the error if it was provided when creating this instance.
         */
        @Override
        protected void print() {
            if (cause == null) {
                delegate().error(message);
            } else {
                delegate().error(message, cause);
            }
        }
    }
}
