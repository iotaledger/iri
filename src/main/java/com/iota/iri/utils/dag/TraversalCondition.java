package com.iota.iri.utils.dag;

import com.iota.iri.controllers.TransactionViewModel;

/**
 * Functional interface for the lambda function getting passed into the DAGUtils traverse method to check if the found
 * transactions is relevant for our processing.
 */
@FunctionalInterface
public interface TraversalCondition {
    boolean check(TransactionViewModel currentTransaction) throws Exception;
}
