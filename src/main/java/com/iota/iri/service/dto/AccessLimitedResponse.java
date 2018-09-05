package com.iota.iri.service.dto;

/**
 * This class represents the core API error for accessing a command which is limited by this Node.
 */
public class AccessLimitedResponse extends AbstractResponse {

    private String error;

    public static AbstractResponse create(String error) {
        AccessLimitedResponse res = new AccessLimitedResponse();
        res.error = error;
        return res;
    }

    /**
     * Gets the error
     *
     * @return The error.
     */
    public String getError() {
        return error;
    }
}
