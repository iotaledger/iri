package com.iota.iri.service.spentaddresses.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.spentaddresses.SpentAddressesException;

public class SpentAddressesProviderImplTest {
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock
    private SnapshotConfig config;

    @Before
    public void setUp() throws Exception {
        Mockito.when(config.isTestnet()).thenReturn(false);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testContainsAddress() throws SpentAddressesException {
        SpentAddressesProviderImpl provider = new SpentAddressesProviderImpl();
        provider.init(config);
    }

    @Test
    public void testSaveAddress() {
        fail("Not yet implemented");
    }

    @Test
    public void testSaveAddressesBatch() {
        fail("Not yet implemented");
    }

}
