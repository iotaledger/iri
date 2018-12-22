package com.iota.iri.benchmarks.dbbenchmark.states;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.apache.commons.io.FileUtils;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(Scope.Benchmark)
public abstract class DbState {
    private final File dbFolder = new File("db-bench");
    private final File logFolder = new File("db-log-bench");

    private Tangle tangle;
    private SnapshotProvider snapshotProvider;
    private List<TransactionViewModel> transactions;

    @Param({"10", "100", "500", "1000", "3000"})
    private int numTxsToTest;


    public void setup() throws Exception {
        System.out.println("-----------------------trial setup--------------------------------");
        boolean mkdirs = dbFolder.mkdirs();
        if (!mkdirs) {
            throw new IllegalStateException("db didn't start with a clean slate. Please delete "
                    + dbFolder.getAbsolutePath());
        }
        logFolder.mkdirs();
        PersistenceProvider dbProvider = new RocksDBPersistenceProvider(dbFolder.getPath(), logFolder.getPath(),
                BaseIotaConfig.Defaults.DB_CACHE_SIZE);
        dbProvider.init();
        tangle = new Tangle();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
        tangle.addPersistenceProvider(dbProvider);
        String trytes = "";
        System.out.println("numTxsToTest = [" + numTxsToTest + "]");
        transactions = new ArrayList<>(numTxsToTest);
        for (int i = 0; i < numTxsToTest; i++) {
            trytes = TransactionTestUtils.nextWord(trytes);
            TransactionViewModel tvm = TransactionTestUtils.createTransactionWithTrytes(trytes);
            transactions.add(tvm);
        }
        transactions = Collections.unmodifiableList(transactions);
    }

    public void teardown() throws Exception {
        System.out.println("-----------------------trial teardown--------------------------------");
        tangle.shutdown();
        snapshotProvider.shutdown();
        FileUtils.forceDelete(dbFolder);
        FileUtils.forceDelete(logFolder);
    }

    public void clearDb() throws Exception {
        System.out.println("-----------------------iteration teardown--------------------------------");
        tangle.clearColumn(Transaction.class);
        tangle.clearMetadata(Transaction.class);
    }

    public Tangle getTangle() {
        return tangle;
    }

    public SnapshotProvider getSnapshotProvider() {
        return snapshotProvider;
    }

    public List<TransactionViewModel> getTransactions() {
        return transactions;
    }
}

