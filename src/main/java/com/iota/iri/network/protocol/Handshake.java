package com.iota.iri.network.protocol;

import com.iota.iri.network.neighbor.Neighbor;

import java.nio.ByteBuffer;

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
    private byte[] byteEncodedCooAddress;
    private State state = State.INIT;

    /**
     * Parses the given message into a {@link Handshake} object.
     * 
     * @param msg the buffer containing the handshake info
     * @return the {@link Handshake} object
     */
    public static Handshake fromByteBuffer(ByteBuffer msg) {
        Handshake handshake = new Handshake();
        handshake.setServerSocketPort((int) msg.getChar());
        handshake.setSentTimestamp(msg.getLong());
        byte[] byteEncodedCooAddress = new byte[Protocol.BYTE_ENCODED_COO_ADDRESS_BYTES_LENGTH];
        msg.get(byteEncodedCooAddress);
        handshake.setByteEncodedCooAddress(byteEncodedCooAddress);
        handshake.setState(Handshake.State.OK);
        return handshake;
    }

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

    /**
     * Gets the byte encoded coordinator address.
     * 
     * @return the byte encoded coordinator address
     */
    public byte[] getByteEncodedCooAddress() {
        return byteEncodedCooAddress;
    }

    /**
     * Sets the byte encoded coordinator address.
     * 
     * @param byteEncodedCooAddress the byte encoded coordinator to set
     */
    public void setByteEncodedCooAddress(byte[] byteEncodedCooAddress) {
        this.byteEncodedCooAddress = byteEncodedCooAddress;
    }
}
