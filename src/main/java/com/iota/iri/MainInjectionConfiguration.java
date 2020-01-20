package com.iota.iri;

import java.security.SecureRandom;
import javax.annotation.Nullable;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.TipsRequester;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.service.API;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.ledger.impl.LedgerServiceImpl;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.milestone.LatestSolidMilestoneTracker;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.milestone.SeenMilestonesRetriever;
import com.iota.iri.service.milestone.impl.LatestMilestoneTrackerImpl;
import com.iota.iri.service.milestone.impl.LatestSolidMilestoneTrackerImpl;
import com.iota.iri.service.milestone.impl.MilestoneServiceImpl;
import com.iota.iri.service.milestone.impl.MilestoneSolidifierImpl;
import com.iota.iri.service.milestone.impl.SeenMilestonesRetrieverImpl;
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
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.service.tipselection.TipSelector;
import com.iota.iri.service.tipselection.Walker;
import com.iota.iri.service.tipselection.impl.CumulativeWeightCalculator;
import com.iota.iri.service.tipselection.impl.EntryPointSelectorImpl;
import com.iota.iri.service.tipselection.impl.TailFinderImpl;
import com.iota.iri.service.tipselection.impl.TipSelectorImpl;
import com.iota.iri.service.tipselection.impl.WalkerAlpha;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.async.AsyncTransactionPruner;
import com.iota.iri.storage.LocalSnapshotsPersistenceProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;

/**
 * Guice module. Configuration class for dependency injection.
 */
public class MainInjectionConfiguration extends AbstractModule {

    private final IotaConfig configuration;

    /**
     * Creates the guice injection module.
     * @param configuration The iota configuration used for conditional bean creation and constructing beans with
     *                      configuration parameters.
     */
    public MainInjectionConfiguration(IotaConfig configuration) {
        this.configuration = configuration;
    }

    @Singleton
    @Provides
    SnapshotProvider provideSnapshotProvider(LocalSnapshotsPersistenceProvider localSnapshotsPersistenceProvider) {
        return new SnapshotProviderImpl(configuration, localSnapshotsPersistenceProvider);
    }

    @Singleton
    @Provides
    SpentAddressesProvider provideSpentAddressesProvider(LocalSnapshotsPersistenceProvider localSnapshotsDb) {
        return new SpentAddressesProviderImpl(configuration, localSnapshotsDb);
    }

    @Singleton
    @Provides
    SnapshotService provideSnapshotService(Tangle tangle, SnapshotProvider snapshotProvider) {
        return new SnapshotServiceImpl(tangle, snapshotProvider, configuration);
    }

    @Singleton
    @Provides
    MilestoneService provideMilestoneService(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, BundleValidator bundleValidator) {
        return new MilestoneServiceImpl(tangle, snapshotProvider, snapshotService, bundleValidator, configuration);
    }

    @Singleton
    @Provides
    SpentAddressesService provideSpentAddressesService(Tangle tangle, SnapshotProvider snapshotProvider, SpentAddressesProvider spentAddressesProvider, BundleValidator bundleValidator) {
        return new SpentAddressesServiceImpl(tangle, snapshotProvider, spentAddressesProvider, bundleValidator, configuration);
    }

    @Singleton
    @Provides
    LedgerService provideLedgerService(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, MilestoneService milestoneService, SpentAddressesService spentAddressesService, BundleValidator bundleValidator) {
        return new LedgerServiceImpl(tangle, snapshotProvider, snapshotService, milestoneService, spentAddressesService, bundleValidator);
    }

    @Singleton
    @Provides
    LatestMilestoneTracker provideLatestMilestoneTracker(Tangle tangle, SnapshotProvider snapshotProvider, MilestoneService milestoneService, MilestoneSolidifier milestoneSolidifier) {
        return new LatestMilestoneTrackerImpl(tangle, snapshotProvider, milestoneService, milestoneSolidifier, configuration);
    }

    @Singleton
    @Provides
    LatestSolidMilestoneTracker provideLatestSolidMilestoneTracker(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneService milestoneService, LedgerService ledgerService,
            LatestMilestoneTracker latestMilestoneTracker, TransactionRequester transactionRequester) {
        return new LatestSolidMilestoneTrackerImpl(tangle, snapshotProvider, milestoneService, ledgerService,
                latestMilestoneTracker, transactionRequester, configuration);
    }

    @Singleton
    @Provides
    SeenMilestonesRetriever provideSeenMilestonesRetriever(Tangle tangle, SnapshotProvider snapshotProvider, TransactionRequester transactionRequester) {
        return new SeenMilestonesRetrieverImpl(tangle, snapshotProvider, transactionRequester);
    }

