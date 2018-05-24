package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.utils.collections.interfaces.TransformingMap;

import java.util.Collection;
import java.util.Map;

/**
 * This interface is used to enforce usage of the walk() method which
 * is responsible for returning "tips" via a Markov Chain Monte Carlo
 * function
 */

public interface Walker {

    /**
     * Walk algorithm
     * <p>
     * Starts from given entry point to select valid transactions to be used
     * as tips. It will output valid transactions as tips.
     * </p>
     *
     * @param entryPoint  Transaction ID of milestone to start walk from.
     * @param ratings  Mapped ratings associated with Transaction ID.
     * @param walkValidator Used to validate consistency of tails.
     * @return  Transaction ID of tip.
     */
    Hash walk(Hash entryPoint, TransformingMap<HashId, Integer> ratings, WalkValidator walkValidator) throws Exception;

}
