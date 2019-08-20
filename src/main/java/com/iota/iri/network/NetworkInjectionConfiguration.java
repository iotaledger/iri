package com.iota.iri.network;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.network.impl.TipsRequesterImpl;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.pipeline.TransactionProcessingPipelineImpl;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

/**
 * Guice module for network package. Configuration class for dependency injection.
 */
public class NetworkInjectionConfiguration extends AbstractModule {

    private final IotaConfig configuration;

    /**
     * Creates the guice injection module.
     * @param configuration The iota configuration used for conditional bean creation and constructing beans with
     *                      configuration parameters.
     */
    public NetworkInjectionConfiguration(IotaConfig configuration) {
        this.configuration = configuration;
    }

    @Singleton
    @Provides
    TransactionRequester provideTransactionRequester(Tangle tangle, SnapshotProvider snapshotProvider) {
        return new TransactionRequester(tangle, snapshotProvider);
    }

    @Singleton
    @Provides
    TipsRequester provideTipsRequester(NeighborRouter neighborRouter, Tangle tangle, LatestMilestoneTracker latestMilestoneTracker, TransactionRequester txRequester) {
        return new TipsRequesterImpl(neighborRouter, tangle, latestMilestoneTracker, txRequester);
    }

    @Singleton
    @Provides
    TransactionProcessingPipeline provideTransactionProcessingPipeline(NeighborRouter neighborRouter,
            TransactionValidator txValidator, Tangle tangle, SnapshotProvider snapshotProvider,
            TipsViewModel tipsViewModel, LatestMilestoneTracker latestMilestoneTracker,
            TransactionRequester transactionRequester) {
        return new TransactionProcessingPipelineImpl(neighborRouter, configuration, txValidator, tangle,
                snapshotProvider, tipsViewModel, latestMilestoneTracker, transactionRequester);
    }

    @Singleton
    @Provides
    NeighborRouter provideNeighborRouter(TransactionRequester transactionRequester, TransactionProcessingPipeline transactionProcessingPipeline) {
        return new NeighborRouterImpl(configuration, configuration, transactionRequester, transactionProcessingPipeline);
    }

}
