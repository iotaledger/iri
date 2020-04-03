package com.iota.iri.network.protocol;

import java.nio.ByteBuffer;

/**
 * Defines the information contained in a heartbeat
 */
public class Heartbeat {

    private int firstSolidMilestoneIndex;
    private int lastSolidMilestoneIndex;

    /**
     * Parses the given message into a {@link Heartbeat} object.
     *
     * @param msg the buffer containing the handshake info
     * @return the {@link Heartbeat} object
     */
    public static Heartbeat fromByteBuffer(ByteBuffer msg) {
        Heartbeat heartbeat = new Heartbeat();
        heartbeat.setFirstSolidMilestoneIndex(msg.getInt());
        heartbeat.setLastSolidMilestoneIndex(msg.getInt());
        return heartbeat;
    }

    /**
     * Gets the first solid milestone index of the node
     * 
     * @return The first solid milestone index
     */
    public int getFirstSolidMilestoneIndex() {
        return firstSolidMilestoneIndex;
    }

    /**
     * Sets the first solid milestone index of the node
     * 
     * @param firstSolidMilestoneIndex The first solid milestone index
     */
    public void setFirstSolidMilestoneIndex(int firstSolidMilestoneIndex) {
        this.firstSolidMilestoneIndex = firstSolidMilestoneIndex;
    }

    /**
     * Gets the last solid milestone index of the node
     * 
     * @return The last solid milestone index
     */
    public int getLastSolidMilestoneIndex() {
        return lastSolidMilestoneIndex;
    }

    /**
     * Sets the last solid milestone index
     * 
     * @param lastSolidMilestoneIndex The last solid milestone index
     */
    public void setLastSolidMilestoneIndex(int lastSolidMilestoneIndex) {
        this.lastSolidMilestoneIndex = lastSolidMilestoneIndex;
    }
}
