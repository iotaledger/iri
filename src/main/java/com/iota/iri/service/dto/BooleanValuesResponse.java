package com.iota.iri.service.dto;

public class BooleanValuesResponse extends AbstractResponse {
    /**
     * States of the specified addresses in Boolean
     * Order of booleans is equal to order of the supplied addresses.
     */
    private boolean[] result;


    public static AbstractResponse create(boolean[] values) {
        BooleanValuesResponse res = new BooleanValuesResponse();
        res.result = values;
        return res;
    }

    public boolean[] getResult() {
        return this.result;
    }
}