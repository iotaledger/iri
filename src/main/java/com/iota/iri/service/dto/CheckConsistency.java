package com.iota.iri.service.dto;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code checkConsistency} API call.
 * See {@link API#checkConsistencyStatement} for how this response is created.
 *
 */
public class CheckConsistency extends AbstractResponse {

    /**
     * States of the specified transactions in the same order as the values in the `tails` parameter. 
     * A `true` value means that the transaction is consistent.
     */
    private boolean state;
    
    /**
     * If state is {@code false}, this contains information about why the transaction is inconsistent.
     */
    private String info;

    /**
     * Creates a new {@link CheckConsistency}
     * @param state {@link #state}
     * @param info {@link #info}
     * @return a {@link CheckConsistency} filled with the state and info
     */
    public static AbstractResponse create(boolean state, String info) {
        CheckConsistency res = new CheckConsistency();
        res.state = state;
        res.info = info;
        return res;
    }

    /**
     * 
     * @return {@link #state}
     */
    public boolean getState() {
        return state;
    }
    
    /**
     * 
     * @return {@link #info}
     */
    public String getInfo() {
        return info;
    }
}
