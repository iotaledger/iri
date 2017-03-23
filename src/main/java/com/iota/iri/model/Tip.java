package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.*;

import java.math.BigInteger;

/**
 * Created by paul on 3/8/17 for iri.
 */
@ArrayModel
@Model
public class Tip extends Flag {
    public Tip(BigInteger hashBytes) {
        this.hash = hashBytes;
    }
}
