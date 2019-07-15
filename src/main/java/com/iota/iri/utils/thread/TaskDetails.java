package com.iota.iri.utils.thread;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>
 * This class represents a container for the metadata of a task that was scheduled through an
 * {@link java.util.concurrent.ExecutorService} that implements the {@link ReportingExecutorService} interface.
 * </p>
 * <p>
 * It can for example be used to show detailed log messages or even implement more sophisticated features like the
 * {@link BoundedScheduledExecutorService}.
 * </p>
 */
public class TaskDetails {
    /**
     * Holds the name of the {@link Thread} that created the task.
     */
    private final String threadName;

    /**
     * Holds a thread-safe flag that indicates if the task is currently scheduled for execution (false if running or
     * done already).
     */
    private final AtomicBoolean scheduledForExecution;

    /**
     * Holds a thread-safe counter for the amount of times this task was executed.
     */
    private final AtomicInteger executionCount;

    /**
     * Holds the initial delay that was provided when scheduling the task (or {@code null} if the task was set to run
     * immediately).
     */
    private Long delay = null;

    /**
     * Holds the interval in which the task is repeated (or {@code null} for non-recurring tasks).
     */
    private Long interval = null;

    /**
     * Holds the timeout that a task can take to terminate before it gets interrupted (or {@code null} if none was
     * provided).
     */
    private Long timeout = null;

    /**
     * Holds the time unit that the other values are denominated in (or {@code null} if no time based values are
     * provided).
     */
    private TimeUnit timeUnit = null;

    /**
     * <p>
     * Creates a container for the metadata of a task that was scheduled through an
     * {@link java.util.concurrent.ExecutorService}.
     * </p>
     * <p>
     * It is automatically initiated with the calling {@link Thread} name, the {@link #scheduledForExecution} flag being
     * set to {@code true} and the {@link #executionCount} being set to 0.
     * </p>
     */
    public TaskDetails() {
        this.threadName = Thread.currentThread().getName();
        this.scheduledForExecution = new AtomicBoolean(true);
        this.executionCount = new AtomicInteger(0);
    }

    /**
     * Getter for the internal {@link #threadName} property.
     *
     * @return name of the {@link Thread} that scheduled the task.
     */
    public String getThreadName() {
        return threadName;
    }

    /**
     * Getter for the internal {@link #scheduledForExecution} property.
     * <p>
     * Note: There is no setter for this property because it returns a mutable object that is not supposed be
     * overwritten.
     * </p>
     *
     * @return a thread-safe flag that indicates if the task is currently scheduled for execution
     */
    public AtomicBoolean getScheduledForExecution() {
        return scheduledForExecution;
    }

    /**
     * Getter for the internal {@link #executionCount} property.
     * <p>
     * Note: There is no setter for this property because it returns a mutable object that is not supposed be
     * overwritten.
     * </p>
     *
     * @return a thread-safe counter for the amount of times this task was executed
     */
    public AtomicInteger getExecutionCount() {
        return executionCount;
    }

    /**
     * Setter for the internal {@link #delay} property.
     *
     * @param delay the initial delay that was provided when scheduling the task
     * @return the instance of TaskDetails itself to allow the chaining of calls
     */
    public TaskDetails setDelay(Long delay) {
        this.delay = delay;

        return this;
    }

    /**
     * Getter for the internal {@link #delay} property.
     *
     * @return the initial delay that was provided when scheduling the task (or {@code null} if the task was set to run
     *         immediately)
     */
    public Long getDelay() {
        return delay;
    }

    /**
     * Setter for the internal {@link #interval} property.
     *
     * @param interval the interval in which the task is repeated (or {@code null} for non-recurring tasks)
     * @return the instance of TaskDetails itself to allow the chaining of calls
     */
    public TaskDetails setInterval(Long interval) {
        this.interval = interval;

        return this;
    }

    /**
     * Getter for the internal {@link #interval} property.
     *
     * @return the interval in which the task is repeated (or {@code null} for non-recurring tasks)
     */
    public Long getInterval() {
        return interval;
    }

    /**
     * Setter for the internal {@link #timeout} property.
     *
     * @param timeout the timeout that a task can take to terminate before it gets interrupted (or {@code null} if none
     *                was provided)
     * @return the instance of TaskDetails itself to allow the chaining of calls
     */
    public TaskDetails setTimeout(Long timeout) {
        this.timeout = timeout;

        return this;
    }

    /**
     * Getter for the internal {@link #timeout} property.
     *
     * @return the timeout that a task can take to terminate before it gets interrupted (or {@code null} if none was
     *         provided)
     */
    public Long getTimeout() {
        return timeout;
    }

    /**
     * Setter for the internal {@link #timeUnit} property.
     *
     * @param timeUnit the time unit that the other values are denominated in (or {@code null} if no time based values
     *                 are provided)
     * @return the instance of TaskDetails itself to allow the chaining of calls
     */
    public TaskDetails setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;

        return this;
    }

    /**
     * Getter for the internal {@link #timeUnit} property.
     *
     * @return the time unit that the other values are denominated in (or {@code null} if no time based values are
     *         provided)
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }
}
