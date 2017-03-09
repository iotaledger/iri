package com.iota.iri.model;

import com.iota.iri.tangle.annotations.HasMany;
import com.iota.iri.tangle.annotations.Model;
import com.iota.iri.tangle.annotations.ModelIndex;

/**
 * Created by paul on 3/8/17 for iri.
 */
@Model
public class Approvee {
    @ModelIndex
    public byte[] hash;
    @HasMany
    public Transaction[] transactions;
}
