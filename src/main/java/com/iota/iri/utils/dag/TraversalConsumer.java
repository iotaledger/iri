package com.iota.iri.utils.dag;

import com.iota.iri.controllers.TransactionViewModel;

/**
 * Functional interface for the lambda function getting passed into the DAGUtils traverse method to process the found
 * transactions.
 */
@FunctionalInterface
public interface TraversalConsumer {
    void consume(TransactionViewModel currentTransaction) throws Exception;
}
