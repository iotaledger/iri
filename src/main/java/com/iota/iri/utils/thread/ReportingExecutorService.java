package com.iota.iri.utils.thread;

/**
 * This interface defines a contract for {@link java.util.concurrent.ExecutorService}s that makes them call a
 * "reporting" method whenever an important event occurs.<br />
 * <br />
 * This way a child class extending these kind of {@link java.util.concurrent.ExecutorService}s can "hook" into these
 * events by overriding the specific method and add additional logic like logging or debugging capabilities.<br />
 */
public interface ReportingExecutorService {
    /**
     * This method gets called whenever a new task is scheduled to run.<br />
     * <br />
     * In contrast to {@link #onStartTask(TaskDetails)} this method gets called only once for "recurring" tasks that are
     * scheduled to run in pre-defined intervals.<br />
     *
     * @param taskDetails object containing details about this task
     */
    void onScheduleTask(TaskDetails taskDetails);

    /**
     * This method gets called whenever a task is started.<br />
     * <br />
     * For recurring tasks it is called multiple times.<br />
     *
     * @param taskDetails object containing details about this task
     */
    void onStartTask(TaskDetails taskDetails);

    /**
     * This method gets called whenever a task is finished.<br />
     * <br />
     * For recurring tasks it is called multiple times.<br />
     *
     * @param taskDetails object containing details about this task
     * @param error {@link Exception} that caused the task to complete
     */
    void onFinishTask(TaskDetails taskDetails, Throwable error);

    /**
     * This method gets called whenever a task is cancelled through its {@link java.util.concurrent.Future} or through
     * the shutdown methods of the {@link java.util.concurrent.ExecutorService}.<br />
     * <br />
     * It only gets called once for every task.<br />
     *
     * @param taskDetails object containing details about this task
     */
    void onCancelTask(TaskDetails taskDetails);

    /**
     * This method gets called whenever a task completes.<br />
     * <br />
     * This can be through either raising an exception, cancelling from the outside or by simply terminating in a normal
     * manner. For recurring tasks this only gets called once.<br />
     *
     * @param taskDetails object containing details about this task
     * @param error {@link Exception} that caused the task to complete
     */
    void onCompleteTask(TaskDetails taskDetails, Throwable error);
}
