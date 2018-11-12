package com.iota.iri.utils.thread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents a {@link SilentScheduledExecutorService} that accepts only a pre-defined amount of tasks that
 * can be queued or executed at the same time. All tasks exceeding the defined limit will be ignored (instead of being
 * queued) by either throwing a {@link RejectedExecutionException} or returning {@code null} depending on the method we
 * call (non-silent vs silent).<br />
 * <br />
 * Whenever a non-recurring task finishes (or a recurring one is cancelled through its {@link Future}), it makes space
 * for a new task. This is useful for classes like the {@link com.iota.iri.utils.log.interval.IntervalLogger} that want
 * to delay an action if and only if there is no other delayed action queued already.<br />
 * <br />
 * Note: In contrast to other existing implementations like the SizedScheduledExecutorService of the apache package,
 *       this class is thread-safe and will only allow to spawn and queue the exact amount of tasks defined during its
 *       creation (since it does not rely on approximate numbers like the queue size).<br />
 */
public class BoundedScheduledExecutorService implements SilentScheduledExecutorService, ReportingExecutorService {
    /**
     * Holds the maximum amount of tasks that can be submitted for execution.<br />
     */
    private final int capacity;

    /**
     * Holds the underlying {@link ScheduledExecutorService} that manages the Threads in the background.<br />
     */
    private final ScheduledExecutorService delegate;

    /**
     * Holds a set of scheduled tasks tasks that are going to be executed by this
     * {@link ScheduledExecutorService}.<br />
     */
    private final Set<TaskDetails> scheduledTasks = ConcurrentHashMap.newKeySet();

    /**
     * Thread-safe counter that is used to determine how many tasks were exactly scheduled already.<br />
     * <br />
     * Note: Whenever a task finishes, we clean up the used resources and make space for new tasks.<br />
     */
    private AtomicInteger scheduledTasksCounter = new AtomicInteger(0);

    /**
     * Creates an executor service that that accepts only a pre-defined amount of tasks that can be queued and run at
     * the same time.<br />
     * <br />
     * All tasks exceeding the defined limit will be ignored (instead of being queued) by either throwing a
     * {@link RejectedExecutionException} or returning {@code null} depending on the method we call (non-silent vs
     * silent).<br />
     *
     * @param capacity the amount of tasks that can be scheduled simultaneously
     */
    public BoundedScheduledExecutorService(int capacity) {
        this.capacity = capacity;

        delegate = Executors.newScheduledThreadPool(capacity);
    }

    //region METHODS OF ReportingExecutorService INTERFACE /////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     * <br />
     * It simply adds the task to the internal set of scheduled tasks.<br />
     */
    @Override
    public void onScheduleTask(TaskDetails taskDetails) {
        scheduledTasks.add(taskDetails);
    }

    @Override
    public void onStartTask(TaskDetails taskDetails) {
        // doesn't do anything but allows a child class to hook into this event by overriding this method
    }

    @Override
    public void onFinishTask(TaskDetails taskDetails, Throwable error) {
        // doesn't do anything but allows a child class to hook into this event by overriding this method
    }

    @Override
    public void onCancelTask(TaskDetails taskDetails) {
        // doesn't do anything but allows a child class to hook into this event by overriding this method
    }

