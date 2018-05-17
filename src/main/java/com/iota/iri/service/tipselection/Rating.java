package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import java.util.Map;

/**
 * This interface is used to enforce usage of the calculate() method
 * which is in charge of calculating the cumulative rating of
 * transactions with connection to the entry point.
 */

public interface Rating {


    Map<Hash, Long> calculate(Hash entryPoint);
}
