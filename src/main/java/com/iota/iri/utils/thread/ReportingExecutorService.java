package com.iota.iri.utils.thread;

/**
 * <p>
 * This interface defines a contract for {@link java.util.concurrent.ExecutorService}s that makes them call a
 * "reporting" method whenever an important event occurs.
 * </p>
 * <p>
 * This way a child class extending these kind of {@link java.util.concurrent.ExecutorService}s can "hook" into these
 * events by overriding the specific method and add additional logic like logging or debugging capabilities.
 * </p>
 */
public interface ReportingExecutorService {
    /**
     * <p>
     * This method gets called whenever a new task is scheduled to run.
     * </p>
     * <p>
     * In contrast to {@link #onStartTask(TaskDetails)} this method gets called only once for "recurring" tasks that are
     * scheduled to run in pre-defined intervals.
     * </p>
     *
     * @param taskDetails object containing details about this task
     */
    void onScheduleTask(TaskDetails taskDetails);

    /**
     * <p>
     * This method gets called whenever a task is started.
     * </p>
     * <p>
     * For recurring tasks it is called multiple times.
     * </p>
     *
     * @param taskDetails object containing details about this task
     */
    void onStartTask(TaskDetails taskDetails);

    /**
     * <p>
     * This method gets called whenever a task is finished.
     * </p>
     * <p>
     * For recurring tasks it is called multiple times.
     * </p>
     *
     * @param taskDetails object containing details about this task
     * @param error {@link Exception} that caused the task to complete
     */
    void onFinishTask(TaskDetails taskDetails, Throwable error);

    /**
     * <p>
     * This method gets called whenever a task is cancelled through its {@link java.util.concurrent.Future} or through
     * the shutdown methods of the {@link java.util.concurrent.ExecutorService}.
     * </p>
     * <p>
     * It only gets called once for every task.
     * </p>
     *
     * @param taskDetails object containing details about this task
     */
    void onCancelTask(TaskDetails taskDetails);

    /**
     * <p>
     * This method gets called whenever a task completes.
     * </p>
     * <p>
     * This can be through either raising an exception, cancelling from the outside or by simply terminating in a normal
     * manner. For recurring tasks this only gets called once.
     * </p>
     *
     * @param taskDetails object containing details about this task
     * @param error {@link Exception} that caused the task to complete
     */
    void onCompleteTask(TaskDetails taskDetails, Throwable error);
}
