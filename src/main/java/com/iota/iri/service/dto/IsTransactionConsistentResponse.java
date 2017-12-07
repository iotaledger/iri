package com.iota.iri.service.dto;

public class IsTransactionConsistentResponse extends AbstractResponse {

    private boolean state;
    private String info;

    public static AbstractResponse create(boolean state, String info) {
        IsTransactionConsistentResponse res = new IsTransactionConsistentResponse();
        res.state = state;
        res.info = info;
        return res;
    }

}
