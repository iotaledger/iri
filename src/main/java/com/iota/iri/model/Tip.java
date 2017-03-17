package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.*;

/**
 * Created by paul on 3/8/17 for iri.
 */
@ArrayModel
@Model
public class Tip extends Flag {
    public Tip(byte[] hashBytes) {
        this.hash = hashBytes;
    }
}
