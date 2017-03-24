package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.HasMany;
import com.iota.iri.service.tangle.annotations.Model;
import com.iota.iri.service.tangle.annotations.ModelIndex;
import com.iota.iri.service.tangle.annotations.SizedArray;

/**
 * Created by paul on 3/8/17 for iri.
 */
@Model
public class Bundle {
    @SizedArray(length = Hash.SIZE_IN_BYTES)
    @ModelIndex
    public Hash hash;
    @HasMany public Hash[] transactions;
}
