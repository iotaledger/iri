package com.iota.iri.network;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.network.impl.TipsRequesterImpl;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.pipeline.TransactionProcessingPipelineImpl;
import com.iota.iri.service.milestone.InSyncService;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.service.validation.TransactionValidator;
import com.iota.iri.storage.Tangle;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

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
    TipsRequester provideTipsRequester(NeighborRouter neighborRouter, Tangle tangle, MilestoneSolidifier milestoneSolidifier, TransactionRequester txRequester) {
        return new TipsRequesterImpl(neighborRouter, tangle, milestoneSolidifier, txRequester);
    }

    @Singleton
    @Provides
    TransactionProcessingPipeline provideTransactionProcessingPipeline(NeighborRouter neighborRouter,
            TransactionValidator txValidator, Tangle tangle, SnapshotProvider snapshotProvider,
            TipsViewModel tipsViewModel, TransactionRequester transactionRequester,
            TransactionSolidifier transactionSolidifier, MilestoneService milestoneService,
            MilestoneSolidifier milestoneSolidifier, InSyncService inSyncService) {
        return new TransactionProcessingPipelineImpl(neighborRouter, configuration, txValidator, tangle,
                snapshotProvider, tipsViewModel, milestoneSolidifier, transactionRequester, transactionSolidifier,
                milestoneService, inSyncService);
    }

    @Singleton
    @Provides
    NeighborRouter provideNeighborRouter(TransactionRequester transactionRequester, TransactionProcessingPipeline transactionProcessingPipeline) {
        return new NeighborRouterImpl(configuration, configuration, transactionRequester, transactionProcessingPipeline);
    }

}
