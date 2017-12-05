package com.iota.iri.service.dto;

public class isTransactionConsistentResponse extends AbstractResponse {

    private boolean state;
    private String info;

    public static AbstractResponse create(boolean state, String info) {
        isTransactionConsistentResponse res = new isTransactionConsistentResponse();
        res.state = state;
        res.info = info;
        return res;
    }

}
