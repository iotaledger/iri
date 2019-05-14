package com.iota.iri.service.transactionpruning;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.service.transactionpruning.impl.PrunedTransactionProviderImpl;

public class PrunedTransactionProviderImplTest {
    
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock
    public SnapshotConfig config;
    
    @Rule
    public final TemporaryFolder dbFolder = new TemporaryFolder();
    
    @Rule
    public final TemporaryFolder logFolder = new TemporaryFolder();
    
    PrunedTransactionProviderImpl provider;
    
    @Before
    public void setUp() throws PrunedTransactionException {
        Mockito.when(config.getPrunedTransactionsDbPath()).thenReturn(dbFolder.getRoot().getAbsolutePath());
        Mockito.when(config.getPrunedTransactionsDbLogPath()).thenReturn(logFolder.getRoot().getAbsolutePath());
        
        provider = new PrunedTransactionProviderImpl(10000);
        provider.init(config);
    }
    
    @After
    public void tearDown() {
        dbFolder.delete();
    }
    
    @Test
    public void containsMarginalOkayTest() throws PrunedTransactionException {
        int size = 10000;
        int contains = 0;
        
        Hash[] hashes = new Hash[size];
        for (int i=0; i<size; i++) {
            hashes[i] = TransactionTestUtils.getTransactionHash();
            provider.addTransaction(hashes[i]);
            
            if (provider.containsTransaction(hashes[i])){
                contains++;
            }
        }
        
        hashes = new Hash[size];
        for (int i=0; i<size; i++) {
            hashes[i] = TransactionTestUtils.getTransactionHash();
            if (provider.containsTransaction(hashes[i])){
                contains++;
            }
        }
        
        // 0.05% margin of spents for unknowns
        assertTrue("Provider should only contain the added hash", contains < size*1.0005);
    }
    
    @Test
    public void deletedFiltersTest() throws PrunedTransactionException {
        int size = 100;
        Hash[] hashes = new Hash[size];
        for (int i=0; i<size; i++) {
            hashes[i] = TransactionTestUtils.getTransactionHash();
            provider.addTransaction(hashes[i]);
        }
        
        for (int i=0; i<10000*10; i++) {
            provider.addTransaction(TransactionTestUtils.getTransactionHash());
        }

        // by now, we have added 10 full filters, so the first 100 should not exist anymore (deleted)
        int notContains = 0;
        for (int i=0; i<size; i++) {
            if (!provider.containsTransaction(hashes[i])) {
                notContains++;
            }
        }
        assertTrue("Provider should not contain the deleted hashes", notContains > size*0.995);
    }
}
