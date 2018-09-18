package com.iota.iri.service.garbageCollector;

/**
 * Functional interface for the lambda function that takes care of parsing a specific job from its serialized String
 * representation into the corresponding object in memory.
 *
 * @see GarbageCollector#registerParser(Class, JobParser) to register the parser
 */
@FunctionalInterface
public interface JobParser {
    GarbageCollectorJob parse(String input) throws GarbageCollectorException;
}
