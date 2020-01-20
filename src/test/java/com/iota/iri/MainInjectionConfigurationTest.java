package com.iota.iri;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.TipsRequester;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.service.API;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.milestone.LatestSolidMilestoneTracker;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.milestone.SeenMilestonesRetriever;
import com.iota.iri.service.snapshot.LocalSnapshotManager;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesService;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.storage.LocalSnapshotsPersistenceProvider;
import com.iota.iri.storage.Tangle;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MainInjectionConfigurationTest {

    @Test
    public void provideSnapshotProvider() {
        assertNotNull("instance creation did not work", testInjector().getInstance(SnapshotProvider.class));
    }

    @Test
    public void provideSpentAddressesProvider() {
        assertNotNull("instance creation did not work", testInjector().getInstance(SpentAddressesProvider.class));
    }

    @Test
    public void provideSnapshotService() {
        assertNotNull("instance creation did not work", testInjector().getInstance(SnapshotService.class));
    }

    @Test
    public void provideMilestoneService() {
        assertNotNull("instance creation did not work", testInjector().getInstance(MilestoneService.class));
    }

    @Test
    public void provideSpentAddressesService() {
        assertNotNull("instance creation did not work", testInjector().getInstance(SpentAddressesService.class));
    }

    @Test
    public void provideLedgerService() {
        assertNotNull("instance creation did not work", testInjector().getInstance(LedgerService.class));
    }

    @Test
    public void provideLatestMilestoneTracker() {
        assertNotNull("instance creation did not work", testInjector().getInstance(LatestMilestoneTracker.class));
    }

    @Test
    public void provideLatestSolidMilestoneTracker() {
        assertNotNull("instance creation did not work", testInjector().getInstance(LatestSolidMilestoneTracker.class));
    }

    @Test
    public void provideSeenMilestonesRetriever() {
        assertNotNull("instance creation did not work", testInjector().getInstance(SeenMilestonesRetriever.class));
    }

    @Test
    public void provideMilestoneSolidifier() {
        assertNotNull("instance creation did not work", testInjector().getInstance(MilestoneSolidifier.class));
    }

    @Test
    public void provideTransactionPruner() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsPruningEnabled()).thenReturn(true);
        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        assertNotNull("instance creation did not work", testInjector(config).getInstance(TransactionPruner.class));
    }

    @Test
    public void givenLocalSnapshotsDisabledWhenProvideTransactionPrunerThenNull() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsPruningEnabled()).thenReturn(true);
        assertNull("instance should be null because of configuration", testInjector(config).getInstance(TransactionPruner.class));
    }

    @Test
    public void givenPruningDisabledWhenProvideTransactionPrunerThenNull() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        assertNull("instance should be null because of configuration", testInjector(config).getInstance(TransactionPruner.class));
    }

    @Test
    public void givenLocalSnapshotsDisabledWhenProvideLocalSnapshotManagerThenNull() {
        assertNull("instance should be null because of configuration", testInjector().getInstance(LocalSnapshotManager.class));
    }

    @Test
    public void provideLocalSnapshotManager() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        assertNotNull("instance creation did not work", testInjector(config).getInstance(LocalSnapshotManager.class));
    }

    @Test
    public void provideTransactionValidator() {
        assertNotNull("instance creation did not work", testInjector().getInstance(TransactionValidator.class));
    }

    @Test
    public void provideIota() {
        assertNotNull("instance creation did not work", testInjector().getInstance(Iota.class));
    }

    @Test
    public void provideIxi() {
        assertNotNull("instance creation did not work", testInjector().getInstance(IXI.class));
    }

    @Test
    public void provideApi() {
        assertNotNull("instance creation did not work", testInjector().getInstance(API.class));
    }

    @Test
    public void provideLocalSnapshotsPersistenceProvider(){
        assertNotNull("instance creation did not work", testInjector().getInstance(LocalSnapshotsPersistenceProvider.class));
    }

    @Test
    public void configure() {
        Injector injector = testInjector();
        assertNotNull("instance creation did not work", injector.getInstance(Tangle.class));
        assertNotNull("instance creation did not work", injector.getInstance(BundleValidator.class));
        assertNotNull("instance creation did not work", injector.getInstance(TipsViewModel.class));
    }

    private Injector testInjector() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getCoordinator()).thenReturn(BaseIotaConfig.Defaults.COORDINATOR);
        return testInjector(config);
    }

    private Injector testInjector(IotaConfig config) {
        return Guice.createInjector(new MainInjectionConfiguration(config), new MockedDependencies());
    }

    private class MockedDependencies extends AbstractModule {

        @Override
        protected void configure() {
            bind(TransactionRequester.class).toInstance(mock(TransactionRequester.class));
            bind(TipsRequester.class).toInstance(mock(TipsRequester.class));
            bind(TransactionProcessingPipeline.class).toInstance(mock(TransactionProcessingPipeline.class));
            bind(NeighborRouter.class).toInstance(mock(NeighborRouter.class));
        }

    }

}