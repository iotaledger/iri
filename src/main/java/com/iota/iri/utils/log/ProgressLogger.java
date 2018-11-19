package com.iota.iri.utils.log;

import org.slf4j.Logger;

/**
 * Represents a wrapper for the {@link Logger} used by IRI that implements a logic to report the progress of a single
 * task.
 *
 * The progress is calculated by comparing the current step to the expected step count and shown as a percentage value.
 */
public interface ProgressLogger {
    /**
     * Starts the logging by (re)setting the current step to 0 and trigger the output of the current progress.
     *
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger start();

    /**
     * Does the same as {@link #start()} but sets the expected step count to the given value.
     *
     * This allows us to initialize and start the logging in a single call.
     *
     * @param stepCount number of steps that the task involves
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger start(int stepCount);

    /**
     * Increases the current step by one and triggers the output of the current progress.
     *
     * This should be called whenever we finish a sub task.
     *
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger progress();

    /**
     * Sets the current step to the given value and triggers the output of the current progress.
     *
     * @param currentStep the current step that was just finished
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger progress(int currentStep);

    /**
     * Sets the current step to the expected step count and triggers the output of the current progress.
     *
     * This allows us to dump the progress when the processing of the task succeeds.
     *
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger finish();

    /**
     * Marks the task as failed and triggers the output of a status message informing us about the failure.
     *
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger abort();

    /**
     * Marks the task as failed and triggers the output of a status message informing us about the failure.
     *
     * The shown error message will contain the reason for the error given by the passed in {@link Exception}.
     *
     * @param reason reason for the failure
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger abort(Throwable reason);

    /**
     * Returns the current finished step of this logger.
     *
     * @return current finished step of this logger
     */
    int getCurrentStep();

    /**
     * Sets the current step to the given value without triggering an output.
     *
     * @param currentStep the current step that was just finished
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger setCurrentStep(int currentStep);

    /**
     * Returns the expected step count of this logger.
     *
     * @return the expected step count of this logger
     */
    int getStepCount();

    /**
     * Sets the expected step count to the given value without triggering an output.
     *
     * @param stepCount the expected step count of this logger
     * @return the logger itself to allow the chaining of calls
     */
    ProgressLogger setStepCount(int stepCount);

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
    ProgressLogger setEnabled(boolean enabled);
}
