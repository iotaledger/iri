package com.iota.iri.model;

import com.iota.iri.tangle.annotations.ArrayModel;
import com.iota.iri.tangle.annotations.HasMany;
import com.iota.iri.tangle.annotations.Model;
import com.iota.iri.tangle.annotations.ModelIndex;

/**
 * Created by paul on 3/8/17 for iri.
 */
@ArrayModel
@Model
public class Tip {
    @ModelIndex byte[] hash;
    @HasMany  public boolean isTip = false;
}
