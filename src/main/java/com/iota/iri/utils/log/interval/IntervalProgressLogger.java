package com.iota.iri.utils.log.interval;

import com.iota.iri.utils.log.ProgressLogger;
import org.slf4j.Logger;

import java.text.DecimalFormat;

/**
 * Implements the basic contract of the {@link ProgressLogger} while showing the progress as messages in the console.
 *
 * Instead of printing all messages immediately and unnecessarily spamming the console output, it rate limits the amount
 * of messages shown and only prints updates in a pre-defined interval.
 */
public class IntervalProgressLogger implements ProgressLogger {
    /**
     * Holds the default interval (in milliseconds) in which the logger shows messages.
     */
    private static final int DEFAULT_LOG_INTERVAL = 3000;

    /**
     * Holds the name of the task that this logger represents.
     */
    private final String taskName;

    /**
     * Holds the logger that takes care of showing our messages.
     */
    private final IntervalLogger intervalLogger;

    /**
     * The last finished step of the task.
     */
    private int currentStep = 0;

    /**
     * The total amount of steps of this task.
     */
    private int stepCount = 1;

    /**
     * Does the same as {@link #IntervalProgressLogger(String, Class, int)} but defaults to
     * {@link #DEFAULT_LOG_INTERVAL} for the interval.
     *
     * @param taskName name of the task that shall be shown on the screen
     * @param clazz class that is used to identify the origin of the log messages
     */
    public IntervalProgressLogger(String taskName, Class<?> clazz) {
        this(taskName, clazz, DEFAULT_LOG_INTERVAL);
    }

    /**
     * Does the same as {@link #IntervalProgressLogger(String, Logger, int)} but defaults to
     * {@link #DEFAULT_LOG_INTERVAL} for the interval.
     *
     * @param taskName name of the task that shall be shown on the screen
     * @param logger logback logger for issuing the messages
     */
    public IntervalProgressLogger(String taskName, Logger logger) {
        this(taskName, logger, DEFAULT_LOG_INTERVAL);
    }

    /**
     * Creates a {@link ProgressLogger} that shows its progress in a pre-defined interval.
     *
     * @param taskName name of the task that shall be shown on the screen
     * @param clazz class that is used to identify the origin of the log messages
     * @param logInterval the time in milliseconds between consecutive log messages
     */
    public IntervalProgressLogger(String taskName, Class<?> clazz, int logInterval) {
        this.taskName = taskName;
        this.intervalLogger = new IntervalLogger(clazz, logInterval);
    }

    /**
     * Creates a {@link ProgressLogger} that shows its progress in a pre-defined interval.
     *
     * @param taskName name of the task that shall be shown on the screen
     * @param logger logback logger for issuing the messages
     * @param logInterval the time in milliseconds between consecutive log messages
     */
    public IntervalProgressLogger(String taskName, Logger logger, int logInterval) {
        this.taskName = taskName;
        this.intervalLogger = new IntervalLogger(logger, logInterval);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntervalProgressLogger start() {
        return setCurrentStep(0).triggerOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntervalProgressLogger start(int stepCount) {
        return setStepCount(stepCount).setCurrentStep(0).triggerOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntervalProgressLogger progress() {
        return setCurrentStep(++currentStep).triggerOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntervalProgressLogger progress(int currentStep) {
        return setCurrentStep(currentStep).triggerOutput();
    }

    /**
     * {@inheritDoc}
     *
     * It will show the message immediately instead of waiting for the interval to pass.
     */
    @Override
    public IntervalProgressLogger finish() {
        return setCurrentStep(stepCount).triggerOutput(true);
    }

    /**
     * {@inheritDoc}
     *
     * It will show the message immediately instead of waiting for the interval to pass.
     */
    @Override
    public IntervalProgressLogger abort() {
        return abort(null);
    }

    /**
     * {@inheritDoc}
     *
     * It will show the message immediately instead of waiting for the interval to pass.
     */
    @Override
    public IntervalProgressLogger abort(Throwable cause) {
        double progress = Math.min(100d, ((double) currentStep / (double) stepCount) * 100d);
        String logMessage = taskName + ": " + new DecimalFormat("#.00").format(progress) + "% ... [FAILED]";

        if (cause != null) {
            intervalLogger.error(logMessage, cause);
        } else {
            intervalLogger.error(logMessage);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCurrentStep() {
        return currentStep;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntervalProgressLogger setCurrentStep(int currentStep) {
        this.currentStep = currentStep;

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStepCount() {
        return stepCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntervalProgressLogger setStepCount(int stepCount) {
        this.stepCount = stepCount;

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getEnabled() {
        return intervalLogger.getEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntervalProgressLogger setEnabled(boolean enabled) {
        this.intervalLogger.setEnabled(enabled);

        return this;
    }

    /**
     * Does the same as {@link #triggerOutput(boolean)} but defaults to but defaults to letting the logger decide when
     * to print the message.
     *
     * @return the logger itself to allow the chaining of calls
     */
    private IntervalProgressLogger triggerOutput() {
        return triggerOutput(false);
    }

    /**
     * This method triggers the output of the logger.
     *
     * It calculates the current progress, generates the log message and passes it on to the underlying status logger.
     *
     * @param printImmediately flag indicating if the message shall be printed immediately
     * @return the logger itself to allow the chaining of calls
     */
    private IntervalProgressLogger triggerOutput(boolean printImmediately) {
        if (stepCount != 0) {
            double progress = Math.min(100d, ((double) currentStep / (double) stepCount) * 100d);
            String logMessage = taskName + ": " + new DecimalFormat("0.00").format(progress) + "% ..." +
                    (currentStep >= stepCount ? " [DONE]" : "");

            intervalLogger.info(logMessage);

            if (printImmediately) {
                intervalLogger.triggerOutput(true);
            }
        }

        return this;
    }
}
