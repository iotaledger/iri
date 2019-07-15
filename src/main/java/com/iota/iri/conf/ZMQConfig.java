package com.iota.iri.conf;

public interface ZMQConfig extends Config {

    /**
     * @return Descriptions#ZMQ_ENABLED
     */
    boolean isZmqEnabled();

    /**
     * @return Descriptions#ZMQ_ENABLE_TCP
     */
    boolean isZmqEnableTcp();

    /**
     * @return Descriptions#ZMQ_ENABLE_IPC
     */
    boolean isZmqEnableIpc();

    /**
     * @return Descriptions#ZMQ_PORT
     */
    int getZmqPort();

    /**
     * @return Descriptions#ZMQ_THREADS
     */
    int getZmqThreads();

    /**
     * @return Descriptions#ZMQ_IPC
     */
    String getZmqIpc();

    interface Descriptions {
        String ZMQ_PORT = "The port used to connect to the ZMQ feed";
        String ZMQ_IPC = "The path that is used to communicate with ZMQ in IPC";
        String ZMQ_ENABLED = "Enable zmq channels (deprecated). Use --zmq-enable-tcp or --zmq-enable-ipc instead";
        String ZMQ_ENABLE_TCP = "Enable zmq channels on tcp port 5556. Use --zmq-port=[PORT] to override.";
        String ZMQ_ENABLE_IPC = "Enable zmq channels on ipc://iri. Use --zmq-ipc=[SOCKET] to override.";
        String ZMQ_THREADS = "The threads used by ZMQ publisher";
    }
}
