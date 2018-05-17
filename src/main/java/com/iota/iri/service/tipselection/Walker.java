package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import java.util.Map;

/**
 * This interface is used to enforce usage of the walk() method which
 * is responsible for returning "tips" via a Markov Chain Monte Carlo
 * function 
 */

public interface Walker {

    Hash walk(Hash entryPoint, Map<Hash, Long> ratings, int maxIndex);

}
