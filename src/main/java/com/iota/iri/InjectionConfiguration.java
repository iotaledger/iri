package com.iota.iri;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.TransactionRequesterWorker;
import com.iota.iri.network.UDPReceiver;
import com.iota.iri.network.impl.TransactionRequesterWorkerImpl;
import com.iota.iri.network.replicator.Replicator;
import com.iota.iri.service.API;
import com.iota.iri.service.TipsSolidifier;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.ledger.impl.LedgerServiceImpl;
import com.iota.iri.service.milestone.*;
import com.iota.iri.service.milestone.impl.*;
import com.iota.iri.service.snapshot.LocalSnapshotManager;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.snapshot.impl.LocalSnapshotManagerImpl;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.service.snapshot.impl.SnapshotServiceImpl;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesService;
import com.iota.iri.service.spentaddresses.impl.SpentAddressesProviderImpl;
import com.iota.iri.service.spentaddresses.impl.SpentAddressesServiceImpl;
import com.iota.iri.service.tipselection.*;
import com.iota.iri.service.tipselection.impl.*;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.async.AsyncTransactionPruner;
import com.iota.iri.storage.Tangle;

import javax.annotation.Nullable;
import java.security.SecureRandom;

public class InjectionConfiguration extends AbstractModule {

    private final IotaConfig configuration;

    public InjectionConfiguration(IotaConfig configuration) {
        this.configuration = configuration;
    }

    @Singleton
    @Provides
    public SnapshotProvider provideSnapshotProvider() {
        return new SnapshotProviderImpl(configuration);
    }

    @Singleton
    @Provides
    public SpentAddressesProvider provideSpentAddressesProvider() {
        return new SpentAddressesProviderImpl(configuration);
    }

    @Singleton
    @Provides
    public SnapshotService provideSnapshotService(Tangle tangle, SnapshotProvider snapshotProvider) {
        return new SnapshotServiceImpl(tangle, snapshotProvider, configuration);
    }

    @Singleton
    @Provides
    public TransactionRequester provideTransactionRequester(Tangle tangle, SnapshotProvider snapshotProvider) {
        return new TransactionRequester(tangle, snapshotProvider, configuration.getpRemoveRequest());
    }

    @Singleton
    @Provides
    public MilestoneService provideMilestoneService(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, BundleValidator bundleValidator) {
        return new MilestoneServiceImpl(tangle, snapshotProvider, snapshotService, bundleValidator, configuration);
    }

    @Singleton
    @Provides
    public SpentAddressesService provideSpentAddressesService(Tangle tangle, SnapshotProvider snapshotProvider, SpentAddressesProvider spentAddressesProvider, BundleValidator bundleValidator) {
        return new SpentAddressesServiceImpl(tangle, snapshotProvider, spentAddressesProvider, bundleValidator, configuration);
    }

    @Singleton
    @Provides
    public LedgerService provideLedgerService(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService,  MilestoneService milestoneService, SpentAddressesService spentAddressesService, BundleValidator bundleValidator) {
        return new LedgerServiceImpl(tangle, snapshotProvider, snapshotService, milestoneService, spentAddressesService, bundleValidator);
    }

    @Singleton
    @Provides
    public LatestMilestoneTracker provideLatestMilestoneTracker(Tangle tangle, SnapshotProvider snapshotProvider, MilestoneService milestoneService, MilestoneSolidifier milestoneSolidifier) {
        return new LatestMilestoneTrackerImpl(tangle, snapshotProvider, milestoneService, milestoneSolidifier, configuration);
    }

    @Singleton
    @Provides
    public LatestSolidMilestoneTracker provideLatestSolidMilestoneTracker(Tangle tangle, SnapshotProvider snapshotProvider, MilestoneService milestoneService, LedgerService ledgerService, LatestMilestoneTracker latestMilestoneTracker) {
        return new LatestSolidMilestoneTrackerImpl(tangle, snapshotProvider, milestoneService, ledgerService, latestMilestoneTracker);
    }

    @Singleton
    @Provides
    public SeenMilestonesRetriever provideSeenMilestonesRetriever(Tangle tangle, SnapshotProvider snapshotProvider, TransactionRequester transactionRequester) {
        return new SeenMilestonesRetrieverImpl(tangle, snapshotProvider, transactionRequester);
    }

    @Singleton
    @Provides
    public MilestoneSolidifier provideMilestoneSolidifier(SnapshotProvider snapshotProvider, TransactionValidator transactionValidator) {
        return new MilestoneSolidifierImpl(snapshotProvider, transactionValidator);
    }

    @Singleton
    @Provides
    public TransactionPruner provideTransactionPruner(Tangle tangle, SnapshotProvider snapshotProvider, SpentAddressesService spentAddressesService, SpentAddressesProvider spentAddressesProvider, TipsViewModel tipsViewModel) {
        return configuration.getLocalSnapshotsEnabled() && configuration.getLocalSnapshotsPruningEnabled()
                ? new AsyncTransactionPruner(tangle, snapshotProvider, spentAddressesService, spentAddressesProvider, tipsViewModel, configuration)
                : null;
    }

