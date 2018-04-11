package com.iota.iri.service.dto;

public class wereAddressesSpentFrom extends AbstractResponse {

    private boolean [] states;

    public static AbstractResponse create(boolean[] inclusionStates) {
        wereAddressesSpentFrom res = new wereAddressesSpentFrom();
        res.states = inclusionStates;
        return res;
    }

    public boolean[] getStates() {
        return states;
    }

}
