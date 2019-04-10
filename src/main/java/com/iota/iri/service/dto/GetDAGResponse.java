package com.iota.iri.service.dto;

/**
 * This class represents the core API error for accessing a command which is limited by this Node.
 */
public class GetDAGResponse extends AbstractResponse {

    private String dag;

    public static AbstractResponse create(String dag) {
        GetDAGResponse res = new GetDAGResponse();
        res.dag = dag;
        return res;
    }

    /**
     * Gets the error
     *
     * @return The error.
     */
    public String getDAG() {
        return dag;
    }
}