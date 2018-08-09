package com.iota.iri.service.dto;

/**
 * This class represents the core API request 'wereAddressesSpentFrom'.
 * Check if a list of addresses was ever spent from, in the current epoch, or in previous epochs.
 **/
public class wereAddressesSpentFrom extends AbstractResponse {

    private boolean [] states;

    public static AbstractResponse create(boolean[] inclusionStates) {
        wereAddressesSpentFrom res = new wereAddressesSpentFrom();
        res.states = inclusionStates;
        return res;
    }

    /**
     * Gets the states of the addresses
     *
     * @return The states.
     */
    public boolean[] getStates() {
        return states;
    }

}
