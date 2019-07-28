package com.iota.iri.service.spentaddresses.impl;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.AddressHash;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.utils.Pair;

import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SpentAddressesProviderImplTest {
    
    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    
    @Rule 
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SnapshotConfig config;
    
    @Mock
    private PersistenceProvider persistenceProvider;
    
    private SpentAddressesProviderImpl provider;
    
    @Before
    public void setUp() throws Exception {
        when(config.isTestnet()).thenReturn(true);
        
        provider = new SpentAddressesProviderImpl();
        provider.init(config, this.persistenceProvider, false);
    }

    @After
    public void tearDown() throws Exception {
        persistenceProvider.shutdown();
    }

    @Test
    public void testContainsAddress() throws Exception {
        when(persistenceProvider.exists(SpentAddress.class, A)).thenReturn(true);
        when(persistenceProvider.exists(SpentAddress.class, B)).thenReturn(false);
        
        assertTrue("Provider should have A as spent", provider.containsAddress(A));
        assertFalse("Provider should not have B as spent", provider.containsAddress(B));
    }

    @Test
    public void testSaveAddress() throws Exception {
        provider.saveAddress(A);
        
        try {
            verify(persistenceProvider, times(1)).save(any(SpentAddress.class), Mockito.eq(A));
        } catch (MockitoAssertionError e) {
            throw new MockitoAssertionError("Save should have been called once in the provider");
        }
    }

    @Test
    public void testSaveAddressesBatch() throws Exception {
        List<Hash> addresses = new LinkedList<Hash>(){{
           add(A);
           add(B);
        }};
        
        provider.saveAddressesBatch(addresses);
        
        ArgumentMatcher<List<Pair<Indexable, Persistable>>> matcher = new ArgumentMatcher<List<Pair<Indexable,Persistable>>>() {

            @Override
            public boolean matches(List<Pair<Indexable, Persistable>> list) {
                return list.size() == 2;
            }
        };
        
        try {
            verify(persistenceProvider, times(1)).saveBatch(Mockito.argThat(matcher));
        } catch (MockitoAssertionError e) {
            throw new MockitoAssertionError("Savebatch should have been called once with a map of 2 pairs, in the provider");
        }
    }

    //The ExpectedSystemExit rule works by issuing a SecurityException.
    //Since we don't have a specific Exception for db errors yet we catch a general exception in the
    //SpentAddressProvider and rethrow it as SpentAddressesException. So unfortunately for the test it also
    //catches the security exception. Once we fix exceptions for the persistence layer we can fix this test as well.
    @Test(expected = SpentAddressesException.class)
    public void failWhenAssertingForExistingData() throws Exception {
        exit.expectSystemExitWithStatus(1);
        when(persistenceProvider.first(SpentAddress.class, AddressHash.class)).thenReturn(new Pair<>(null,null));
        provider.init(config, persistenceProvider, true);
    }

}