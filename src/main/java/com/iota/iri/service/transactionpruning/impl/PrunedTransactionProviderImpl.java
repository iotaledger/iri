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
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.persistables.Cuckoo;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.transactionpruning.PrunedTransactionException;
import com.iota.iri.service.transactionpruning.PrunedTransactionProvider;
import com.iota.iri.storage.Indexable;
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
                    {{put("pruned-transactions", Cuckoo.class);}}, null);
            this.persistenceProvider.init();
            
            filters = new CircularFifoQueue<CuckooFilter>(MAX_FILTERS);
            
            readPreviousPrunedTransactions();
        } catch (Exception e) {
            throw new PrunedTransactionException("There is a problem with accessing previously pruned transactions", e);
        }
        
        return this;
    }

    private void readPreviousPrunedTransactions() throws PrunedTransactionException {
        if (config.isTestnet()) {
            try {
                newFilter();
            } catch (Exception e) {
                // Ignorable, testnet starts empty, log for debugging
                log.warn(e.getMessage());
            }
            return;
        }

        try {
            TreeMap<Integer, CuckooFilter> filters = new TreeMap<>();
            
            // Load all data from all filters
            List<byte[]> bytes = persistenceProvider.loadAllKeysFromTable(Cuckoo.class);
            for (byte[] filterData : bytes) {
                Cuckoo bucket = new Cuckoo();
                bucket.read(filterData);
                
                if (MAX_FILTERS < bucket.filterId.getValue()) {
                    throw new PrunedTransactionException("Database contains more filters then we can store");
                }
                
                CuckooFilter filter = new CuckooFilterImpl(filterSize, 4, 16, bucket.filterBits);
                filters.put(bucket.filterId.getValue(), filter);
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
            
        } catch (Exception e) {
            throw new PrunedTransactionException(e);
        }
    }
    
    private void persistFilter(CuckooFilter filter, Integer index) throws Exception {
        IntegerIndex intIndex = new IntegerIndex(index);
        Cuckoo bucket = new Cuckoo();
        bucket.filterId = intIndex;
        bucket.filterBits = filter.getFilterData();
        persistenceProvider.save(bucket, intIndex);
    }

    /**
     * 
     * {@inheritDoc}
     */
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
     * Adds a transaction to the latest filter. When this filter is full, saves and creates a new filter.
     * 
     * {@inheritDoc}
     * @throws PrunedTransactionException when saving the old (full) filter or deleting the oldest fails 
     */
    @Override
    public void addTransaction(Hash transactionHash) throws PrunedTransactionException {
        try {
            if (null == lastAddedFilter) {
                newFilter();
            }
            
            if (!lastAddedFilter.add(transactionHash.bytes())){
                try {
                    persistFilter(lastAddedFilter, highestIndex);
                } catch (Exception e) {
                    throw new PrunedTransactionException(e);
                }
                newFilter().add(transactionHash.bytes());
            }
        } catch (Exception e) {
            throw new PrunedTransactionException(e);
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
        try {
            persistFilter(lastAddedFilter, highestIndex);
        } catch (Exception e) {
           throw new PrunedTransactionException(e);
        }
    }

    private CuckooFilter newFilter() throws Exception {
        if (filters.isAtFullCapacity()) {
            log.debug("Removing " + filters.peek());
            persistenceProvider.delete(Cuckoo.class, new IntegerIndex(getLowestIndex()));
        }
        
        highestIndex++;
        
        // We keep a reference to the last filter to prevent looking it up every time
        filters.offer(lastAddedFilter = new CuckooFilterImpl(filterSize, 4, 16));
        return lastAddedFilter;
    }

    private int getLowestIndex() {
        if (highestIndex < MAX_FILTERS) {
            return 0;
        }
        
        return highestIndex - MAX_FILTERS;
    }
}
