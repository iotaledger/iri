package com.iota.iri.network;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.service.milestone.InSyncService;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.service.validation.TransactionValidator;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Test;

public class NetworkInjectionConfigurationTest {

    @Test
    public void provideTransactionRequester() {
        assertNotNull("instance creation did not work", testInjector().getInstance(TransactionRequester.class));
    }

    @Test
    public void provideNeighborRouter() {
        assertNotNull("instance creation did not work", testInjector().getInstance(NeighborRouter.class));
    }

    @Test
    public void provideTipsRequester() {
        assertNotNull("instance creation did not work", testInjector().getInstance(TipsRequester.class));
    }

    @Test
    public void provideTransactionProcessingPipeline() {
        assertNotNull("instance creation did not work", testInjector().getInstance(TransactionProcessingPipeline.class));
    }

    @Test
    public void provideTransactionSolidifier(){
        assertNotNull("instance creation did not work", testInjector().getInstance(TransactionSolidifier.class));
    }

    @Test
    public void provideInSyncService(){
        assertNotNull("instance creation did not work", testInjector().getInstance(InSyncService.class));
    }

    private Injector testInjector() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getCoordinator()).thenReturn(BaseIotaConfig.Defaults.COORDINATOR);
        return Guice.createInjector(new NetworkInjectionConfiguration(config), new MockedDependencies());
    }

    private class MockedDependencies extends AbstractModule {

        @Override
        protected void configure() {
            bind(MilestoneSolidifier.class).toInstance(mock(MilestoneSolidifier.class));
            bind(SnapshotProvider.class).toInstance(mock(SnapshotProvider.class));
            bind(TransactionValidator.class).toInstance(mock(TransactionValidator.class));
            bind(TransactionSolidifier.class).toInstance(mock(TransactionSolidifier.class));
            bind(MilestoneService.class).toInstance(mock(MilestoneService.class));
            bind(InSyncService.class).toInstance(mock(InSyncService.class));
        }

    }
}