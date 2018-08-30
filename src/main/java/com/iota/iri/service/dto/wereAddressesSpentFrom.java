package com.iota.iri.service.dto;

public class wereAddressesSpentFrom extends AbstractResponse {

    private boolean [] states;

    public static AbstractResponse create(boolean[] inclusionStates) {
        wereAddressesSpentFrom res = new wereAddressesSpentFrom();
        res.states = inclusionStates;
        return res;
    }

    /**
     * States of the specified addresses in Boolean
     *
     * @return The states.
     */
    public boolean[] getStates() {
        return states;
    }

}
