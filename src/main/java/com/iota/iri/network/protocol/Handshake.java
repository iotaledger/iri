package com.iota.iri.network.protocol;

import com.iota.iri.network.neighbor.Neighbor;

/**
 * A {@link Handshake} defines information exchanged up on a new connection with a {@link Neighbor}.
 */
public class Handshake {

    /**
     * The state of the handshaking.
     */
    public enum State {
        INIT, FAILED, OK,
    }

    private int serverSocketPort;
    private long sentTimestamp;
    private State state = State.INIT;

    /**
     * Gets the state of the handshaking.
     * 
     * @return the state
     */
    public State getState() {
        return state;
    }

    /**
     * Sets the state of the handshaking.
     * 
     * @param state the state to set
     */
    public void setState(State state) {
        this.state = state;
    }

    /**
     * Sets the server socket port number.
     * 
     * @param serverSocketPort the number to set
     */
    public void setServerSocketPort(int serverSocketPort) {
        this.serverSocketPort = serverSocketPort;
    }

    /**
     * Gets the server socket port number.
     * 
     * @return the server socket port number.
     */
    public int getServerSocketPort() {
        return serverSocketPort;
    }

    /**
     * Sets the sent timestamp.
     * 
     * @param sentTimestamp the timestamp
     */
    public void setSentTimestamp(long sentTimestamp) {
        this.sentTimestamp = sentTimestamp;
    }

    /**
     * Gets the sent timestamp.
     * 
     * @return the sent timestamp
     */
    public long getSentTimestamp() {
        return sentTimestamp;
    }
}
