package com.iota.iri.network;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotProvider;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private Injector testInjector() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getCoordinator()).thenReturn(BaseIotaConfig.Defaults.COORDINATOR);
        return Guice.createInjector(new NetworkInjectionConfiguration(config), new MockedDependencies());
    }

    private class MockedDependencies extends AbstractModule {

        @Override
        protected void configure() {
            bind(LatestMilestoneTracker.class).toInstance(mock(LatestMilestoneTracker.class));
            bind(SnapshotProvider.class).toInstance(mock(SnapshotProvider.class));
            bind(TransactionValidator.class).toInstance(mock(TransactionValidator.class));
        }

    }
}