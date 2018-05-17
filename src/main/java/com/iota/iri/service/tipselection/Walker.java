package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;

import java.util.Map;

public interface Walker {

    public void walk(Hash entryPoint, Map<Hash, Long> ratings, int maxIndex);

}
