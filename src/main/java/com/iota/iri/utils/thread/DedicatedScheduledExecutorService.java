package com.iota.iri.utils.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * This class represents a {@link ScheduledExecutorService} that is associated with one specific task for which it
 * provides automatic logging capabilities<br />
 * <br />
 * It informs the user about its lifecycle using the logback loggers used by IRI. In addition it offers "silent" methods
 * of the {@link ScheduledExecutorService} that do not throw {@link Exception}s when we try to start the same task
 * multiple times. This is handy for implementing the "start" and "shutdown" methods of the background workers of IRI
 * that would otherwise have to take care of not starting the same task more than once (when trying to be robust against
 * coding errors or tests that start the same thread multiple times).<br />
 */
public class DedicatedScheduledExecutorService extends BoundedScheduledExecutorService {
    /**
     * Default logger for this class allowing us to dump debug and status messages.<br />
     * <br />
     * Note: The used logger can be overwritten by providing a different logger in the constructor (to have transparent
     *       log messages that look like they are coming from a different source).<br />
     */
    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(DedicatedScheduledExecutorService.class);

    /**
     * Holds a reference to the logger that is used to emit messages.<br />
     */
    private final Logger logger;

    /**
     * Holds the name of the thread that gets started by this class and that gets printed in the log messages.<br />
     */
    private final String threadName;

    /**
     * Flag indicating if we want to issue debug messages (for example whenever a task gets started and finished).<br />
     */
    private final boolean debug;

    /**
     * Creates a {@link ScheduledExecutorService} that is associated with one specific task for which it provides
     * automatic logging capabilities (using the provided thread name).<br />
     * <br />
     * It informs the user about its lifecycle using the logback loggers used by IRI. In addition it offers "silent"
     * methods of the {@link ScheduledExecutorService} that do not throw {@link Exception}s when we try to start the
     * same task multiple times. This is handy for implementing the "start" and "shutdown" methods of the background
     * workers of IRI that would otherwise have to take care of not starting the same task more than once (when trying
     * to be robust against coding errors or tests that start the same thread multiple times).<br />
     * <br />
     * <pre>
     *     <code>Example:
     *
     *         private final static Logger logger = LoggerFactor.getLogger(MilestoneSolidifier.class);
     *
     *         private DedicatedScheduledExecutorService milestoneSolidifier = new DedicatedScheduledExecutorService(
     *                 "Solidification Thread", logger, false);
     *
     *         // calling this multiple times will only start exactly one background job (ignore additional requests)
     *         public void start() {
     *             milestoneSolidifier.silentScheduleAtFixedRate(this::solidificationThread, 10, 50, MILLISECONDS);
     *         }
     *
     *         // calling this multiple times will only stop the one instance that is running (if it is running)
     *         public void shutdown() {
     *             milestoneSolidifier.shutdownNow();
     *         }
     *
     *         public void solidificationThread() {
     *             System.out.println("I get executed every 50 milliseconds");
     *         }
     *     </code>
     *     <code>Resulting Log Output:
     *
     *         [main] INFO  MilestoneSolidifier - Starting [Milestone Solidifier] (starts in 10ms / runs every 50ms) ...
     *         [main] INFO  MilestoneSolidifier - [Milestone Solidifier] Started (execution #1) ...
     *         [Milestone Solidifier] INFO  c.i.i.s.m.MilestoneSolidifier - I get executed every 50 milliseconds
     *         [Milestone Solidifier] INFO  c.i.i.s.m.MilestoneSolidifier - I get executed every 50 milliseconds
     *         [Milestone Solidifier] INFO  c.i.i.s.m.MilestoneSolidifier - I get executed every 50 milliseconds
     *         [main] INFO  MilestoneSolidifier - Stopping [Milestone Solidifier] ...
     *         [main] INFO  MilestoneSolidifier - [Milestone Solidifier] Stopped (after #4 executions) ...
     *     </code
     * </pre>
     *
     * @param threadName name of the thread (or null if we want to disable the automatic logging - exceptions will
     *                   always be logged)
     * @param logger logback logger that shall be used for the origin of the log messages
     * @param debug debug flag that indicates if every "run" should be accompanied with a log message
     */
    public DedicatedScheduledExecutorService(String threadName, Logger logger, boolean debug) {
        super(1);

        this.threadName = threadName;
        this.logger = logger;
        this.debug = debug;
    }

