package com.iota.iri.service.dto;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code wereAddressesSpentFrom} API call.
 * See {@link API#wereAddressesSpentFromStatement} for how this response is created.
 *
 */
public class WereAddressesSpentFrom extends AbstractResponse {

    /**
     * States of the specified addresses in Boolean
     * Order of booleans is equal to order of the supplied addresses.
     */
    private boolean[] states;

    /**
     * Creates a new {@link WereAddressesSpentFrom}
     * 
     * @param inclusionStates {@link #states}
     * @return a {@link WereAddressesSpentFrom} filled with the address states
     */
    public static AbstractResponse create(boolean[] inclusionStates) {
        WereAddressesSpentFrom res = new WereAddressesSpentFrom();
        res.states = inclusionStates;
        return res;
    }

    /**
     * 
     * @return {@link #states}
     */
    public boolean[] getStates() {
        return states;
    }

}
