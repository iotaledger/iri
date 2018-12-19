package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.WalkValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Do not do any validation for speed purpose.
 */
public class WalkValidatorNull implements WalkValidator {

    private final Logger log = LoggerFactory.getLogger(WalkValidator.class);

    public WalkValidatorNull() {
    }

    @Override
    public boolean isValid(Hash transactionHash) throws Exception {
        return true;
    }
}