    /**
     * {@inheritDoc}
     * <br />
     * It frees the reserved resources by decrementing the {@link #scheduledTasksCounter} and removing the task from the
     * {@link #scheduledTasks} set.<br />
     */
    @Override
    public void onCompleteTask(TaskDetails taskDetails, Throwable error) {
        scheduledTasks.remove(taskDetails);
        scheduledTasksCounter.decrementAndGet();
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region METHODS OF SilentScheduledExecutorService INTERFACE ///////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link ScheduledFuture} returned by this method allows to cancel jobs without their unwinding
     *       logic being executed, we wrap the returned {@link ScheduledFuture} AND the {@link Runnable} to correctly
     *       free the resources if its {@link ScheduledFuture#cancel(boolean)} method is called.<br />
     */
    @Override
    public ScheduledFuture<?> silentSchedule(Runnable task, long delay, TimeUnit unit) {
        return reserveCapacity(1) ? wrapScheduledFuture(
                wrappedTask -> delegate.schedule(wrappedTask, delay, unit),
                task,
                new TaskDetails()
                        .setDelay(delay)
                        .setTimeUnit(unit)
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link ScheduledFuture} returned by this method allows to cancel jobs without their unwinding
     *       logic being executed, we wrap the returned {@link ScheduledFuture} AND the {@link Callable} to correctly
     *       free the resources if its {@link ScheduledFuture#cancel(boolean)} method is called.<br />
     */
    @Override
    public <V> ScheduledFuture<V> silentSchedule(Callable<V> task, long delay, TimeUnit unit) {
        return reserveCapacity(1) ? wrapScheduledFuture(
                wrappedTask -> delegate.schedule(wrappedTask, delay, unit),
                task,
                new TaskDetails()
                        .setDelay(delay)
                        .setTimeUnit(unit)
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link ScheduledFuture} returned by this method allows to cancel jobs without their unwinding
     *       logic being executed, we wrap the returned {@link ScheduledFuture} AND the {@link Runnable} to correctly
     *       free the resources if its {@link ScheduledFuture#cancel(boolean)} method is called.<br />
     */
    @Override
    public ScheduledFuture<?> silentScheduleAtFixedRate(Runnable task, long initialDelay, long period,
            TimeUnit unit) {

        return reserveCapacity(1) ? wrapScheduledFuture(
                wrappedTask -> delegate.scheduleAtFixedRate(wrappedTask, initialDelay, period, unit),
                task,
                new TaskDetails()
                        .setDelay(initialDelay)
                        .setInterval(period)
                        .setTimeUnit(unit)
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link ScheduledFuture} returned by this method allows to cancel jobs without their unwinding
     *       logic being executed, we wrap the returned {@link ScheduledFuture} AND the {@link Runnable} to correctly
     *       free the resources if its {@link ScheduledFuture#cancel(boolean)} method is called.<br />
     */
    @Override
    public ScheduledFuture<?> silentScheduleWithFixedDelay(Runnable task, long initialDelay, long delay,
            TimeUnit unit) {

        return reserveCapacity(1) ? wrapScheduledFuture(
                wrappedTask -> delegate.scheduleWithFixedDelay(wrappedTask, initialDelay, delay, unit),
                task,
                new TaskDetails()
                        .setDelay(initialDelay)
                        .setInterval(delay)
                        .setTimeUnit(unit)
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link Future} returned by this method allows to cancel jobs without their unwinding logic being
     *       executed, we wrap the returned {@link Future} AND the {@link Callable} to correctly free the resources if
     *       its {@link Future#cancel(boolean)} method is called.<br />
     */
    @Override
    public <T> Future<T> silentSubmit(Callable<T> task) {
        return reserveCapacity(1) ? wrapFuture(
                delegate::submit,
                task,
                new TaskDetails()
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link Future} returned by this method allows to cancel jobs without their unwinding logic being
     *       executed, we wrap the returned {@link Future} AND the {@link Runnable} to correctly free the resources if
     *       its {@link Future#cancel(boolean)} method is called.<br />
     */
    @Override
    public Future<?> silentSubmit(Runnable task) {
        return reserveCapacity(1) ? wrapFuture(
                delegate::submit,
                task,
                new TaskDetails()
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link Future} returned by this method allows to cancel jobs without their unwinding logic being
     *       executed, we wrap the returned {@link Future} AND the {@link Runnable} to correctly free the resources if
     *       its {@link Future#cancel(boolean)} method is called.<br />
     */
    @Override
    public <T> Future<T> silentSubmit(Runnable task, T result) {
        return reserveCapacity(1) ? wrapFuture(
                wrappedTask -> delegate.submit(wrappedTask, result),
                task,
                new TaskDetails()
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link Future}s will all be finished (and cannot be cancelled anymore) when the underlying method
     *       returns, we only wrap the {@link Callable}s to correctly free the resources and omit wrapping the returned
     *       {@link Future}s.<br />
     */
    @Override
    public <T> List<Future<T>> silentInvokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return reserveCapacity(tasks.size()) ? delegate.invokeAll(wrapTasks(tasks, new TaskDetails())) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link Future}s will all be finished (and cannot be cancelled anymore) when the underlying method
     *       returns, we only wrap the {@link Callable}s to correctly free the resources and omit wrapping the returned
     *       {@link Future}s.<br />
     */
    @Override
    public <T> List<Future<T>> silentInvokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {

        return reserveCapacity(tasks.size()) ? delegate.invokeAll(
                wrapTasks(
                        tasks,
                        new TaskDetails()
                                .setTimeout(timeout)
                                .setTimeUnit(unit)
                ),
                timeout,
                unit
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link Future}s are not passed to the caller, we only wrap the {@link Callable}s to correctly
     *       free the resources and omit wrapping the {@link Future}s as well.<br />
     */
    @Override
    public <T> T silentInvokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException,
            ExecutionException {

        return reserveCapacity(tasks.size()) ? delegate.invokeAny(wrapTasks(tasks, new TaskDetails())) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since the {@link Future}s are not passed to the caller, we only wrap the {@link Callable}s to correctly
     *       free the resources and omit wrapping the related {@link Future}s as well.<br />
     */
    @Override
    public <T> T silentInvokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws
            InterruptedException, ExecutionException, TimeoutException {

        return reserveCapacity(tasks.size()) ? delegate.invokeAny(
                wrapTasks(
                        tasks,
                        new TaskDetails()
                                .setTimeout(timeout)
                                .setTimeUnit(unit)
                ),
                timeout,
                unit
        ) : null;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Note: Since there is no {@link Future} passed to the caller, we only wrap the {@link Runnable} to correctly free
     *       the resources.<br />
     */
    @Override
    public void silentExecute(Runnable task) {
        if (reserveCapacity(1)) {
            delegate.execute(wrapTask(task, new TaskDetails()));
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region METHODS OF ScheduledExecutorService INTERFACE /////////////////////////////////////////////////////////////

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return throwCapacityExhaustedIfNull(silentSchedule(command, delay, unit));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return throwCapacityExhaustedIfNull(silentSchedule(callable, delay, unit));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return throwCapacityExhaustedIfNull(silentScheduleAtFixedRate(command, initialDelay, period, unit));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return throwCapacityExhaustedIfNull(silentScheduleWithFixedDelay(command, initialDelay, delay, unit));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return throwCapacityExhaustedIfNull(silentSubmit(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return throwCapacityExhaustedIfNull(silentSubmit(task, result));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return throwCapacityExhaustedIfNull(silentSubmit(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return throwCapacityExhaustedIfNull(silentInvokeAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {

        return throwCapacityExhaustedIfNull(silentInvokeAll(tasks, timeout, unit));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return throwCapacityExhaustedIfNull(silentInvokeAny(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws
            InterruptedException, ExecutionException, TimeoutException {

        return throwCapacityExhaustedIfNull(silentInvokeAny(tasks, timeout, unit));
    }

    @Override
    public void execute(Runnable command) {
        if (reserveCapacity(1)) {
            delegate.execute(wrapTask(command, new TaskDetails()));
        } else {
            throw new RejectedExecutionException("the capacity is exhausted");
        }
    }

    /**
     * {@inheritDoc}
     * <br />
     * In addition to delegating the method call to the internal {@link ScheduledExecutorService}, we call the cancel
     * logic for recurring tasks because shutdown prevents them from firing again. If these "cancelled" jobs are
     * scheduled for execution (and not running right now), we also call their
     * {@link #onCompleteTask(TaskDetails, Throwable)} callback to "report" that hey have finished (otherwise this will
     * be fired inside the wrapped task).<br />
     */
    @Override
    public void shutdown() {
        delegate.shutdown();

        for (TaskDetails currentTask : scheduledTasks) {
            if (currentTask.getInterval() != null) {
                onCancelTask(currentTask);

                if (currentTask.getScheduledForExecution().compareAndSet(true, false)) {
                    onCompleteTask(currentTask, null);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <br />
     * Before delegating the method call to the internal {@link ScheduledExecutorService}, we call the
     * {@link #onCancelTask(TaskDetails)} callback for all scheduled tasks and fire the
     * {@link #onCompleteTask(TaskDetails, Throwable)} callback for all tasks that are not being executed right now
     * (otherwise this will be fired inside the wrapped task).<br />
     */
    @Override
    public List<Runnable> shutdownNow() {
        if (!delegate.isShutdown()) {
            for (TaskDetails currentTask : scheduledTasks) {
                onCancelTask(currentTask);

                if (currentTask.getScheduledForExecution().compareAndSet(true, false)) {
                    onCompleteTask(currentTask, null);
                }
            }
        }

        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region PRIVATE UTILITY METHODS AND MEMBERS ///////////////////////////////////////////////////////////////////////

    /**
     * This interface is used to generically describe the lambda that is used to create the unwrapped future from the
     * delegated {@link ScheduledExecutorService} by passing in the wrapped command.<br />
     *
     * @param <RESULT> the kind of future returned by this factory ({@link ScheduledFuture} vs {@link Future})
     * @param <ARGUMENT> type of the wrapped command that is passed in ({@link Runnable} vs {@link Callable})
     */
    @FunctionalInterface
    private interface FutureFactory<RESULT, ARGUMENT> {
        /**
         * This method creates the unwrapped future from the wrapped task by passing it on to the delegated
         * {@link ScheduledExecutorService}.
         *
         * @param task the wrapped task that shall be scheduled
         * @return the unwrapped "original" {@link Future} of the delegated {@link ScheduledExecutorService}
         */
        RESULT create(ARGUMENT task);
    }

    /**
     * This is a wrapper for the {@link Future}s returned by the {@link ScheduledExecutorService} that allows us to
     * override the behaviour of the {@link #cancel(boolean)} method (to be able to free the resources of a task that
     * gets cancelled without being executed).<br />
     *
     * @param <V> the type of the result returned by the task that is the origin of this {@link Future}.
     */
    private class WrappedFuture<V> implements Future<V> {
        /**
         * Holds the metadata of the task that this {@link Future} belongs to.<br />
         */
        protected final TaskDetails taskDetails;

        /**
         * "Original" unwrapped {@link Future} that is returned by the delegated {@link ScheduledExecutorService}.<br />
         * <br />
         * Note: All methods except the {@link #cancel(boolean)} are getting passed through without any
         *       modifications.<br />
         */
        private Future<V> delegate;

        /**
         * This creates a {@link WrappedFuture} that cleans up the reserved resources when it is cancelled while the
         * task is still pending.<br />
         * <br />
         * We do not hand in the {@link #delegate} in the constructor because we need to populate this instance to the
         * wrapped task before we "launch" the processing of the task (see {@link #delegate(Future)}).<br />
         *
         * @param taskDetails metadata holding the relevant information of the task
         */
        public WrappedFuture(TaskDetails taskDetails) {
            this.taskDetails = taskDetails;
        }

        /**
         * This method stores the delegated {@link Future} in its internal property.<br />
         * <br />
         * After the delegated {@link Future} is created, the underlying {@link ScheduledExecutorService} starts
         * processing the task. To be able to "address" this wrapped future before we start processing the task (the
         * wrapped task needs to access it), we populate this lazy (see
         * {@link #wrapFuture(FutureFactory, Runnable, TaskDetails)} and
         * {@link #wrapFuture(FutureFactory, Callable, TaskDetails)}).<br />
         *
         * @param delegatedFuture the "original" future that handles the logic in the background
         * @return the instance itself (since we want to return the {@link WrappedFuture} after launching the underlying
         *         processing).
         */
        public Future<V> delegate(Future<V> delegatedFuture) {
            this.delegate = delegatedFuture;

            return this;
        }

        /**
         * This method returns the delegated future.<br />
         * <br />
         * We define a getter for this property to be able to override it in the extending class and achieve a
         * polymorphic behavior.<br />
         *
         * @return the original "unwrapped" {@link Future} that is used as a delegate for the methods of this class
         */
        public Future<V> delegate() {
            return delegate;
        }

        /**
         * {@inheritDoc}
         * <br />
         * This method fires the {@link #onCancelTask(TaskDetails)} if the future has not been cancelled before.
         * Afterwards it also fires the {@link #onCompleteTask(TaskDetails, Throwable)} callback if the task is not
         * running right now (otherwise this will be fired inside the wrapped task).<br />
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!delegate().isCancelled() && !delegate().isDone()) {
                onCancelTask(taskDetails);
            }

            if (taskDetails.getScheduledForExecution().compareAndSet(true, false)) {
                onCompleteTask(taskDetails, null);
            }

            return delegate().cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate().isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate().isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return delegate().get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate().get(timeout, unit);
        }
    }

    /**
     * This is a wrapper for the {@link ScheduledFuture}s returned by the {@link ScheduledExecutorService} that allows
     * us to override the behaviour of the {@link #cancel(boolean)} method (to be able to free the resources of a task
     * that gets cancelled without being executed).<br />
     *
     * @param <V> the type of the result returned by the task that is the origin of this {@link ScheduledFuture}.
     */
    private class WrappedScheduledFuture<V> extends WrappedFuture<V> implements ScheduledFuture<V> {
        /**
         * "Original" unwrapped {@link ScheduledFuture} that is returned by the delegated
         * {@link ScheduledExecutorService}.<br />
         * <br />
         * Note: All methods except the {@link #cancel(boolean)} are getting passed through without any modifications.
         */
        private ScheduledFuture<V> delegate;

        /**
         * This creates a {@link ScheduledFuture} that cleans up the reserved resources when it is cancelled while the
         * task is still pending.<br />
         * <br />
         * We do not hand in the {@link #delegate} in the constructor because we need to populate this instance to the
         * wrapped task before we "launch" the processing of the task (see {@link #delegate(Future)}).
         *
         * @param taskDetails metadata holding the relevant information of the task
         */
        private WrappedScheduledFuture(TaskDetails taskDetails) {
            super(taskDetails);
        }

        /**
         * This method stores the delegated {@link ScheduledFuture} in its internal property.<br />
         * <br />
         * After the delegated {@link ScheduledFuture} is created, the underlying {@link ScheduledExecutorService}
         * starts processing the task. To be able to "address" this wrapped future before we start processing the task
         * (the wrapped task needs to access it), we populate this lazy (see
         * {@link #wrapScheduledFuture(FutureFactory, Runnable, TaskDetails)}).<br />
         *
         * @param delegatedFuture the "original" future that handles the logic in the background
         * @return the instance itself (since we want to return the {@link WrappedFuture} immediately after launching
         *         the underlying processing).
         */
        public ScheduledFuture<V> delegate(ScheduledFuture<V> delegatedFuture) {
            this.delegate = delegatedFuture;

            return this;
        }

        @Override
        public ScheduledFuture<V> delegate() {
            return delegate;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return delegate().getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return delegate().compareTo(o);
        }

        /**
         * {@inheritDoc}
         * <br />
         * This method fires the {@link #onCancelTask(TaskDetails)} if the future has not been cancelled before.
         * Afterwards it also fires the {@link #onCompleteTask(TaskDetails, Throwable)} callback if the task is not
         * running right now (otherwise this will be fired inside the wrapped task).<br />
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!delegate().isCancelled() && !delegate().isDone()) {
                onCancelTask(taskDetails);
            }

            if (taskDetails.getScheduledForExecution().compareAndSet(true, false)) {
                onCompleteTask(taskDetails, null);
            }

            return delegate().cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate().isCancelled();
        }
    }

    /**
     * This method wraps the passed in task to automatically call the callbacks for its lifecycle.<br />
     * <br />
     * The lifecycle methods are for example used to manage the resources that are reserved for this task.<br />
     *
     * @param task the raw task that shall be wrapped with the resource freeing logic
     * @param taskDetails metadata holding the relevant information of the task
     * @param future the wrapped {@link Future} that this task belongs to - it allows us to check if the task was
     *               cancelled from the outside
     * @param <V> the return type of the task
     * @return a wrapped task that cleans up its reserved resources upon completion
     */
    private <V> Callable<V> wrapTask(Callable<V> task, TaskDetails taskDetails, Future<V> future) {
        onScheduleTask(taskDetails);

        return () -> {
            if (taskDetails.getScheduledForExecution().compareAndSet(true, false)) {
                Throwable error = null;
                try {
                    onStartTask(taskDetails);
                    taskDetails.getExecutionCount().incrementAndGet();

                    return task.call();
                } catch (Exception e) {
                    error = e;

                    throw e;
                } finally {
                    onFinishTask(taskDetails, error);

                    if (taskDetails.getInterval() == null || error != null || future == null || future.isCancelled() ||
                            delegate.isShutdown()) {

                        onCompleteTask(taskDetails, error);
                    } else {
                        taskDetails.getScheduledForExecution().set(true);
                    }
                }
            }

            return null;
        };
    }

    /**
     * This method does the same as {@link #wrapTask(Callable, TaskDetails, Future)} but defaults to a task that is not
     * associated to a user accessible {@link Future}.<br />
     * <br />
     * This method is used whenever the {@link Future} is not returned by the underlying method so we don't need to wrap
     * it.<br />
     *
     * @param task the raw task that shall be wrapped with the resource freeing logic
     * @param taskDetails metadata holding the relevant information of the task
     * @param <V> the return type of the task
     * @return a wrapped task that cleans up its reserved resources upon completion
     */
    private <V> Callable<V> wrapTask(Callable<V> task, TaskDetails taskDetails) {
        return wrapTask(task, taskDetails, null);
    }

    /**
     * This method wraps the passed in task to automatically call the callbacks for its lifecycle.<br />
     * <br />
     * The lifecycle methods are for example used to manage the resources that are reserved for this task.<br />
     *
     * @param task the raw task that shall be wrapped with the resource freeing logic
     * @param taskDetails metadata holding the relevant information of the task
     * @param future the wrapped {@link Future} that this task belongs to - it allows us to check if the task was
     *               cancelled from the outside
     * @param <V> the return type of the task
     * @return a wrapped task that cleans up its reserved resources upon completion
     */
    private <V> Runnable wrapTask(Runnable task, TaskDetails taskDetails, Future<V> future) {
        onScheduleTask(taskDetails);

        return () -> {
            if (taskDetails.getScheduledForExecution().compareAndSet(true, false)) {
                Throwable error = null;
                try {
                    onStartTask(taskDetails);
                    taskDetails.getExecutionCount().incrementAndGet();

                    task.run();
                } catch (Exception e) {
                    error = e;

                    throw e;
                } finally {
                    onFinishTask(taskDetails, error);

                    if (taskDetails.getInterval() == null || error != null || future == null || future.isCancelled() ||
                            delegate.isShutdown()) {

                        onCompleteTask(taskDetails, error);
                    } else {
                        taskDetails.getScheduledForExecution().set(true);
                    }
                }
            }
        };
    }

    /**
     * This method does the same as {@link #wrapTask(Runnable, TaskDetails, Future)} but defaults to a task that is not
     * associated to a user accessible {@link Future}.<br />
     * <br />
     * This method is used whenever the {@link Future} is not returned by the underlying method so we don't need to wrap
     * it.<br />
     *
     * @param task the raw task that shall be wrapped with the resource freeing logic
     * @param taskDetails metadata holding the relevant information of the task
     * @return a wrapped task that cleans up its reserved resources upon completion
     */
    private Runnable wrapTask(Runnable task, TaskDetails taskDetails) {
        return wrapTask(task, taskDetails, null);
    }

    /**
     * This is a utility method that wraps the task and the resulting {@link Future} in a single call.<br />
     * <br />
     * It creates the {@link WrappedFuture} and the wrapped task and starts the execution of the task by delegating
     * the launch through the {@link FutureFactory}.<br />
     *
     * @param futureFactory the lambda that returns the original "unwrapped" future from the wrapped task
     * @param task the task that shall be wrapped to clean up its reserved resources upon completion
     * @param taskDetails metadata holding the relevant information of the task
     * @param <V> the return type of the task
     * @return the {@link WrappedFuture} that allows to interact with the task
     */
    private <V> Future<V> wrapFuture(FutureFactory<Future<V>, Callable<V>> futureFactory, Callable<V> task,
            TaskDetails taskDetails) {

        WrappedFuture<V> wrappedFuture = new WrappedFuture<>(taskDetails);
        Callable<V> wrappedCallable = wrapTask(task, taskDetails, wrappedFuture);

        return wrappedFuture.delegate(futureFactory.create(wrappedCallable));
    }

    /**
     * This is a utility method that wraps the task and the resulting {@link Future} in a single call.<br />
     * <br />
     * It creates the {@link WrappedFuture} and the wrapped task and starts the execution of the task by delegating
     * the launch through the {@link FutureFactory}.<br />
     *
     * @param futureFactory the lambda that returns the original "unwrapped" future from the wrapped task
     * @param task the task that shall be wrapped to clean up its reserved resources upon completion
     * @param taskDetails metadata holding the relevant information of the task
     * @param <V> the return type of the task
     * @return the {@link WrappedFuture} that allows to interact with the task
     */
    private <V> Future<V> wrapFuture(FutureFactory<Future<V>, Runnable> futureFactory, Runnable task,
            TaskDetails taskDetails) {

        WrappedFuture<V> wrappedFuture = new WrappedFuture<>(taskDetails);
        Runnable wrappedTask = wrapTask(task, taskDetails, wrappedFuture);

        return wrappedFuture.delegate(futureFactory.create(wrappedTask));
    }

    /**
     * This is a utility method that wraps the task and the resulting {@link ScheduledFuture} in a single call.<br />
     * <br />
     * It creates the {@link WrappedScheduledFuture} and the wrapped task and starts the execution of the task by
     * delegating the launch through the {@link FutureFactory}.<br />
     *
     * @param futureFactory the lambda that returns the original "unwrapped" future from the wrapped task
     * @param task the task that shall be wrapped to clean up its reserved resources upon completion
     * @param taskDetails metadata holding the relevant information of the task
     * @param <V> the return type of the task
     * @return the {@link WrappedFuture} that allows to interact with the task
     */
    private <V> ScheduledFuture<V> wrapScheduledFuture(FutureFactory<ScheduledFuture<V>, Callable<V>> futureFactory,
            Callable<V> task, TaskDetails taskDetails) {

        WrappedScheduledFuture<V> wrappedFuture = new WrappedScheduledFuture<>(taskDetails);
        Callable<V> wrappedCallable = wrapTask(task, taskDetails, wrappedFuture);

        return wrappedFuture.delegate(futureFactory.create(wrappedCallable));
    }

    /**
     * This is a utility method that wraps the task and the resulting {@link ScheduledFuture} in a single call.<br />
     * <br />
     * It creates the {@link WrappedScheduledFuture} and the wrapped task and starts the execution of the task by
     * delegating the launch through the {@link FutureFactory}.<br />
     *
     * @param futureFactory the lambda that returns the original "unwrapped" future from the wrapped task
     * @param task the task that shall be wrapped to clean up its reserved resources upon completion
     * @param taskDetails metadata holding the relevant information of the task
     * @param <V> the return type of the task
     * @return the {@link WrappedFuture} that allows to interact with the task
     */
    private <V> ScheduledFuture<V> wrapScheduledFuture(FutureFactory<ScheduledFuture<V>, Runnable> futureFactory,
            Runnable task, TaskDetails taskDetails) {

        WrappedScheduledFuture<V> wrappedFuture = new WrappedScheduledFuture<>(taskDetails);
        Runnable wrappedRunnable = wrapTask(task, taskDetails, wrappedFuture);

        return wrappedFuture.delegate(futureFactory.create(wrappedRunnable));
    }

    /**
     * This is a utility method that wraps a {@link Collection} of {@link Callable}s to make them free their resources
     * upon completion.<br />
     * <br />
     * It simply iterates over the tasks and wraps them one by one by calling
     * {@link #wrapTask(Callable, TaskDetails)}.<br />
     *
     * @param tasks list of jobs that shall be wrapped
     * @param <T> the type of the values returned by the {@link Callable}s
     * @return wrapped list of jobs that will free their reserved resources upon completion
     */
    private <T> Collection<? extends Callable<T>> wrapTasks(Collection<? extends Callable<T>> tasks,
            TaskDetails taskDetails) {

        List<Callable<T>> wrappedTasks = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            wrappedTasks.add(wrapTask(task, taskDetails));
        }

        return wrappedTasks;
    }

    /**
     * This method checks if we have enough resources to schedule the given amount of tasks and reserves the space if
     * the check is successful.<br />
     * <br />
     * The reserved resources will be freed again once the tasks finish their execution or when they are cancelled
     * through their corresponding {@link Future}.<br />
     *
     * @param requestedJobCount the amount of tasks that shall be scheduled
     * @return true if we could reserve the given space and false otherwise
     */
    private boolean reserveCapacity(int requestedJobCount) {
        if (scheduledTasksCounter.addAndGet(requestedJobCount) <= capacity) {
            return true;
        } else {
            scheduledTasksCounter.addAndGet(-requestedJobCount);

            return false;
        }
    }

    /**
     * This method is a utility method that simply checks the passed in object for being {@code null} and throws a
     * {@link RejectedExecutionException} if it is.<br />
     * <br />
     * It is used to turn the silent methods into non-silent ones and conform with the {@link ScheduledExecutorService}
     * interface.<br />
     *
     * @param result the object that shall be checked for being null (the result of a "silent" method call)
     * @param <V> the type of the result
     * @return the passed in object (if it is not null)
     * @throws RejectedExecutionException if the passed in object was null
     */
    private <V> V throwCapacityExhaustedIfNull(V result) throws RejectedExecutionException {
        if (result == null) {
            throw new RejectedExecutionException("the capacity is exhausted");
        }

        return result;
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
