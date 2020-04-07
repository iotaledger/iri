package com.iota.iri.network.protocol;

import java.nio.ByteBuffer;

/**
 * Defines the information contained in a milestone request
 */
public class MilestoneRequest {

    private int milestoneIndex;

    /**
     * Parses the given message into a {@link MilestoneRequest} object.
     *
     * @param msg the buffer containing the milestone request info
     * @return the {@link MilestoneRequest} object
     */
    public static MilestoneRequest fromByteBuffer(ByteBuffer msg) {
        MilestoneRequest milestoneRequest = new MilestoneRequest();
        milestoneRequest.setMilestoneIndex(msg.getInt());
        return milestoneRequest;
    }

    /**
     * Gets the milestone index of the milestone request
     * 
     * @return The milestone index
     */
    public int getMilestoneIndex() {
        return milestoneIndex;
    }

    /**
     * Sets the milestone index of the milestone request
     * 
     * @param milestoneIndex The milestone index
     */
    public void setMilestoneIndex(int milestoneIndex) {
        this.milestoneIndex = milestoneIndex;
    }
}
