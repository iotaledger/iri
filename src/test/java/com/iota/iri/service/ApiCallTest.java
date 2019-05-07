package com.iota.iri.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.iota.iri.conf.IotaConfig;

public class ApiCallTest {
    
    private API api;
    
    @Before
    public void setUp() {
        IotaConfig configuration = Mockito.mock(IotaConfig.class);
        api = new API(configuration, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    public void apiHasAllEnums() {
        for (ApiCommand c : ApiCommand.values()) {
            if (!api.commandRoute.containsKey(c)) {
                Assert.fail("Api should contain all enum values");
            }
        }
    }
}