    @Singleton
    @Provides
    public LocalSnapshotManager provideLocalSnapshotManager(SnapshotProvider snapshotProvider, SnapshotService snapshotService, @Nullable TransactionPruner transactionPruner) {
        return configuration.getLocalSnapshotsEnabled()
                ? new LocalSnapshotManagerImpl(snapshotProvider, snapshotService, transactionPruner, configuration)
                : null;
    }

    @Singleton
    @Provides
    public TransactionRequesterWorker provideTransactionRequesterWorker(Tangle tangle, TransactionRequester transactionRequester, TipsViewModel tipsViewModel, Node node) {
        return new TransactionRequesterWorkerImpl(tangle, transactionRequester, tipsViewModel, node);
    }

    @Singleton
    @Provides
    public TransactionValidator provideTransactionValidator(Tangle tangle, SnapshotProvider snapshotProvider, TipsViewModel tipsViewModel, TransactionRequester transactionRequester) {
        return new TransactionValidator(tangle, snapshotProvider, tipsViewModel, transactionRequester, configuration.isTestnet(), configuration.getMwm());
    }

    @Singleton
    @Provides
    public Node provideNode(Tangle tangle, SnapshotProvider snapshotProvider, TransactionValidator transactionValidator, TransactionRequester transactionRequester, TipsViewModel tipsViewModel, LatestMilestoneTracker latestMilestoneTracker) {
        return new Node(tangle, snapshotProvider, transactionValidator, transactionRequester, tipsViewModel, latestMilestoneTracker, configuration);
    }

    @Singleton
    @Provides
    public Replicator provideReplicator(Node node) {
        return new Replicator(node, configuration);
    }

    @Singleton
    @Provides
    public UDPReceiver provideUdpReceiver(Node node) {
        return new UDPReceiver(node, configuration);
    }

    @Singleton
    @Provides
    public TipsSolidifier provideTipsSolidifier(Tangle tangle,  TransactionValidator transactionValidator, TipsViewModel tipsViewModel) {
        return new TipsSolidifier(tangle, transactionValidator, tipsViewModel, configuration);
    }

    @Singleton
    @Provides
    private TipSelector providesTipSelector(Tangle tangle, SnapshotProvider snapshotProvider,
                                            LatestMilestoneTracker latestMilestoneTracker, LedgerService ledgerService) {
        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, snapshotProvider,
                latestMilestoneTracker);
        RatingCalculator ratingCalculator = new CumulativeWeightCalculator(tangle, snapshotProvider);
        TailFinder tailFinder = new TailFinderImpl(tangle);
        Walker walker = new WalkerAlpha(tailFinder, tangle, new SecureRandom(), configuration);
        return new TipSelectorImpl(tangle, snapshotProvider, ledgerService, entryPointSelector, ratingCalculator,
                walker, configuration);
    }

    @Singleton
    @Provides
    public Iota provideIota(SpentAddressesProvider spentAddressesProvider, SpentAddressesService spentAddressesService, SnapshotProvider snapshotProvider, SnapshotService snapshotService, @Nullable LocalSnapshotManager localSnapshotManager, MilestoneService milestoneService, LatestMilestoneTracker latestMilestoneTracker, LatestSolidMilestoneTracker latestSolidMilestoneTracker, SeenMilestonesRetriever seenMilestonesRetriever, LedgerService ledgerService, @Nullable TransactionPruner transactionPruner, MilestoneSolidifier milestoneSolidifier, TransactionRequesterWorker transactionRequesterWorker, BundleValidator bundleValidator, Tangle tangle, TransactionValidator transactionValidator, TipsSolidifier tipsSolidifier, TransactionRequester transactionRequester, Node node, UDPReceiver udpReceiver, Replicator replicator, TipsViewModel tipsViewModel, TipSelector tipsSelector) {
        return new Iota(configuration, spentAddressesProvider, spentAddressesService, snapshotProvider, snapshotService, localSnapshotManager, milestoneService, latestMilestoneTracker, latestSolidMilestoneTracker, seenMilestonesRetriever, ledgerService, transactionPruner, milestoneSolidifier, transactionRequesterWorker, bundleValidator, tangle, transactionValidator, tipsSolidifier, transactionRequester, node, udpReceiver, replicator, tipsViewModel, tipsSelector);
    }

    @Singleton
    @Provides
    public IXI provideIxi(Iota iota) {
        return new IXI(iota);
    }

    @Singleton
    @Provides
    public API provideApi(IXI ixi, TransactionRequester transactionRequester,
                          SpentAddressesService spentAddressesService, Tangle tangle, BundleValidator bundleValidator,
                          SnapshotProvider snapshotProvider, LedgerService ledgerService, Node node, TipSelector tipsSelector,
                          TipsViewModel tipsViewModel, TransactionValidator transactionValidator,
                          LatestMilestoneTracker latestMilestoneTracker) {
        return new API(configuration, ixi, transactionRequester, spentAddressesService, tangle, bundleValidator, snapshotProvider, ledgerService, node, tipsSelector, tipsViewModel, transactionValidator, latestMilestoneTracker);
    }

    @Override
    protected void configure() {
        bind(Tangle.class).asEagerSingleton();
        bind(BundleValidator.class).asEagerSingleton();
        bind(TipsViewModel.class).asEagerSingleton();
    }

}
