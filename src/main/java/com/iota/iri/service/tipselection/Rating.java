package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import java.util.Map;

public interface Rating {


    Map<Hash, Long> calculate(Hash entryPoint);
}
