package com.iota.iri.service.dto;

/**
 * This class represents the core API error for accessing a command which is limited by this Node.
 */
public class GetBlocksInPeriodResponse extends AbstractResponse {

    private String blocks;

    public static AbstractResponse create(String blocks) {
        GetBlocksInPeriodResponse res = new GetBlocksInPeriodResponse();
        res.blocks = blocks;
        return res;
    }

    /**
     * Gets the error
     *
     * @return The error.
     */
    public String getBlocks() {
        return blocks;
    }
}