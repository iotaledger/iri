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
     * The state of all the provided tails, which is set to {@code false} on the following checks<br/>
     * <ol>
     *     <li>Missing a reference transaction<li/>
     *     <li>Invalid bundle<li/>
     *     <li>Tails of tails are invalid<li/>
     * </ol>
     */
    private boolean state;
    
    /**
     * If state is {@code false}, this provides information on the cause of the inconsistency.
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
