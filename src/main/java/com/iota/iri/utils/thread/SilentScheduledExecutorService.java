package com.iota.iri.utils.thread;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * This interface extends the {@link ScheduledExecutorService} by providing additional methods to enqueue tasks without
 * throwing a {@link RejectedExecutionException} exception.<br />
 * <br />
 * This can be useful when preventing additional tasks to run is no error but an intended design decision in the
 * implementing class. In these cases raising an exception and catching it would cause too much unnecessary overhead
 * (using {@link Exception}s for control flow is an anti pattern).<br />
 */
public interface SilentScheduledExecutorService extends ScheduledExecutorService {
    /**
     * Does the same as {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)} but returns {@code null}
     * instead of throwing a {@link RejectedExecutionException} if the task cannot be scheduled for execution.<br />
     *
     * @param command the task to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @return a ScheduledFuture representing pending completion of the task and whose {@code get()} method will return
     *         {@code null} upon completion / {@code null} if the task cannot be scheduled for execution
     * @throws NullPointerException if command is null
     */
    ScheduledFuture<?> silentSchedule(Runnable command, long delay, TimeUnit unit);

    /**
     * Does the same as {@link ScheduledExecutorService#schedule(Callable, long, TimeUnit)} but returns {@code null}
     * instead of throwing a {@link java.util.concurrent.RejectedExecutionException} if the task cannot be scheduled for
     * execution.<br />
     *
     * @param callable the function to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @param <V> the type of the callable's result
     * @return a ScheduledFuture that can be used to extract result or cancel / {@code null} if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if callable is null
     */
    <V> ScheduledFuture<V> silentSchedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * Does the same as {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)} but returns
     * {@code null} instead of throwing a {@link RejectedExecutionException} if the task cannot be scheduled for
     * execution.<br />
     *
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions
     * @param unit the time unit of the initialDelay and period parameters
     * @return a ScheduledFuture representing pending completion of the task, and whose {@code get()} method will throw
     *         an exception upon cancellation / {@code null} if the task cannot be scheduled for execution
     * @throws NullPointerException if command is null
     * @throws IllegalArgumentException if period less than or equal to zero
     */
    ScheduledFuture<?> silentScheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * Does the same as
     * {@link ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)} but returns {@code null}
     * instead of throwing a {@link RejectedExecutionException} if the task cannot be scheduled for execution.<br />
     *
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param delay the delay between the termination of one execution and the commencement of the next
     * @param unit the time unit of the initialDelay and delay parameters
     * @return a ScheduledFuture representing pending completion of the task, and whose {@code get()} method will throw
     *         an exception upon cancellation / {@code null} if the task cannot be scheduled for execution
     * @throws NullPointerException if command is null
     * @throws IllegalArgumentException if delay less than or equal to zero
     */
    ScheduledFuture<?> silentScheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

    /**
     * Does the same as {@link ScheduledExecutorService#submit(Callable)} but returns {@code null} instead of throwing a
     * {@link RejectedExecutionException} if the task cannot be scheduled for execution.<br />
     *
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @return a Future representing pending completion of the task / {@code null} if the task cannot be scheduled for execution
     * @throws NullPointerException if the task is null
     */
    <T> Future<T> silentSubmit(Callable<T> task);

    /**
     * Does the same as {@link ScheduledExecutorService#submit(Runnable)} but returns {@code null} instead of throwing a
     * {@link RejectedExecutionException} if the task cannot be scheduled for execution.<br />
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task / {@code null} if the task cannot be scheduled for
     *         execution
     * @throws NullPointerException if the task is null
     */
    Future<?> silentSubmit(Runnable task);

    /**
     * Does the same as {@link ScheduledExecutorService#submit(Runnable, Object)} but returns {@code null} instead of
     * throwing a {@link RejectedExecutionException} if the task cannot be scheduled for execution.<br />
     *
     * @param task the task to submit
     * @param result the result to return
     * @param <T> the type of the result
     * @return a Future representing pending completion of the task / {@code null} if the task cannot be scheduled for
     *         execution
     * @throws NullPointerException if the task is null
     */
    <T> Future<T> silentSubmit(Runnable task, T result);

    /**
     * Does the same as {@link ScheduledExecutorService#invokeAll(Collection)} but returns {@code null} instead of
     * throwing a {@link RejectedExecutionException} if any task cannot be scheduled for execution.<br />
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same sequential order as produced by the iterator for
     *         the given task list, each of which has completed / {@code null} if any task cannot be scheduled for
     *         execution
     * @throws InterruptedException if interrupted while waiting, in which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks or any of its elements are {@code null}
     */
    <T> List<Future<T>> silentInvokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;

    /**
     * Does the same as {@link ScheduledExecutorService#invokeAll(Collection, long, TimeUnit)} but returns {@code null}
     * instead of throwing a {@link RejectedExecutionException} if any task cannot be scheduled for execution.<br />
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same sequential order as produced by the iterator for
     *         the given task list. If the operation did not time out, each task will have completed. If it did time
     *         out, some of these tasks will not have completed. It returns {@code null} if any task cannot be scheduled
     *         for execution.
     * @throws InterruptedException if interrupted while waiting, in which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks, any of its elements, or unit are {@code null}
     */
    <T> List<Future<T>> silentInvokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException;

    /**
     * Does the same as {@link ScheduledExecutorService#invokeAny(Collection)} but returns {@code null} instead of
     * throwing a {@link RejectedExecutionException} if tasks cannot be scheduled for execution.<br />
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return the result returned by one of the tasks or {@code null} if tasks cannot be scheduled for execution
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks or any element task subject to execution is {@code null}
     * @throws IllegalArgumentException if tasks is empty
     * @throws ExecutionException if no task successfully completes
     */
    <T> T silentInvokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;

    /**
     * Does the same as {@link ScheduledExecutorService#invokeAny(Collection, long, TimeUnit)} but returns {@code null}
     * instead of throwing a {@link RejectedExecutionException} if tasks cannot be scheduled for execution.<br />
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @param <T> the type of the values returned from the tasks
     * @return the result returned by one of the tasks or {@code null} if tasks cannot be scheduled for execution
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks, or unit, or any element task subject to execution is {@code null}
     * @throws TimeoutException if the given timeout elapses before any task successfully completes
     * @throws ExecutionException if no task successfully completes
     */
    <T> T silentInvokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

    /**
     * Does the same as {@link ScheduledExecutorService#execute(Runnable)} but doesn't throw a
     * {@link RejectedExecutionException} if this task cannot be accepted for execution.<br />
     *
     * @param command the runnable task
     * @throws NullPointerException if command is null
     */
    void silentExecute(Runnable command);
}
