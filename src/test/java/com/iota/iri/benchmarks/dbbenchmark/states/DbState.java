package com.iota.iri.benchmarks.dbbenchmark.states;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Transaction;
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
    private final File dbFolder = new File("db-clean");
    private final File logFolder = new File("db-log-clean");

    private Tangle tangle;
    private List<TransactionViewModel> transactions;

    @Param({"10", "100", "500", "1000", "3000"})
    private int numTxsToTest;


    public void setup() throws Exception {
        System.out.println("-----------------------trial setup--------------------------------");
        boolean mkdirs = dbFolder.mkdirs();
        System.out.println("mkdirs success: " + mkdirs );
        logFolder.mkdirs();
        PersistenceProvider dbProvider = new RocksDBPersistenceProvider(dbFolder.getPath(), logFolder.getPath(),
                BaseIotaConfig.Defaults.DB_CACHE_SIZE);
        dbProvider.init();
        tangle = new Tangle();
        tangle.addPersistenceProvider(dbProvider);
        String trytes = "";
        System.out.println("numTxsToTest = [" + numTxsToTest + "]");
        transactions = new ArrayList<>(numTxsToTest);
        for (int i = 0; i < numTxsToTest; i++) {
            trytes = nextWord(trytes);
            TransactionViewModel tvm = TransactionTestUtils.createTransactionWithTrytes(trytes);
            transactions.add(tvm);
        }
        transactions = Collections.unmodifiableList(transactions);
    }

    public void teardown() throws Exception {
        System.out.println("-----------------------trial teardown--------------------------------");
        tangle.shutdown();
        FileUtils.forceDelete(dbFolder);
        FileUtils.forceDelete(logFolder);
    }

    public void clearDb() throws Exception {
        System.out.println("-----------------------iteration teardown--------------------------------");
        tangle.clearColumn(Transaction.class);
        tangle.clearMetadata(Transaction.class);
    }

    private String nextWord(String trytes) {
        if ("".equals(trytes)) {
            return "A";
        }
        trytes = trytes.toUpperCase();
        char[] chars = trytes.toCharArray();
        for (int i = chars.length -1; i>=0; --i) {
            if (chars[i] != 'Z') {
                ++chars[i];
                return new String(chars);
            }
        }
        return trytes + 'A';
    }

    public Tangle getTangle() {
        return tangle;
    }

    public List<TransactionViewModel> getTransactions() {
        return transactions;
    }
}

