package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;

/**
 * This interface is used to enforce usage of the walk() method which
 * is responsible for returning "tips" via a Markov Chain Monte Carlo
 * function
 */
@FunctionalInterface
public interface WalkValidator {

    boolean isValid(Hash transactionId) throws Exception;

}
