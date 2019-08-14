package com.iota.iri.network.protocol;

import com.iota.iri.network.neighbor.Neighbor;

import java.nio.ByteBuffer;

/**
 * Defines information exchanged up on a new connection with a {@link Neighbor}.
 */
public class Handshake {

    /**
     * The amount of bytes used for the coo address sent in a handshake packet.
     */
    public final static int BYTE_ENCODED_COO_ADDRESS_BYTES_LENGTH = 49;

    /**
     * The state of the handshaking.
     */
    public enum State {
        INIT, FAILED, OK,
    }

    private int serverSocketPort;
    private long sentTimestamp;
    private byte[] byteEncodedCooAddress;
    private int mwm;
    private State state = State.INIT;
    private byte[] supportedVersions;

    /**
     * Creates a new handshake packet.
     *
     * @param ownSourcePort the node's own server socket port number
     * @return a {@link ByteBuffer} containing the handshake packet
     */
    public static ByteBuffer createHandshakePacket(char ownSourcePort, byte[] ownByteEncodedCooAddress,
                                                   byte ownUsedMWM) {
        short maxLength = ProtocolMessage.HANDSHAKE.getMaxLength();
        final short payloadLengthBytes = (short) (maxLength - (maxLength - 60) + Protocol.SUPPORTED_PROTOCOL_VERSIONS.length);
        ByteBuffer buf = ByteBuffer.allocate(ProtocolMessage.HEADER.getMaxLength() + payloadLengthBytes);
        Protocol.addProtocolHeader(buf, ProtocolMessage.HANDSHAKE, payloadLengthBytes);
        buf.putChar(ownSourcePort);
        buf.putLong(System.currentTimeMillis());
        buf.put(ownByteEncodedCooAddress);
        buf.put(ownUsedMWM);
        buf.put(Protocol.SUPPORTED_PROTOCOL_VERSIONS);
        buf.flip();
        return buf;
    }

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
        byte[] byteEncodedCooAddress = new byte[BYTE_ENCODED_COO_ADDRESS_BYTES_LENGTH];
        msg.get(byteEncodedCooAddress);
        handshake.setByteEncodedCooAddress(byteEncodedCooAddress);
        handshake.setMWM(msg.get());
        handshake.setState(Handshake.State.OK);
        // extract supported versions
        byte[] supportedVersions = new byte[msg.remaining()];
        msg.get(supportedVersions);
        handshake.setSupportedVersions(supportedVersions);
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

    /**
     * Gets the MWM.
     * 
     * @return the mwm
     */
    public int getMWM() {
        return mwm;
    }

    /**
     * Sets the mwm.
     * 
     * @param mwm the mwm to set
     */
    public void setMWM(int mwm) {
        this.mwm = mwm;
    }

    /**
     * Gets the supported versions.
     * 
     * @return the supported versions
     */
    public byte[] getSupportedVersions() {
        return supportedVersions;
    }

    /**
     * Sets the supported versions.
     * 
     * @param supportedVersions the supported versions to set
     */
    public void setSupportedVersions(byte[] supportedVersions) {
        this.supportedVersions = supportedVersions;
    }

    /**
     * Returns the highest supported protocol version by the neighbor or a negative number indicating the highest
     * protocol version the neighbor would have supported but which our node doesn't.
     * 
     * @param ownSupportedVersions the versions our own node supports
     * @return a positive integer defining the highest supported protocol version and a negative integer indicating the
     *         highest supported version by the given neighbor but which is not supported by us
     */
    public int getNeighborSupportedVersion(byte[] ownSupportedVersions) {
        int highestSupportedVersion = 0;
        for (int i = 0; i < ownSupportedVersions.length; i++) {
            // max check up to advertised versions by the neighbor
            if (i > supportedVersions.length - 1) {
                break;
            }

            // get versions matched by both
            byte supported = (byte) (supportedVersions[i] & ownSupportedVersions[i]);

            // none supported
            if (supported == 0) {
                continue;
            }

            // iterate through all bits and find highest (more to the left is higher)
            int highest = 0;
            for (int j = 0; j < 8; j++) {
                if (((supported >> j) & 1) == 1) {
                    highest = j + 1;
                }
            }
            highestSupportedVersion = highest + (i * 8);
        }

        // if the highest version is still 0, it means that we don't support
        // any protocol version the neighbor supports
        if (highestSupportedVersion == 0) {
            // grab last byte denoting the highest versions.
            // a node will only hold version bytes if at least one version in that
            // byte is supported, therefore it's safe to assume, that the last byte contains
            // the highest supported version of a given node.
            byte lastVersionsByte = supportedVersions[supportedVersions.length - 1];
            // find highest version
            int highest = 0;
            for (int j = 0; j < 8; j++) {
                if (((lastVersionsByte >> j) & 1) == 1) {
                    highest = j + 1;
                }
            }
            int highestSupportedVersionByNeighbor = highest + ((supportedVersions.length - 1) * 8);
            // negate to indicate that we don't actually support it
            return -highestSupportedVersionByNeighbor;
        }

        return highestSupportedVersion;
    }
}
