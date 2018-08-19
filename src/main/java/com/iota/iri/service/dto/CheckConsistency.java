package com.iota.iri.service.dto;

/**
 * This class represents the core API request 'checkConsistency'.
 * Checks the consistency of the transactions. 
 * Marks state as false on the following checks
 * - Transaction does not exist
 * - Transaction is not a tail
 * - Missing a reference transaction
 * - Invalid bundle
 * - Tails of tails are invalid
 **/
public class CheckConsistency extends AbstractResponse {

    private boolean state;
    
    private String info;

    public static AbstractResponse create(boolean state, String info) {
        CheckConsistency res = new CheckConsistency();
        res.state = state;
        res.info = info;
        return res;
    }

    /**
     * The state of the transaction
     *
     * @return The state.
     */
    public boolean getState() {
        return state;
    }
    
    /**
     * Information about the state
     *
     * @return The info.
     */
    public String getInfo() {
        return info;
    }
}
