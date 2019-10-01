package com.iota.iri.service.dto;


/**
 * Genereric boolean list response
 */
public class BooleanValuesResponse extends AbstractResponse {
    /**
     * List of boleans to use as an API result
     */
    private boolean[] result;


    /**
     * Creates a new {@link BooleanValuesResponse}
     * @param values {@link #result}
     * @return an {@link BooleanValuesResponse} filled with a list of booleans
     */
    public static AbstractResponse create(boolean[] values) {
        BooleanValuesResponse res = new BooleanValuesResponse();
        res.result = values;
        return res;
    }

    /**
     *
     * @return list of booleans
     */
    public boolean[] getResult() {
        return this.result;
    }
}