    @Singleton
    @Provides
    MilestoneSolidifier provideMilestoneSolidifier(SnapshotProvider snapshotProvider, TransactionValidator transactionValidator) {
        return new MilestoneSolidifierImpl(snapshotProvider, transactionValidator);
    }

    @Singleton
    @Provides
    TransactionPruner provideTransactionPruner(Tangle tangle, SnapshotProvider snapshotProvider, SpentAddressesService spentAddressesService, SpentAddressesProvider spentAddressesProvider, TipsViewModel tipsViewModel) {
        return configuration.getLocalSnapshotsEnabled() && configuration.getLocalSnapshotsPruningEnabled()
                ? new AsyncTransactionPruner(tangle, snapshotProvider, spentAddressesService, spentAddressesProvider, tipsViewModel, configuration)
                : null;
    }

    @Singleton
    @Provides
    LocalSnapshotManager provideLocalSnapshotManager(SnapshotProvider snapshotProvider, SnapshotService snapshotService, @Nullable TransactionPruner transactionPruner) {
        return configuration.getLocalSnapshotsEnabled()
                ? new LocalSnapshotManagerImpl(snapshotProvider, snapshotService, transactionPruner, configuration)
                : null;
    }

    @Singleton
    @Provides
    TransactionValidator provideTransactionValidator(Tangle tangle, SnapshotProvider snapshotProvider, TipsViewModel tipsViewModel, TransactionRequester transactionRequester) {
        return new TransactionValidator(tangle, snapshotProvider, tipsViewModel, transactionRequester, configuration);
    }

    @Singleton
    @Provides
    TipSelector provideTipSelector(Tangle tangle, SnapshotProvider snapshotProvider,
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
    Iota provideIota(SpentAddressesProvider spentAddressesProvider, SpentAddressesService spentAddressesService, SnapshotProvider snapshotProvider, SnapshotService snapshotService, @Nullable LocalSnapshotManager localSnapshotManager, MilestoneService milestoneService, LatestMilestoneTracker latestMilestoneTracker, LatestSolidMilestoneTracker latestSolidMilestoneTracker, SeenMilestonesRetriever seenMilestonesRetriever, LedgerService ledgerService, @Nullable TransactionPruner transactionPruner, MilestoneSolidifier milestoneSolidifier, BundleValidator bundleValidator, Tangle tangle, TransactionValidator transactionValidator, TransactionRequester transactionRequester, NeighborRouter neighborRouter, TransactionProcessingPipeline transactionProcessingPipeline, TipsRequester tipsRequester, TipsViewModel tipsViewModel, TipSelector tipsSelector, LocalSnapshotsPersistenceProvider localSnapshotsDb) {
        return new Iota(configuration, spentAddressesProvider, spentAddressesService, snapshotProvider, snapshotService, localSnapshotManager, milestoneService, latestMilestoneTracker, latestSolidMilestoneTracker, seenMilestonesRetriever, ledgerService, transactionPruner, milestoneSolidifier, bundleValidator, tangle, transactionValidator, transactionRequester, neighborRouter, transactionProcessingPipeline, tipsRequester, tipsViewModel, tipsSelector, localSnapshotsDb);
    }

    @Singleton
    @Provides
    IXI provideIxi(Iota iota) {
        return new IXI(iota);
    }

    @Singleton
    @Provides
    API provideApi(IXI ixi, TransactionRequester transactionRequester,
                          SpentAddressesService spentAddressesService, Tangle tangle, BundleValidator bundleValidator,
                          SnapshotProvider snapshotProvider, LedgerService ledgerService, NeighborRouter neighborRouter, TipSelector tipsSelector,
                          TipsViewModel tipsViewModel, TransactionValidator transactionValidator,
                          LatestMilestoneTracker latestMilestoneTracker, TransactionProcessingPipeline txPipeline) {
        return new API(configuration, ixi, transactionRequester, spentAddressesService, tangle, bundleValidator, snapshotProvider, ledgerService, neighborRouter, tipsSelector, tipsViewModel, transactionValidator, latestMilestoneTracker, txPipeline);
    }

    @Singleton
    @Provides
    LocalSnapshotsPersistenceProvider provideLocalSnapshotsPersistenceProvider(){
        return new LocalSnapshotsPersistenceProvider(new RocksDBPersistenceProvider(
                configuration.getLocalSnapshotsDbPath(),
                configuration.getLocalSnapshotsDbLogPath(),
                configuration.getDbConfigFile(),
                1000,
                LocalSnapshotsPersistenceProvider.COLUMN_FAMILIES, null));
    }

    @Override
    protected void configure() {
        // beans that only need a default constructor
        bind(Tangle.class).asEagerSingleton();
        bind(BundleValidator.class).asEagerSingleton();
        bind(TipsViewModel.class).asEagerSingleton();
    }
}
