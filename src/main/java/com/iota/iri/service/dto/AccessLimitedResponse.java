package com.iota.iri.service.dto;

/**
 * Created by Adrian on 07.01.2017.
 */
public class AccessLimitedResponse extends AbstractResponse {

    private String error;

    public static AbstractResponse create(String error) {
        AccessLimitedResponse res = new AccessLimitedResponse();
        res.error = error;
        return res;
    }

    public String getError() {
        return error;
    }
}
