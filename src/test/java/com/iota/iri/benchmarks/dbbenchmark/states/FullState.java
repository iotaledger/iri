package com.iota.iri.benchmarks.dbbenchmark.states;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Pair;
import org.openjdk.jmh.annotations.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
public class FullState extends DbState {

    private List<Pair<Indexable, ? extends Class<? extends Persistable>>> pairs;

    @Override
    @Setup(Level.Trial)
    public void setup() throws Exception {
        super.setup();
        pairs = getTransactions().stream()
                .map(tvm -> new Pair<>((Indexable) tvm.getHash(), Transaction.class))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

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

    @Setup(Level.Iteration)
    public void populateDb() throws Exception {
        System.out.println("-----------------------iteration setup--------------------------------");
        for (TransactionViewModel tvm : getTransactions()) {
            tvm.store(getTangle(), getSnapshotProvider().getInitialSnapshot());
        }
    }

    public List<Pair<Indexable, ? extends Class<? extends Persistable>>> getPairs() {
        return pairs;
    }
}
