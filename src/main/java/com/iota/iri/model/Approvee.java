package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.HasMany;
import com.iota.iri.service.tangle.annotations.Model;
import com.iota.iri.service.tangle.annotations.ModelIndex;
import com.iota.iri.service.tangle.annotations.SizedArray;

import java.math.BigInteger;

/**
 * Created by paul on 3/8/17 for iri.
 */
@Model
public class Approvee {
    @SizedArray(length = Hash.SIZE_IN_BYTES)
    @ModelIndex
    public BigInteger hash;
    @HasMany
    public BigInteger[] transactions;
}
