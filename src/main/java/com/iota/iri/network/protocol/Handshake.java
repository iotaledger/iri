package com.iota.iri.network.protocol;

public class Handshake {

    public enum State {
        INIT,
        FAILED,
        OK,
    }

    private int serverSocketPort;
    private long sentTimestamp;
    private State state = State.INIT;

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public void setServerSocketPort(int serverSocketPort) {
        this.serverSocketPort = serverSocketPort;
    }

    public int getServerSocketPort() {
        return serverSocketPort;
    }

    public void setSentTimestamp(long sentTimestamp) {
        this.sentTimestamp = sentTimestamp;
    }

    public long getSentTimestamp() {
        return sentTimestamp;
    }
}
