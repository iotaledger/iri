package com.iota.iri.service.dto;

public class BooleanValuesRespones extends AbstractResponse {
    /**
     * States of the specified addresses in Boolean
     * Order of booleans is equal to order of the supplied addresses.
     */
    private boolean[] values;


    public static AbstractResponse create(boolean[] values) {
        BooleanValuesRespones res = new BooleanValuesRespones();
        res.values = values;
        return res;
    }

    public boolean[] getValue() {
        return this.values;
    }
}