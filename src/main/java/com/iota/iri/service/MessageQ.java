package com.iota.iri.service;

import org.zeromq.ZMQ;

import java.text.MessageFormat;
import java.util.Objects;

/**
 * Created by paul on 6/20/17.
 */
public class MessageQ {
    private final ZMQ.Context context;
    private final ZMQ.Socket publisher;

    public MessageQ(int port, String ipc) {
        context = ZMQ.context(1);
        publisher = context.socket(ZMQ.PUB);
        publisher.bind(String.format("tcp://*:%d", port));
        if(ipc != null) {
            publisher.bind(ipc);
        }
    }

    public void publish(String message, Object... objects) {
        publisher.send(MessageFormat.format(message, objects));
    }

    public void shutdown () {
        publisher.close();
        context.term();
    }
}
