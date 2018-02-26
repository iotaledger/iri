package com.iota.iri.zmq;

import org.zeromq.ZMQ;

/**
 * Created by paul on 6/20/17.
 */
public class MessageQ {
    private final ZMQ.Context context;
    private final ZMQ.Socket publisher;
    private boolean enabled = false;

    public MessageQ(int port, String ipc, int nthreads, boolean enabled) {
        if(enabled) {
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
        if(enabled) {
            publisher.send(String.format(message, objects));
        }
    }

    public void shutdown () {
        publisher.close();
        context.term();
    }
}
