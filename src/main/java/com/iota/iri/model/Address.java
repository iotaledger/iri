package com.iota.iri.model;

import com.iota.iri.tangle.annotations.HasMany;
import com.iota.iri.tangle.annotations.Model;
import com.iota.iri.tangle.annotations.ModelIndex;

/**
 * Created by paul on 3/6/17 for iri.
 */
@Model
public class Address {
    @ModelIndex public byte[] bytes;
    @HasMany public Transaction[] transactions;
}
