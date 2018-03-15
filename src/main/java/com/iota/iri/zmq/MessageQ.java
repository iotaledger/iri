package com.iota.iri.zmq;

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

    private final ExecutorService publisherService;
    private final ZMQ.Context context;
    private final ZMQ.Socket publisher;
    private final boolean enabled;

    public MessageQ(int port, String ipc, int nthreads, boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            publisherService = Executors.newSingleThreadExecutor();
            context = ZMQ.context(nthreads);
            publisher = context.socket(ZMQ.PUB);
            publisher.bind(String.format("tcp://*:%d", port));
            if (ipc != null) {
                publisher.bind(ipc);
            }
        } else {
            publisherService = null;
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
        if (enabled) {
            publisherService.shutdown();
            try {
                publisherService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.error("Publisher service shutdown failed.", e);
            }
            publisher.close();
            context.term();
        }
    }
}