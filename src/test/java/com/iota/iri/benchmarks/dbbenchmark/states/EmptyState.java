package com.iota.iri.benchmarks.dbbenchmark.states;

import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
public class EmptyState extends DbState {

    @Override
    @Setup(Level.Trial)
    public void setup() throws Exception {
        super.setup();
    }

    @Override
    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        super.teardown();
    }

    @Override
    @TearDown(Level.Iteration)
    public void clearDb() throws Exception {
        super.clearDb();
    }

}
