package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.HasMany;
import com.iota.iri.service.tangle.annotations.Model;
import com.iota.iri.service.tangle.annotations.ModelIndex;

/**
 * Created by paul on 3/6/17 for iri.
 */
@Model
public class Tag {
    @ModelIndex public byte[] bytes;
    @HasMany public Transaction[] transactions;
}