    /**
     * Does the same as {@link #DedicatedScheduledExecutorService(String, Logger, boolean)} but defaults to the
     * {@link #DEFAULT_LOGGER} for the log messages.<br />
     *
     * @param threadName name of the thread (or null if we want to disable the automatic logging - exceptions will
     *                   always be logged)
     * @param debug debug flag that indicates if every "run" should be accompanied with a log message
     */
    public DedicatedScheduledExecutorService(String threadName, boolean debug) {
        this(threadName, DEFAULT_LOGGER, debug);
    }

    /**
     * Does the same as {@link #DedicatedScheduledExecutorService(String, Logger, boolean)} but defaults to false
     * for the debug flag.<br />
     *
     * @param threadName name of the thread (or null if we want to disable the automatic logging - exceptions will
     *                   always be logged)
     * @param logger logback logger that shall be used for the origin of the log messages
     */
    public DedicatedScheduledExecutorService(String threadName, Logger logger) {
        this(threadName, logger, false);
    }

    /**
     * Does the same as {@link #DedicatedScheduledExecutorService(String, Logger, boolean)} but defaults to {@code null}
     * for the thread name (which causes only error messages to be printed - unless debug is true).<br />
     * <br />
     * Note: This is for example used by the {@link com.iota.iri.utils.log.interval.IntervalLogger} which does not want
     *       to inform the user when scheduling a log output, but which still needs the "only run one task" logic.<br />
     *
     * @param logger logback logger that shall be used for the origin of the log messages
     * @param debug debug flag that indicates if every "run" should be accompanied with a log message
     */
    public DedicatedScheduledExecutorService(Logger logger, boolean debug) {
        this(null, logger, debug);
    }

    /**
     * Does the same as {@link #DedicatedScheduledExecutorService(String, Logger, boolean)} but defaults to the
      {@link #DEFAULT_LOGGER} for the log messages and false for the debug flag.<br />
     *
     * @param threadName name of the thread (or null if we want to disable the automatic logging - exceptions will
     *                   always be logged)
     */
    public DedicatedScheduledExecutorService(String threadName) {
        this(threadName, DEFAULT_LOGGER, false);
    }

    /**
     * Does the same as {@link #DedicatedScheduledExecutorService(String, Logger, boolean)} but defaults to {@code null}
     * for the thread name (which causes only error messages to be printed - unless debug is true) and and false for the
     * debug flag.<br />
     * <br />
     * Note: This is for example used by the {@link com.iota.iri.utils.log.interval.IntervalLogger} which does not want
     *       to inform the user when scheduling a log output, but which still needs the "only run one task" logic.<br />
     *
     * @param logger logback logger that shall be used for the origin of the log messages
     */
    public DedicatedScheduledExecutorService(Logger logger) {
        this(null, logger, false);
    }

    /**
     * Does the same as {@link #DedicatedScheduledExecutorService(String, Logger, boolean)} but defaults to {@code null}
     * for the thread name (which causes only error messages to be printed - unless debug is true) and the
     * {@link #DEFAULT_LOGGER} for the log messages.<br />
     * <br />
     * Note: This is for example used by the {@link com.iota.iri.utils.log.interval.IntervalLogger} which does not want
     *       to inform the user when scheduling a log output, but which still needs the "only run one task" logic.<br />
     *
     * @param debug debug flag that indicates if every "run" should be accompanied with a log message
     */
    public DedicatedScheduledExecutorService(boolean debug) {
        this(null, DEFAULT_LOGGER, debug);
    }

    /**
     * Does the same as {@link #DedicatedScheduledExecutorService(String, Logger, boolean)} but defaults to {@code null}
     * for the thread name (which causes only error messages to be printed), the {@link #DEFAULT_LOGGER} for the log
     * messages and {@code false} for the debug flag.<br />
     * <br />
     * Note: This is for example used by the {@link com.iota.iri.utils.log.interval.IntervalLogger} which does not want
     *       to inform the user when scheduling a log output, but which still needs the "only run one task" logic.<br />
     */
    public DedicatedScheduledExecutorService() {
        this(null, DEFAULT_LOGGER, false);
    }

