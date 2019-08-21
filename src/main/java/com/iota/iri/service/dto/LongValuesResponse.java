package com.iota.iri.service.dto;

public class LongValuesResponse extends AbstractResponse {
    /**
     * States of the specified addresses in Boolean
     * Order of booleans is equal to order of the supplied addresses.
     */
    private long[] values;


    public static AbstractResponse create(long[] values) {
        LongValuesResponse res = new LongValuesResponse();
        res.values = values;
        return res;
    }

    public long[] getValue() {
        return this.values;
    }
}
