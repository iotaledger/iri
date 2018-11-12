package com.iota.iri.zmq;

import com.iota.iri.conf.ZMQConfig;
import com.iota.iri.utils.IotaIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Allows publishing of IRI events to a ZeroMQ queue.
 *
 * <p>
 *    In order to be able to watch IRI events, and possibly build tools on top of them, this class
 *    allows the code to publish operational facts to a ZMQ stream.
 * </p>
 * <p>
 *    IRI events are published to this queue prepending the {@code topic} of the event to the body
 *    of the {@code message}. The topic describes the type or the source of the event and is represented by
 *    a short lowercase string.
 *    Some example topics:
 *    <ol>
 *        <li><code>tx</code>: transactions events</li>
 *        <li><code>lm</code>: milestones events</li>
 *        <li><code>dnscu</code>: neighbors' DNS update events</li>
 *    </ol>
 *    To monitor for activity on a specific address, the topic is instead the {@code Address} to watch.
 *    For a complete list and detailed topic specification please refer to the README.md.
 * </p>
 */
public class MessageQ {
    private final static Logger LOG = LoggerFactory.getLogger(MessageQ.class);

    private final ZMQ.Context context;
    private final ZMQ.Socket publisher;
    private boolean enabled = false;

    private final ExecutorService publisherService = Executors.newSingleThreadExecutor();

    public static MessageQ createWith(ZMQConfig config) {
        return new MessageQ(config.getZmqPort(), config.getZmqIpc(), config.getZmqThreads(), config.isZmqEnabled());
    }

    /**
     * Creates and starts a ZMQ publisher.
     *
     * @param port port the publisher will be bound to
     * @param ipc IPC socket the publisher will be bound to
     * @param nthreads number of threads used by the ZMQ publisher
     * @param enabled boolean enable flag; by default the publisher will not be started
     */
    private MessageQ(int port, String ipc, int nthreads, boolean enabled) {
        if (enabled) {
            context = ZMQ.context(nthreads);
            publisher = context.socket(ZMQ.PUB);
            publisher.bind(String.format("tcp://*:%d", port));
            if (ipc != null) {
                publisher.bind(ipc);
            }
            this.enabled = true;
        } else {
            context = null;
            publisher = null;
        }
    }

    /**
     * Publishes an event to the queue
     *
     * @param message message body, prepended by the topic string
     * @param objects arguments referenced by the message body, similar to a format string
     */
    public void publish(String message, Object... objects) {
        if (enabled) {
            String toSend = String.format(message, objects);
            publisherService.submit(() -> publisher.send(toSend));
        }
    }

    /**
     * Gracefully shuts down the ZMQ publisher, forcing after 5 seconds.
     */
    public void shutdown() {
        publisherService.shutdown();

        try {
            publisherService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error("Publisher service shutdown failed.", e);
        }

        IotaIOUtils.closeQuietly(publisher);
        IotaIOUtils.closeQuietly(context);
    }
}
