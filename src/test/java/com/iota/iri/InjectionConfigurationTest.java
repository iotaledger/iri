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
import com.iota.iri.service.milestone.*;
import com.iota.iri.service.snapshot.LocalSnapshotManager;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesService;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.storage.Tangle;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InjectionConfigurationTest {

    @Test
    public void provideSnapshotProvider() {
        assertNotNull(testInjector().getInstance(SnapshotProvider.class));
    }

    @Test
    public void provideSpentAddressesProvider() {
        assertNotNull(testInjector().getInstance(SpentAddressesProvider.class));
    }

    @Test
    public void provideSnapshotService() {
        assertNotNull(testInjector().getInstance(SnapshotService.class));
    }

    @Test
    public void provideTransactionRequester() {
        assertNotNull(testInjector().getInstance(TransactionRequester.class));
    }

    @Test
    public void provideMilestoneService() {
        assertNotNull(testInjector().getInstance(MilestoneService.class));
    }

    @Test
    public void provideSpentAddressesService() {
        assertNotNull(testInjector().getInstance(SpentAddressesService.class));
    }

    @Test
    public void provideLedgerService() {
        assertNotNull(testInjector().getInstance(LedgerService.class));
    }

    @Test
    public void provideLatestMilestoneTracker() {
        assertNotNull(testInjector().getInstance(LatestMilestoneTracker.class));
    }

    @Test
    public void provideLatestSolidMilestoneTracker() {
        assertNotNull(testInjector().getInstance(LatestSolidMilestoneTracker.class));
    }

    @Test
    public void provideSeenMilestonesRetriever() {
        assertNotNull(testInjector().getInstance(SeenMilestonesRetriever.class));
    }

    @Test
    public void provideMilestoneSolidifier() {
        assertNotNull(testInjector().getInstance(MilestoneSolidifier.class));
    }

    @Test
    public void provideTransactionPruner() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsPruningEnabled()).thenReturn(true);
        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        Injector injector = Guice.createInjector(new InjectionConfiguration(config));
        assertNotNull(injector.getInstance(TransactionPruner.class));
    }

    @Test
    public void givenLocalSnapshotsDisabledWhenProvideTransactionPrunerThenNull() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsPruningEnabled()).thenReturn(true);
        Injector injector = Guice.createInjector(new InjectionConfiguration(config));
        assertNull(injector.getInstance(TransactionPruner.class));
    }

    @Test
    public void givenPruningDisabledWhenProvideTransactionPrunerThenNull() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        Injector injector = Guice.createInjector(new InjectionConfiguration(config));
        assertNull(injector.getInstance(TransactionPruner.class));
    }

    @Test
    public void givenLocalSnapshotsDisabledWhenProvideLocalSnapshotManagerThenNull() {
        assertNull(testInjector().getInstance(LocalSnapshotManager.class));
    }

    @Test
    public void provideLocalSnapshotManager() {
        IotaConfig config = mock(IotaConfig.class);
        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        Injector injector = Guice.createInjector(new InjectionConfiguration(config));
        assertNotNull(injector.getInstance(LocalSnapshotManager.class));
    }

    @Test
    public void provideTransactionRequesterWorker() {
        assertNotNull(testInjector().getInstance(TransactionRequesterWorker.class));
    }

    @Test
    public void provideTransactionValidator() {
        assertNotNull(testInjector().getInstance(TransactionValidator.class));
    }

    @Test
    public void provideNode() {
        assertNotNull(testInjector().getInstance(Node.class));
    }

    @Test
    public void provideReplicator() {
        assertNotNull(testInjector().getInstance(Replicator.class));
    }

    @Test
    public void provideUdpReceiver() {
        assertNotNull(testInjector().getInstance(UDPReceiver.class));
    }

    @Test
    public void provideTipsSolidifier() {
        assertNotNull(testInjector().getInstance(TipsSolidifier.class));
    }

    @Test
    public void provideIota() {
        assertNotNull(testInjector().getInstance(Iota.class));
    }

    @Test
    public void provideIxi() {
        assertNotNull(testInjector().getInstance(IXI.class));
    }

    @Test
    public void provideApi() {
        assertNotNull(testInjector().getInstance(API.class));
    }

    @Test
    public void configure() {
        Injector injector = testInjector();
        assertNotNull(injector.getInstance(Tangle.class));
        assertNotNull(injector.getInstance(BundleValidator.class));
        assertNotNull(injector.getInstance(TipsViewModel.class));
    }

    private Injector testInjector() {
        return Guice.createInjector(new InjectionConfiguration(mock(IotaConfig.class)));
    }

}