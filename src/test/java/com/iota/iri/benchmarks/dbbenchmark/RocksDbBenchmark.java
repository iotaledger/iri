package com.iota.iri.benchmarks.dbbenchmark;

import com.iota.iri.benchmarks.dbbenchmark.states.EmptyState;
import com.iota.iri.benchmarks.dbbenchmark.states.FullState;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Pair;
import org.openjdk.jmh.annotations.Benchmark;


public class RocksDbBenchmark {

    @Benchmark
    public void persistOneByOne(EmptyState state) throws Exception {
        for (TransactionViewModel tvm: state.getTransactions()) {
            tvm.store(state.getTangle(), state.getSnapshotProvider().getInitialSnapshot());
        }
    }

    @Benchmark
    public void deleteOneByOne(FullState state) throws Exception {
        for (TransactionViewModel tvm : state.getTransactions()) {
            tvm.delete(state.getTangle());
        }
    }

    @Benchmark
    public void dropAll(FullState state) throws Exception {
        state.getTangle().clearColumn(Transaction.class);
        state.getTangle().clearMetadata(Transaction.class);
    }

    @Benchmark
    public void deleteBatch(FullState state) throws Exception {
        state.getTangle().deleteBatch(state.getPairs());
    }

    @Benchmark
    public void fetchOneByOne(FullState state) throws Exception {
        for (Pair<Indexable, ? extends Class<? extends Persistable>> pair : state.getPairs()) {
            state.getTangle().load(pair.hi, pair.low);
        }
    }



}
