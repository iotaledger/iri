package com.iota.iri.benchmarks;

import com.iota.iri.benchmarks.dbbenchmark.RocksDbBenchmark;
import org.junit.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {

    @Test
    public void launchDbBenchmarks() throws RunnerException {
        Options opts = new OptionsBuilder()
                .include(RocksDbBenchmark.class.getName() + ".*")
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.MILLISECONDS)
                .warmupIterations(5)
                .forks(1)
                .measurementIterations(10)
                .shouldFailOnError(true)
                .shouldDoGC(false)
                .build();

        //possible to do assertions over run results
        new Runner(opts).run();
    }
}
