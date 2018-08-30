package com.iota.iri.service.dto;

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
