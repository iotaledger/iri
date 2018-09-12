package com.iota.iri.utils.dag;

import com.iota.iri.controllers.TransactionViewModel;

/**
 * Functional interface for the lambda function getting passed into the DAGUtils traverse method to check if the found
 * transaction is relevant for our processing.
 */
public interface TraversalCondition {
    boolean check(TransactionViewModel currentTransaction) throws Exception;
}
