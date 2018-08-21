package com.iota.iri.utils;

import org.slf4j.Logger;

import java.text.DecimalFormat;

public class ProgressLogger {
    private static final int DEFAULT_LOG_INTERVAL = 5000;

    private String taskName;

    private Logger logger;

    private int currentStep;

    private int stepCount;

    private int logInterval;

    private boolean enabled = true;

    private long lastLogTime = 0;

    private String lastLogMessage = "";

    public ProgressLogger(String taskName, Logger logger, int logInterval) {
        this.taskName = taskName;
        this.logger = logger;
        this.logInterval = logInterval;
        this.currentStep = 0;
        this.stepCount = 1;
    }

    public ProgressLogger(String taskName, Logger logger) {
        this(taskName, logger, DEFAULT_LOG_INTERVAL);
    }

    public ProgressLogger setEnabled(boolean enabled) {
        this.enabled = enabled;

        return this;
    }

    public ProgressLogger setCurrentStep(int currentStep) {
        this.currentStep = currentStep;

        return this;
    }

    public ProgressLogger setStepCount(int stepCount) {
        this.stepCount = stepCount;

        return this;
    }

    public int getStepCount() {
        return stepCount;
    }

    private ProgressLogger dumpMessage() {
        long currentTime = System.currentTimeMillis();

        if (
            lastLogTime == 0 ||                                                // first log message ever for this task
            currentTime - lastLogTime >= logInterval ||                        // we didn't dump for the given timeout
            currentStep == stepCount                                           // always dump when finished
        ) {
            double progress = (double) currentStep / (double) stepCount * 100;
            String logMessage = taskName + ": " + new DecimalFormat("#.00").format(progress) + "% done ...";

            // only log when the message changes
            if (!lastLogMessage.equals(logMessage)) {
                logger.info(logMessage);

                lastLogTime = currentTime;
                lastLogMessage = logMessage;
            }
        }

        return this;
    }

    public ProgressLogger start() {
        return setCurrentStep(0).dumpMessage();
    }

    public ProgressLogger start(int stepCount) {
        return setStepCount(stepCount).setCurrentStep(0).dumpMessage();
    }

    public ProgressLogger progress() {
        return setCurrentStep(currentStep++).dumpMessage();
    }

    public ProgressLogger progress(int currentStep) {
        return setCurrentStep(currentStep).dumpMessage();
    }

    public ProgressLogger finish() {
        return setCurrentStep(stepCount).dumpMessage();
    }

    public ProgressLogger abort(Exception e) {
        double progress = (double) currentStep / (double) stepCount * 100;
        String logMessage = taskName + ": " + new DecimalFormat("#.00").format(progress) + "% done ... [FAILED]";

        logger.error(logMessage, e);

        return this;
    }
}
