package com.iota.iri.service.transactionpruning.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.CuckooBucket;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.impl.SpentAddressesProviderImpl;
import com.iota.iri.service.transactionpruning.PrunedTransactionException;
import com.iota.iri.service.transactionpruning.PrunedTransactionProvider;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.datastructure.CuckooFilter;

public class PrunedTransactionProviderImpl implements PrunedTransactionProvider {
    
    private static final Logger log = LoggerFactory.getLogger(PrunedTransactionProviderImpl.class);

    private RocksDBPersistenceProvider persistenceProvider;

    private CuckooFilter filter;

    private SnapshotConfig config;
    
    /**
     * Starts the PrunedTransactionProvider by reading the current pruned transactions from a persistence provider
     *
     * @param config The snapshot configuration used for file location
     * @return the current instance
     * @throws SpentAddressesException if we failed to create a file at the designated location
     */
    public PrunedTransactionProviderImpl init(SnapshotConfig config) throws PrunedTransactionException {
        this.config = config;
        try {
            this.persistenceProvider = new RocksDBPersistenceProvider(
                    config.getPrunedTransactionsDbPath(),
                    config.getPrunedTransactionsDbLogPath(),
                    1000,
                    new HashMap<String, Class<? extends Persistable>>(1)
                    {{put("pruned-transactions", CuckooBucket.class);}}, null);
            this.persistenceProvider.init();
            readPreviousPrunedTransactions();
        }
        catch (Exception e) {
            throw new PrunedTransactionException("There is a problem with accessing stored spent addresses", e);
        }
        
        return this;
    }

    private void readPreviousPrunedTransactions() throws PrunedTransactionException {
        if (config.isTestnet()) {
            return;
        }

        try {
            for (byte[] bucketData : persistenceProvider.loadAllKeysFromTable(CuckooBucket.class)) {
                CuckooBucket bucket = new CuckooBucket();
                bucket.read(bucketData);
                filter.update(bucket);
            }
        } catch (IllegalArgumentException e) {
            throw new PrunedTransactionException(e);
        }
    }

    @Override
    public boolean containsTransaction(Hash transactionHash) throws PrunedTransactionException {
        return false;
    }
    
    @Override
    public void addTransaction(Hash transactionHash) throws PrunedTransactionException {
        
    }

    @Override
    public void AddTransactionBatch(Collection<Hash> transactionHashes) throws PrunedTransactionException {
        
    }

}
