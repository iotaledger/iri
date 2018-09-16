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
 * Created by paul on 6/20/17.
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

    public void publish(String message, Object... objects) {
        if (enabled) {
            String toSend = String.format(message, objects);
            publisherService.submit(() -> publisher.send(toSend));
        }
    }

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
