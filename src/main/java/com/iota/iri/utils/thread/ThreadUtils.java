package com.iota.iri.utils.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This class contains a collection of methods that make the management of {@link Thread}s a little bit more convenient.
 *
 * Apart from offering method to spawn and terminate {@link Thread}s, it also offers methods to {@link #sleep(int)}
 * while correctly maintaining the {@link Thread#isInterrupted()} flags.
 */
public class ThreadUtils {
    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger logger = LoggerFactory.getLogger(ThreadUtils.class);

    /**
     * Holds a map of {@link Thread}s that are managed by the {@link ThreadUtils}.
     *
     * This map is used to offer a thread safe way to deal with spawning and stopping {@link Thread}s.
     *
     * @see #spawnThread(Runnable, ThreadIdentifier)
     * @see #stopThread(ThreadIdentifier)
     */
    private static final Map<Object, Thread> threads = new HashMap<>();

    /**
     * This method spawns a new {@link Thread} for the given {@link ThreadIdentifier}.
     *
     * This method is thread safe and makes sure there is exactly one "non-interrupted" {@link Thread} associated to the
     * identifier.
     *
     * It can therefore be used as a convenient way to offer a {@code start()} method in the corresponding manager
     * instance, without having to worry about starting multiple {@link Thread}s when calling the {@code start()} method
     * multiple times.
     *
     * @param runnable method reference that is responsible for processing the thread (i.e. {@code this::myThreadMethod})
     * @param threadIdentifier identifier object that is used to associate the {@link Thread} and synchronize the access
     * @return the thread that got spawned by this method
     */
    public static Thread spawnThread(Runnable runnable, ThreadIdentifier threadIdentifier) {
        if (threads.get(threadIdentifier) == null || threads.get(threadIdentifier).isInterrupted()) {
            synchronized(threadIdentifier) {
                if (threads.get(threadIdentifier) == null || threads.get(threadIdentifier).isInterrupted()) {
                    threads.put(threadIdentifier, spawnThread(runnable, threadIdentifier.getName()));
                }
            }
        }

        return threads.get(threadIdentifier);
    }

    /**
     * This method spawns a new {@link Thread} with the given name.
     *
     * This method does not actively manage the {@link Thread}s and calling it multiple times will create multiple
     * {@link Thread}s. It is internally used by {@link #spawnThread(Runnable, ThreadIdentifier)}.
     *
     * In addition to creating the {@link Thread}, it also dumps some log messages to inform the user about the creation
     * and the lifecycle of the {@link Thread}s.
     *
     * @param runnable method reference that is responsible for processing the thread (i.e. {@code this::threadMethod})
     * @param threadName the name of the {@link Thread} that shall be started started
     * @return the thread that got spawned by this method
     */
    public static Thread spawnThread(Runnable runnable, String threadName) {
        logger.info("Starting Thread: " + threadName + " ...");

        Thread thread = new Thread(() -> {
            logger.info(threadName + " [STARTED]");

            runnable.run();

            logger.info(threadName + " [STOPPED]");
        }, threadName);
        thread.start();

        return thread;
    }

    /**
     * This method stops the {@link Thread} that is associated with the given {@link ThreadIdentifier}.
     *
     * This method is thread safe and only calls the {@link Thread#interrupt()} method once per running {@link Thread}.
     * If no running {@link Thread} is associated with the given {@link ThreadIdentifier}, it will have no affect.
     *
     * It can therefore be used as a convenient way to offer a {@code shutdown()} method in the corresponding manager
     * instance, without having to worry about starting multiple {@link Thread}s when calling the {@code shutdown()}
     * method multiple times.
     *
     * @param threadIdentifier the identifier of the thread that we want to stop
     * @return the {@link Thread} that will be terminated or null if there was no {@link Thread} associated to the given
     *         identifier
     */
    public static Thread stopThread(ThreadIdentifier threadIdentifier) {
        if (threads.get(threadIdentifier) != null && !threads.get(threadIdentifier).isInterrupted()) {
            synchronized(threadIdentifier) {
                if (threads.get(threadIdentifier) != null && !threads.get(threadIdentifier).isInterrupted()) {
                    logger.info("Stopping Thread: " + threadIdentifier.getName() + " ...");

                    threads.get(threadIdentifier).interrupt();
                }
            }
        }

        return threads.get(threadIdentifier);
    }

    /**
     * This method is a wrapper for the {@link Thread#sleep(long)} method, that correctly handles
     * {@link InterruptedException}s and the {@link Thread#interrupted()} flag of the calling {@link Thread}.
     *
     * It first checks if the {@link Thread} was interrupted already and otherwise issues a {@link Thread#sleep(long)}.
     * If a {@link InterruptedException} is caught, it resets the interrupted flag and returns the corresponding result.
     *
     * See <a href="https://dzone.com/articles/how-to-handle-the-interruptedexception">Handle InterruptedException</a>
     *
     * @param timeoutInMS milliseconds that the current Thread should wait
     * @return true if the sleep was successful and false if it got interrupted
     */
    public static boolean sleep(int timeoutInMS) {
        if(Thread.currentThread().isInterrupted()) {
            return false;
        }

        try {
            Thread.sleep(timeoutInMS);

            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return false;
        }
    }
}
