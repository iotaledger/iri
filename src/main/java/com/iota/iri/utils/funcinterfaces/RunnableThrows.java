package com.iota.iri.utils.funcinterfaces;

@FunctionalInterface
public interface RunnableThrows {
    void run() throws Exception;
}