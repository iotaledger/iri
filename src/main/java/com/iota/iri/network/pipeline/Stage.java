package com.iota.iri.network.pipeline;

/**
 * Defines a stage in the {@link TransactionProcessingPipelineImpl} which processes a {@link ProcessingContext} and its
 * payload and then mutates the given context with the information for the next stage.
 */
public interface Stage {

    /**
     * Processes the given context and adjusts it with the payloads needed for the next stage (if any).
     * 
     * @param ctx the context to process
     * @return the mutated context (usually the same context as the passed in context)
     */
    ProcessingContext process(ProcessingContext ctx);
}
