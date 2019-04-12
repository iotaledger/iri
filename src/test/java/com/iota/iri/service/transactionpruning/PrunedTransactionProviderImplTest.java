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
        
        // Filter size of 10, for easier testing
        provider = new PrunedTransactionProviderImpl(10);
        provider.init(config);
    }
    
    @After
    public void tearDown() {
        dbFolder.delete();
    }
    
    @Test
    public void test() throws PrunedTransactionException {
        Hash randomHash = TransactionTestUtils.getRandomTransactionHash();
        provider.addTransaction(randomHash);
        for (int i=0; i<100; i++) {
            provider.addTransaction(TransactionTestUtils.getRandomTransactionHash());
        }
        
        int contains = 0;
        for (int i=0; i<100; i++) {
            if (provider.containsTransaction(randomHash)){
                contains++;
            }
        }
        
        System.out.println(contains);
        assertTrue("Provider should contain the hash", contains > 90);
    }
}
