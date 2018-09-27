package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Created by paul on 5/15/17.
 */
public class Approvee extends Hashes{
    public Approvee(Hash hash) {
        set.add(hash);
    }

    public Approvee() {

    }
}
