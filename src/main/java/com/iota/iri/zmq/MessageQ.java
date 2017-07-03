package com.iota.iri.zmq;

import org.zeromq.ZMQ;

import java.text.MessageFormat;
import java.util.Objects;

/**
 * Created by paul on 6/20/17.
 */
public class MessageQ {
    private final ZMQ.Context context;
    private final ZMQ.Socket publisher;
    private boolean enabled = false;

    public MessageQ(int port, String ipc, int nthreads) {
        context = ZMQ.context(nthreads);
        publisher = context.socket(ZMQ.PUB);
        publisher.bind(String.format("tcp://*:%d", port));
        if(ipc != null) {
            publisher.bind(ipc);
        }
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }


    public void publish(String message, Object... objects) {
        if(enabled) {
            publisher.send(String.format(message, objects));
        }
    }

    public void shutdown () {
        publisher.close();
        context.term();
    }
}
