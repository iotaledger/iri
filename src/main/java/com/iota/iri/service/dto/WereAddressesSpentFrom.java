package com.iota.iri.service.dto;

public class WereAddressesSpentFrom extends AbstractResponse {

    private boolean [] states;

    public static AbstractResponse create(boolean[] inclusionStates) {
        WereAddressesSpentFrom res = new WereAddressesSpentFrom();
        res.states = inclusionStates;
        return res;
    }

    public boolean[] getStates() {
        return states;
    }

}
