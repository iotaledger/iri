package com.iota.iri.benchmarks;

import com.iota.iri.benchmarks.dbbenchmark.RocksDbBenchmark;
import org.junit.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {

    @Test
    public void launchDbBenchmarks() throws RunnerException {

        Options opts = new OptionsBuilder()
                .include(RocksDbBenchmark.class.getName() + ".*")
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.MILLISECONDS)
                .warmupIterations(getWarmUpIterations(5))
                .forks(getForks(1))
                .threads(getThreads())
                .measurementIterations(getMeasurementIterations(10))
                .shouldFailOnError(true)
                .shouldDoGC(false)
                .build();

        //possible to do assertions over run results
        new Runner(opts).run();
    }

    @Test
    public void launchCryptoBenchmark() throws RunnerException {
        Options opts = new OptionsBuilder()
                .include(this.getClass().getPackage().getName() + ".crypto")
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .warmupIterations(getWarmUpIterations(5))
                .forks(getForks(1))
                .threads(getThreads())
                .measurementIterations(getMeasurementIterations(10))
                .shouldFailOnError(true)
                .shouldDoGC(false)
                .build();
        new Runner(opts).run();
    }

    private int getThreads() {
        return getProperty("threads", Integer.toString(Runtime.getRuntime().availableProcessors()));
    }

    private int getForks(int defValue) {
        return getProperty("forks", Integer.toString(defValue));
    }

    private int getWarmUpIterations(int defValue) {
        return getProperty("warmUpIterations", Integer.toString(defValue));
    }

    private int getMeasurementIterations(int defValue) {
        return getProperty("measurementIterations", Integer.toString(defValue));
    }

    private int getProperty(String property, String defValue){
        return Integer.valueOf(Optional.ofNullable(System.getProperty(property)).orElse(defValue));
    }
}