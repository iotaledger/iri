package com.iota.iri.service.spentaddresses.impl;

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.utils.Pair;

public class SpentAddressesProviderImplTest {
    
    private static Hash A = TransactionTestUtils.getRandomTransactionHash();
    private static Hash B = TransactionTestUtils.getRandomTransactionHash();
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SnapshotConfig config;
    
    @Mock
    private PersistenceProvider persistenceProvider;
    
    private SpentAddressesProviderImpl provider;
    
    @Before
    public void setUp() throws Exception {
        Mockito.when(config.isTestnet()).thenReturn(true);
        
        provider = new SpentAddressesProviderImpl();
        provider.init(config, this.persistenceProvider);
    }

    @After
    public void tearDown() throws Exception {
        persistenceProvider.shutdown();
    }

    @Test
    public void testContainsAddress() throws Exception {
        Mockito.when(persistenceProvider.exists(SpentAddress.class, A)).thenReturn(true);
        Mockito.when(persistenceProvider.exists(SpentAddress.class, B)).thenReturn(false);
        
        assertTrue(provider.containsAddress(A));
        assertFalse(provider.containsAddress(B));
    }

    @Test
    public void testSaveAddress() throws Exception {
        provider.saveAddress(A);
        verify(persistenceProvider, times(1)).save(any(SpentAddress.class), Mockito.eq(A));
    }

    @Test
    public void testSaveAddressesBatch() throws Exception {
        List<Hash> addresses = new LinkedList<Hash>(){{
           add(A);
           add(B);
        }};
        
        provider.saveAddressesBatch(addresses);
        
        ArgumentMatcher<List<Pair<Indexable, Persistable>>> matcher = new ArgumentMatcher<List<Pair<Indexable,Persistable>>>() {
            public boolean matches(Object list) {
                return ((List) list).size() == 2;
            }
        };
        
        verify(persistenceProvider, times(1)).saveBatch(Mockito.argThat(matcher));
    }

}