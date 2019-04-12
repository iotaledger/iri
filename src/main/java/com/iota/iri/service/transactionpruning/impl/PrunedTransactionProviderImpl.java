package com.iota.iri.service.transactionpruning.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.CuckooBucket;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.transactionpruning.PrunedTransactionException;
import com.iota.iri.service.transactionpruning.PrunedTransactionProvider;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.datastructure.CuckooFilter;
import com.iota.iri.utils.datastructure.impl.CuckooFilterImpl;

/**
 * 
 */
public class PrunedTransactionProviderImpl implements PrunedTransactionProvider {
    
    private static final Logger log = LoggerFactory.getLogger(PrunedTransactionProviderImpl.class);
    
    private static final int FILTER_SIZE = 200000;
    
    private static final int MAX_FILTERS = 10;
    
    private RocksDBPersistenceProvider persistenceProvider;
    
    private SnapshotConfig config;

    private CircularFifoQueue<CuckooFilter> filters;
    private CuckooFilter lastAddedFilter;

    // Once this exceeds integer max range, this will give problems.
    private Integer highestIndex = -1;

    private int filterSize;
    
    /**
     * 
     */
    public PrunedTransactionProviderImpl() {
        this(FILTER_SIZE);
    }
    
    /**
     * 
     * @param filterSize
     */
    public PrunedTransactionProviderImpl(int filterSize) {
        this.filterSize = filterSize;
    }
    
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
            
            // 10 * 1.000.000 * 4 hashes we can hold, with a total of 80mb size when max filled.
            // The database has overhead for the bucket index, and allows a maximum of 2^7 - 1 filters
            filters = new CircularFifoQueue<CuckooFilter>(MAX_FILTERS);
            
            readPreviousPrunedTransactions();
        } catch (Exception e) {
            throw new PrunedTransactionException("There is a problem with accessing previously pruned transactions", e);
        }
        
        return this;
    }

    private void readPreviousPrunedTransactions() throws PrunedTransactionException {
        if (config.isTestnet()) {
            newFilter();
            return;
        }

        try {
            
            TreeMap<Integer, CuckooFilter> filters = new TreeMap<>();
            
            // Load all data from all filters
            List<byte[]> bytes = persistenceProvider.loadAllKeysFromTable(CuckooBucket.class);
            for (byte[] bucketData : bytes) {
                CuckooBucket bucket = new CuckooBucket();
                bucket.read(bucketData);
                
                if (MAX_FILTERS < bucket.bucketId.getValue()) {
                    throw new PrunedTransactionException("Database contains more filters then we can store");
                }
                
                // Find its bucket, or create it if wasnt there
                CuckooFilter filter = filters.get(bucket.bucketId.getValue());
                if (null == filter) {
                    filter = new CuckooFilterImpl(filterSize, 4, 16);
                    filters.put(bucket.bucketId.getValue(), filter);
                }
                
                // Don't update using the entire CuckooBucket so that packages are separate-able
                filter.update(bucket.bucketIndex.getValue(), bucket.bucketBits);
            }
            
            //Then add all in order, treemap maintains order from lowest to highest key
            for (CuckooFilter filter : filters.values()) {
                if (null != filter) {
                    this.filters.add(filter);
                }
            }
            
            Entry<Integer, CuckooFilter> entry = filters.lastEntry();
            if (null != entry) {
                lastAddedFilter = entry.getValue();
                highestIndex = entry.getKey();
            } else {
                newFilter();
            }
            
        } catch (IllegalArgumentException e) {
            throw new PrunedTransactionException(e);
        }
    }
    
    private void persistFilter(CuckooFilter filter, Integer index) {
        
    }

    @Override
    public boolean containsTransaction(Hash transactionHash) throws PrunedTransactionException {
        byte[] hashBytes = transactionHash.bytes();
        
        // last filter added is most recent, thus most likely to be requested
        CuckooFilter[] filterArray = filters.toArray(new CuckooFilter[filters.size()]);
        for (int i = filterArray.length - 1; i >=0; i--) {
            if (filterArray[i].contains(hashBytes)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public void addTransaction(Hash transactionHash) throws PrunedTransactionException {
        if (null == lastAddedFilter) {
            newFilter();
        }
        
        if (!lastAddedFilter.add(transactionHash.bytes())){
            newFilter().add(transactionHash.bytes());
        }
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public void addTransactionBatch(Collection<Hash> transactionHashes) throws PrunedTransactionException {
        // Should we create a new filter when we get this method call? 
        // It probably implies a new pruned job completed, and these TX would be checked more often then the older ones.
        for (Hash transactionHash : transactionHashes) {
            addTransaction(transactionHash);
        }
        persistFilter(lastAddedFilter, highestIndex);
    }

    private CuckooFilter newFilter() {
        if (filters.isAtFullCapacity()) {
            log.debug("Removing " + filters.peek());
        }
        
        highestIndex++;
        
        // We keep a reference to the last filter to prevent looking it up every time
        filters.offer(lastAddedFilter = new CuckooFilterImpl(filterSize, 4, 16));
        return lastAddedFilter;
    }
}