    /**
     * This method is the getter for the name of the thread that gets created by this service.<br />
     *
     * @return it simply returns the private property of {@link #threadName}.
     */
    public String getThreadName() {
        return threadName;
    }

    //region METHODS OF ReportingExecutorService INTERFACE /////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     * This method shows a message whenever a task gets successfully scheduled.<br />
     * <br />
     * We only show the scheduling message if debugging is enabled or a thread name was defined when creating this
     * {@link DedicatedScheduledExecutorService} (to not pollute the CLI with meaningless messages).<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     */
    @Override
    public void onScheduleTask(TaskDetails taskDetails) {
        super.onScheduleTask(taskDetails);

        if (debug || threadName != null) {
            printScheduledMessage(taskDetails);
        }
    }

    /**
     * {@inheritDoc}
     * This method shows a message whenever a task starts to be processed.<br />
     * <br />
     * We only show the starting message if debug is enabled or if it is the first start of the task in a named
     * {@link DedicatedScheduledExecutorService} (display it like it would be a {@link Thread} with one start message
     * and one stop message - to not pollute the CLI with meaningless messages).<br />
     * <br />
     * To increase the information available for debugging, we change the thread name to the one that initiated the
     * start (rather than the randomly assigned one from the executor service) before printing the start message.<br />
     * <br />
     * After the start message was printed, we set the name of the {@link Thread} that will consequently be used for log
     * messages from the task itself.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     */
    @Override
    public void onStartTask(TaskDetails taskDetails) {
        super.onStartTask(taskDetails);

        if (debug || (threadName != null && taskDetails.getExecutionCount().get() == 0)) {
            Thread.currentThread().setName(taskDetails.getThreadName());

            printStartedMessage(taskDetails);
        }

        if (threadName != null) {
            Thread.currentThread().setName(threadName);
        } else {
            Thread.currentThread().setName(getPrintableThreadName(taskDetails));
        }
    }

    /**
     * {@inheritDoc}
     * This method shows a message when the task finishes its execution (can happen multiple times for recurring
     * tasks).<br />
     * <br />
     * We only show the finishing message if debug is enabled and if no error occurred (otherwise the
     * {@link #onCompleteTask(TaskDetails, Throwable)} callback will give enough information about the crash).<br />
     * <br />
     * To be consistent with the start message, we change the thread name to the one that initiated the task (this also
     * makes it easier to distinguish log messages that are emitted by the "real" logic of the task from the "automated"
     * messages about its lifecycle).<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @param error the exception that caused this task to terminate or {@code null} if it terminated normally
     */
    @Override
    public void onFinishTask(TaskDetails taskDetails, Throwable error) {
        super.onFinishTask(taskDetails, error);

        if (debug && error == null) {
            Thread.currentThread().setName(taskDetails.getThreadName());

            printFinishedMessage(taskDetails);
        }
    }

    /**
     * {@inheritDoc}
     * This method shows an information about the intent to cancel the task.<br />
     * <br />
     * We only show the cancel message if debugging is enabled or a thread name was defined when creating this
     * {@link DedicatedScheduledExecutorService} (to not pollute the CLI with meaningless messages).<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     */
    @Override
    public void onCancelTask(TaskDetails taskDetails) {
        super.onCancelTask(taskDetails);

        if (debug || threadName != null) {
            printStopMessage(taskDetails);
        }
    }

