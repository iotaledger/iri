package com.iota.iri.model;

import com.iota.iri.tangle.annotations.*;

/**
 * Created by paul on 3/8/17 for iri.
 */
@Removeable
@ArrayModel
@Model
public class Flag {
    @ModelIndex
    public byte[] hash;
    @HasOne
    public boolean status = true;

    public Flag() {}
    public Flag(byte[] hashBytes) {
        hash = hashBytes;
    }
}
