package com.iota.iri.conf;

public interface ZMQConfig extends Config {

    boolean isZmqEnabled();

    boolean isZmqEnableTcp();

    boolean isZmqEnableIpc();

    int getZmqPort();

    int getZmqThreads();

    String getZmqIpc();

    interface Descriptions {
        String ZMQ_PORT = "The port used to connect to the ZMQ feed";
        String ZMQ_IPC = "The path that is used to communicate with ZMQ in IPC";
        String ZMQ_ENABLED = "Enabling zmq channels (deprecated). Use --zmq-enable-tcp or --zmq-enable-ipc instead";
        String ZMQ_ENABLE_TCP = "Enabling zmq channels on tcp port 5556. Use --zmq-port=[PORT] to override.";
        String ZMQ_ENABLE_IPC = "Enabling zmq channels on ipc://iri. Use --zmq-ipc=[SOCKET] to override.";
    }
}