    /**
     * {@inheritDoc}
     * This method shows a stopped message whenever it finally terminates (and doesn't get launched again in case of
     * recurring tasks).<br />
     * <br />
     * We only show the stopped message if debug is enabled, an exception occurred (always show unexpected errors) or if
     * we have a named {@link DedicatedScheduledExecutorService} (to not pollute the CLI with meaningless
     * messages).<br />
     * If the completion of the task was not caused by an outside call to cancel it, we change the thread name to the
     * one that initiated the task (this makes it easier to distinguish log messages that are emitted by the "real"
     * logic of the task from the "automated" messages about its lifecycle).<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @param error the exception that caused this task to terminate or {@code null} if it terminated normally
     */
    @Override
    public void onCompleteTask(TaskDetails taskDetails, Throwable error) {
        super.onCompleteTask(taskDetails, error);

        if (debug || threadName != null || error != null) {
            String oldThreadName = Thread.currentThread().getName();
            if (oldThreadName.equals(getPrintableThreadName(taskDetails))) {
                Thread.currentThread().setName(taskDetails.getThreadName());
            }

            printStoppedMessage(taskDetails, error);

            if (!oldThreadName.equals(Thread.currentThread().getName())) {
                Thread.currentThread().setName(oldThreadName);
            }
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region PRIVATE UTILITY METHODS ///////////////////////////////////////////////////////////////////////////////////

    /**
     * This method is a utility method that prints the schedule message of the task.<br />
     * <br />
     * It constructs the matching message by passing the task details into the
     * {@link #buildScheduledMessage(TaskDetails)} method.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     */
    private void printScheduledMessage(TaskDetails taskDetails) {
        logger.info(buildScheduledMessage(taskDetails));
    }

    /**
     * This method is a utility method that prints the started message of the task.<br />
     * <br />
     * It constructs the message by passing the details into the {@link #buildStartedMessage(TaskDetails)} and printing
     * it through the logger.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     */
    private void printStartedMessage(TaskDetails taskDetails) {
        logger.info(buildStartedMessage(taskDetails));
    }

    /**
     * This method is a utility method that prints the finished message of the task.<br />
     * <br />
     * It constructs the message by passing the details into the {@link #buildFinishedMessage(TaskDetails)} and printing
     * it through the logger.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     */
    private void printFinishedMessage(TaskDetails taskDetails) {
        logger.info(buildFinishedMessage(taskDetails));
    }

    /**
     * This method is a utility method that prints the stop message of the task.<br />
     * <br />
     * It constructs the message by passing the details into the {@link #buildStopMessage(TaskDetails)} and printing
     * it through the logger.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     */
    private void printStopMessage(TaskDetails taskDetails) {
        logger.info(buildStopMessage(taskDetails));
    }

    /**
     * This method is a utility method that prints the stopped message of the task.<br />
     * <br />
     * It constructs the message by passing the details into the {@link #buildStoppedMessage(TaskDetails, Throwable)}
     * and printing it through the logger. If an error occurred we use the error channel and append the error to
     * get a stack trace of what happened.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @param error the exception that caused the task to be stopped
     */
    private void printStoppedMessage(TaskDetails taskDetails, Throwable error) {
        String stoppedMessage = buildStoppedMessage(taskDetails, error);
        if (error != null) {
            logger.error(stoppedMessage, error);
        } else {
            logger.info(stoppedMessage);
        }
    }

    /**
     * This method is a utility method that generates the thread name that is used in the log messages.<br />
     * <br />
     * It simply returns the thread name (if one is set) or generates a name if this
     * {@link DedicatedScheduledExecutorService} is "unnamed".<br />
     *
     * @return the thread name that is used in the log messages
     */
    private String getPrintableThreadName(TaskDetails taskDetails) {
        return threadName != null
                ? threadName
                : "UNNAMED THREAD (started by \"" + taskDetails.getThreadName() + "\")";
    }

    /**
     * This method creates the schedule message of the task by first building the temporal parts of the message and
     * then appending them to the actual message.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @return the schedule message that can be used with the logger
     */
    private String buildScheduledMessage(TaskDetails taskDetails) {
        String printableThreadName = getPrintableThreadName(taskDetails);

        String timeoutMessageFragment = buildDelayMessageFragment(taskDetails);
        String intervalMessageFragment = buildIntervalMessageFragment(taskDetails);

        String timeMessageFragment = "";
        if (timeoutMessageFragment != null) {
            timeMessageFragment += " (" + timeoutMessageFragment + (intervalMessageFragment != null ? " / " : ")");
        }
        if (intervalMessageFragment != null) {
            timeMessageFragment += (timeoutMessageFragment == null ? " (" : "") + intervalMessageFragment + ")";
        }

        return "Starting [" + printableThreadName + "]" + timeMessageFragment + " ...";
    }

    /**
     * This method creates the started message of the task by simply extracting the relevant information from the
     * {@code taskDetails} and concatenating them to the actual message.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @return the started message that can be used with the logger
     */
    private String buildStartedMessage(TaskDetails taskDetails) {
        String printableThreadName = getPrintableThreadName(taskDetails);

        return "[" + printableThreadName + "] Started (execution #" + (taskDetails.getExecutionCount().get() + 1) +
                ") ...";
    }

    /**
     * This method creates the finished message of the task by simply extracting the relevant information from the
     * {@code taskDetails} and concatenating them to the actual message.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @return the finished message that can be used with the logger
     */
    private String buildFinishedMessage(TaskDetails taskDetails) {
        String printableThreadName = getPrintableThreadName(taskDetails);

        return "[" + printableThreadName + "] Finished (execution #" + (taskDetails.getExecutionCount().get()) +
                ") ...";
    }

    /**
     * This method creates the stop message of the task by simply extracting the relevant information from the
     * {@code taskDetails} and concatenating them to the actual message.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @return the stop message that can be used with the logger
     */
    private String buildStopMessage(TaskDetails taskDetails) {
        String printableThreadName = getPrintableThreadName(taskDetails);

        return taskDetails.getExecutionCount().get() > 0 ? "Stopping [" + printableThreadName + "] ..."
                                                         : "Cancelling Start [" + printableThreadName + "] ...";
    }

    /**
     * This method creates the stopped message of the task by simply extracting the relevant information from the
     * {@code taskDetails} and concatenating them to the actual message.<br />
     * <br />
     * We differentiate between different termination ways by giving different reasons in the message.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @return the stop message that can be used with the logger
     */
    private String buildStoppedMessage(TaskDetails taskDetails, Throwable error) {
        String printableThreadName = getPrintableThreadName(taskDetails);

        if (error != null) {
            return "[" + printableThreadName + "] Crashed (after #" + taskDetails.getExecutionCount().get() +
                    " executions) ...";
        } else if (taskDetails.getExecutionCount().get() > 0) {
            return "[" + printableThreadName + "] Stopped (after #" + taskDetails.getExecutionCount().get() +
                    " executions) ...";
        } else {
            return "[" + printableThreadName + "] Cancelled Start ...";
        }
    }

    /**
     * This method is a utility method that builds the message fragment which expresses the delay of the scheduled
     * task.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @return the message fragment which expresses the delay of the scheduled task
     */
    private String buildDelayMessageFragment(TaskDetails taskDetails) {
        if (taskDetails.getDelay() == null) {
            return null;
        } else {
            return "starts in " + taskDetails.getDelay() + buildUnitAbbreviation(taskDetails.getTimeUnit());
        }
    }

    /**
     * This method is a utility method that builds the message fragment which expresses the interval of the scheduled
     * task.<br />
     *
     * @param taskDetails metadata holding the relevant information of the task
     * @return the message fragment which expresses the interval of the scheduled task
     */
    private String buildIntervalMessageFragment(TaskDetails taskDetails) {
        if (taskDetails.getInterval() == null) {
            return null;
        } else {
            return "runs every " + taskDetails.getInterval() + buildUnitAbbreviation(taskDetails.getTimeUnit());
        }
    }

    /**
     * This method is a utility method that creates a human readable abbreviation of the provided
     * {@link TimeUnit}.<br />
     *
     * @param unit the time unit used for the values in the {@link TaskDetails}
     * @return a human readable abbreviation of the provided {@link TimeUnit}
     */
    private String buildUnitAbbreviation(TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:  return "ns";
            case MICROSECONDS: return "µs";
            case MILLISECONDS: return "ms";
            case SECONDS:      return "s";
            case MINUTES:      return "min";
            case HOURS:        return "hours";
            case DAYS:         return "days";
            default:           return "";
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
