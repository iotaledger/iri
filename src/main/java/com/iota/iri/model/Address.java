package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.HasMany;
import com.iota.iri.service.tangle.annotations.Model;
import com.iota.iri.service.tangle.annotations.ModelIndex;

import java.math.BigInteger;

/**
 * Created by paul on 3/6/17 for iri.
 */
@Model
public class Address {
    @ModelIndex public BigInteger hash;
    @HasMany public BigInteger[] transactions;
}
