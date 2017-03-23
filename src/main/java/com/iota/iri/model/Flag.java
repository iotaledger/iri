package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.*;

import java.math.BigInteger;

/**
 * Created by paul on 3/8/17 for iri.
 */
@Model
public class Flag {
    @ModelIndex
    public BigInteger hash;
    @HasOne
    public byte[] status = new byte[]{0};

    public Flag() {}
    public Flag(BigInteger hashBytes) {
        hash = hashBytes;
    }
}
