package com.iota.iri;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.TransactionRequesterWorker;
import com.iota.iri.network.UDPReceiver;
import com.iota.iri.network.replicator.Replicator;
import com.iota.iri.service.API;
import com.iota.iri.service.TipsSolidifier;
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
import com.iota.iri.storage.Tangle;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InjectionConfigurationTest {

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
    public void provideTransactionRequester() {
        assertNotNull("instance creation did not work", testInjector().getInstance(TransactionRequester.class));
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
        Injector injector = Guice.createInjector(new InjectionConfiguration(config));
        assertNotNull("instance creation did not work", injector.getInstance(TransactionPruner.class));
    }

    @Test
    public void givenLocalSnapshotsDisabledWhenProvideTransactionPrunerThenNull() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsPruningEnabled()).thenReturn(true);
        Injector injector = Guice.createInjector(new InjectionConfiguration(config));
        assertNull("instance should be null because of configuration", injector.getInstance(TransactionPruner.class));
    }

    @Test
    public void givenPruningDisabledWhenProvideTransactionPrunerThenNull() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        Injector injector = Guice.createInjector(new InjectionConfiguration(config));
        assertNull("instance should be null because of configuration", injector.getInstance(TransactionPruner.class));
    }

    @Test
    public void givenLocalSnapshotsDisabledWhenProvideLocalSnapshotManagerThenNull() {
        assertNull("instance should be null because of configuration", testInjector().getInstance(LocalSnapshotManager.class));
    }

    @Test
    public void provideLocalSnapshotManager() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        Injector injector = Guice.createInjector(new InjectionConfiguration(config));
        assertNotNull("instance creation did not work", injector.getInstance(LocalSnapshotManager.class));
    }

    @Test
    public void provideTransactionRequesterWorker() {
        assertNotNull("instance creation did not work", testInjector().getInstance(TransactionRequesterWorker.class));
    }

    @Test
    public void provideTransactionValidator() {
        assertNotNull("instance creation did not work", testInjector().getInstance(TransactionValidator.class));
    }

    @Test
    public void provideNode() {
        assertNotNull("instance creation did not work", testInjector().getInstance(Node.class));
    }

    @Test
    public void provideReplicator() {
        assertNotNull("instance creation did not work", testInjector().getInstance(Replicator.class));
    }

    @Test
    public void provideUdpReceiver() {
        assertNotNull("instance creation did not work", testInjector().getInstance(UDPReceiver.class));
    }

    @Test
    public void provideTipsSolidifier() {
        assertNotNull("instance creation did not work", testInjector().getInstance(TipsSolidifier.class));
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
    public void configure() {
        Injector injector = testInjector();
        assertNotNull("instance creation did not work", injector.getInstance(Tangle.class));
        assertNotNull("instance creation did not work", injector.getInstance(BundleValidator.class));
        assertNotNull("instance creation did not work", injector.getInstance(TipsViewModel.class));
    }

    private Injector testInjector() {
        return Guice.createInjector(new InjectionConfiguration(mock(IotaConfig.class)));
    }

}