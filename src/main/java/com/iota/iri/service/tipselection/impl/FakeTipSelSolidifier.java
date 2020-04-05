package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.TipSelSolidifier;

/**
 * Does nothing
 */
public class FakeTipSelSolidifier implements TipSelSolidifier {


    @Override
    public void solidify(Hash transactionHash) {
        //DO NOTHING
    }
}
