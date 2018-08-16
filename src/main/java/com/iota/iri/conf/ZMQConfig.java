package com.iota.iri.conf;

public interface ZMQConfig extends Config {

    boolean isZmqEnabled();

    int getZmqPort();

    int getZmqThreads();

    String getZmqIpc();

    interface Descriptions {
        String ZMQ_ENABLED = "Enabling zmq channels.";
        String ZMQ_PORT = "The port used to connect to the ZMQ feed";
        String ZMQ_IPC = "The path that is used to communicate with ZMQ in IPC";
    }
}
