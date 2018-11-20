package com.iota.iri;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.UDPReceiver;
import com.iota.iri.network.replicator.Replicator;
import com.iota.iri.service.TipsSolidifier;
import com.iota.iri.service.ledger.impl.LedgerServiceImpl;
import com.iota.iri.service.milestone.impl.LatestMilestoneTrackerImpl;
import com.iota.iri.service.milestone.impl.LatestSolidMilestoneTrackerImpl;
import com.iota.iri.service.milestone.impl.MilestoneServiceImpl;
import com.iota.iri.service.milestone.impl.MilestoneSolidifierImpl;
import com.iota.iri.service.milestone.impl.SeenMilestonesRetrieverImpl;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.LocalSnapshotManagerImpl;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.service.snapshot.impl.SnapshotServiceImpl;
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
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.service.transactionpruning.async.AsyncTransactionPruner;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.ZmqPublishProvider;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Pair;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

/**
 *
 * The main class of IRI. This will propagate transactions into and throughout the network.
 * This data is stored as a {@link Tangle}, a form of a Directed acyclic graph.
 * All incoming data will be stored in one or more implementations of {@link PersistenceProvider}.
 *
 * <p>
 *     During initialization, all the Providers can be set to rescan or revalidate their transactions.
 *     After initialization, an asynchronous process has started which will process inbound and outbound transactions.
 *     Each full node should be peered with 7-9 other full nodes (neighbors) to function optimally.
 * </p>
 * <p>
 *     If this node has no Neighbors defined, no data is transferred.
 *     However, if the node has Neighbors, but no Internet connection,
 *     synchronization will continue after Internet connection is established.
 *     Any transactions sent to this node in its local network will then be processed.
 *     This makes IRI able to run partially offline if an already existing database exists on this node.
 * </p>
 * <p>
 *     Validation of a transaction is the process by which other devices choose the transaction.
 *     This is done via a {@link TipSelector} algorithm, after which the transaction performs
 *     the necessary proof-of-work in order to cast their vote of confirmation/approval upon those tips. <br/>
 *
 *     As many other transactions repeat this process on top of each other,
 *     validation of the transaction in question slowly builds up enough verifications.
 *     Eventually this will reach a minimum acceptable verification threshold.
 *     This threshold is determined by the recipient of the transaction.
 *     When this minimum threshold is reached, the transaction is "confirmed".
 * </p>
 *
 */
public class Iota {
    private static final Logger log = LoggerFactory.getLogger(Iota.class);

    public final SnapshotProviderImpl snapshotProvider;

    public final SnapshotServiceImpl snapshotService;

    public final LocalSnapshotManagerImpl localSnapshotManager;

    public final MilestoneServiceImpl milestoneService;

    public final LatestMilestoneTrackerImpl latestMilestoneTracker;

    public final LatestSolidMilestoneTrackerImpl latestSolidMilestoneTracker;

    public final SeenMilestonesRetrieverImpl seenMilestonesRetriever;

    public final LedgerServiceImpl ledgerService = new LedgerServiceImpl();

    public final AsyncTransactionPruner transactionPruner;

    public final MilestoneSolidifierImpl milestoneSolidifier;

    public final Tangle tangle;
    public final TransactionValidator transactionValidator;
    public final TipsSolidifier tipsSolidifier;
    public final TransactionRequester transactionRequester;
    public final Node node;
    public final UDPReceiver udpReceiver;
    public final Replicator replicator;
    public final IotaConfig configuration;
    public final TipsViewModel tipsViewModel;
    public final MessageQ messageQ;
    public final TipSelector tipsSelector;

    /**
     * Initializes the latest snapshot and then creates all services needed to run an IOTA node.
     *
     * @param configuration Information about how this node will be configured.
     * @throws TransactionPruningException If the TransactionPruner could not restore its state.
     * @throws SnapshotException If the Snapshot fails to initialize.
     *                           This can happen if the snapshot signature is invalid or the file cannot be read.
     */
    public Iota(IotaConfig configuration) throws TransactionPruningException, SnapshotException {
        this.configuration = configuration;

        // new refactored instances
        snapshotProvider = new SnapshotProviderImpl();
        snapshotService = new SnapshotServiceImpl();
        localSnapshotManager = new LocalSnapshotManagerImpl();
        milestoneService = new MilestoneServiceImpl();
        latestMilestoneTracker = new LatestMilestoneTrackerImpl();
        latestSolidMilestoneTracker = new LatestSolidMilestoneTrackerImpl();
        seenMilestonesRetriever = new SeenMilestonesRetrieverImpl();
        milestoneSolidifier = new MilestoneSolidifierImpl();
        transactionPruner = new AsyncTransactionPruner();

        // legacy code
        tangle = new Tangle();
        messageQ = MessageQ.createWith(configuration);
        tipsViewModel = new TipsViewModel();
        transactionRequester = new TransactionRequester(tangle, snapshotProvider, messageQ);
        transactionValidator = new TransactionValidator(tangle, snapshotProvider, tipsViewModel, transactionRequester);
        node = new Node(tangle, snapshotProvider, transactionValidator, transactionRequester, tipsViewModel,
                latestMilestoneTracker, messageQ, configuration);
        replicator = new Replicator(node, configuration);
        udpReceiver = new UDPReceiver(node, configuration);
        tipsSolidifier = new TipsSolidifier(tangle, transactionValidator, tipsViewModel);
        tipsSelector = createTipSelector(configuration);

        injectDependencies();
    }

    /**
     * Adds all database providers, and starts initialization of our services.
     * According to the {@link IotaConfig}, data is optionally cleared, reprocessed and reverified.<br/>
     * After this function, incoming and outbound transaction processing has started.
     *
     * @throws Exception If along the way a service fails to initialize.
     *                   Most common cause is a file read or database error.
     */
    public void init() throws Exception {
        initializeTangle();
        tangle.init();

        if (configuration.isRescanDb()){
            rescanDb();
        }

        if (configuration.isRevalidate()) {
            tangle.clearColumn(com.iota.iri.model.persistables.Milestone.class);
            tangle.clearColumn(com.iota.iri.model.StateDiff.class);
            tangle.clearMetadata(com.iota.iri.model.persistables.Transaction.class);
        }

        transactionValidator.init(configuration.isTestnet(), configuration.getMwm());
        tipsSolidifier.init();
        transactionRequester.init(configuration.getpRemoveRequest());
        udpReceiver.init();
        replicator.init();
        node.init();

        latestMilestoneTracker.start();
        latestSolidMilestoneTracker.start();
        seenMilestonesRetriever.start();
        milestoneSolidifier.start();

        if (configuration.getLocalSnapshotsEnabled()) {
            localSnapshotManager.start(latestMilestoneTracker);

            if (configuration.getLocalSnapshotsPruningEnabled()) {
                 transactionPruner.start();
            }
        }
    }

    private void injectDependencies() throws SnapshotException, TransactionPruningException {
        snapshotProvider.init(configuration);
        snapshotService.init(tangle, snapshotProvider, configuration);
        localSnapshotManager.init(snapshotProvider, snapshotService, transactionPruner, configuration);
        milestoneService.init(tangle, snapshotProvider, messageQ, configuration);
        latestMilestoneTracker.init(tangle, snapshotProvider, milestoneService, milestoneSolidifier,
                messageQ, configuration);
        latestSolidMilestoneTracker.init(tangle, snapshotProvider, ledgerService, latestMilestoneTracker, messageQ);
        seenMilestonesRetriever.init(tangle, snapshotProvider, transactionRequester);
        milestoneSolidifier.init(snapshotProvider, transactionValidator);
        ledgerService.init(tangle, snapshotProvider, snapshotService, milestoneService);
        transactionPruner.init(tangle, snapshotProvider, tipsViewModel, configuration).restoreState();
    }

    private void rescanDb() throws Exception {
        //delete all transaction indexes
        tangle.clearColumn(com.iota.iri.model.persistables.Address.class);
        tangle.clearColumn(com.iota.iri.model.persistables.Bundle.class);
        tangle.clearColumn(com.iota.iri.model.persistables.Approvee.class);
        tangle.clearColumn(com.iota.iri.model.persistables.ObsoleteTag.class);
        tangle.clearColumn(com.iota.iri.model.persistables.Tag.class);
        tangle.clearColumn(com.iota.iri.model.persistables.Milestone.class);
        tangle.clearColumn(com.iota.iri.model.StateDiff.class);
        tangle.clearMetadata(com.iota.iri.model.persistables.Transaction.class);

        //rescan all tx & refill the columns
        TransactionViewModel tx = TransactionViewModel.first(tangle);
        int counter = 0;
        while (tx != null) {
            if (++counter % 10000 == 0) {
                log.info("Rescanned {} Transactions", counter);
            }
            List<Pair<Indexable, Persistable>> saveBatch = tx.getSaveBatch();
            saveBatch.remove(5);
            tangle.saveBatch(saveBatch);
            tx = tx.next(tangle);
        }
    }

    /**
     * Gracefully shuts down by calling <tt>shutdown()</tt> on all used services.
     * Exceptions during shutdown are not caught.
     */
    public void shutdown() throws Exception {
        tipsSolidifier.shutdown();
        node.shutdown();
        udpReceiver.shutdown();
        replicator.shutdown();
        transactionValidator.shutdown();
        tangle.shutdown();
        messageQ.shutdown();

        // shutdown in reverse starting order (to not break any dependencies)
        milestoneSolidifier.shutdown();
        seenMilestonesRetriever.shutdown();
        latestSolidMilestoneTracker.shutdown();
        latestMilestoneTracker.shutdown();
        snapshotProvider.shutdown();

        if (configuration.getLocalSnapshotsEnabled()) {
            localSnapshotManager.shutdown();

            if (configuration.getLocalSnapshotsPruningEnabled()) {
                transactionPruner.shutdown();
            }
        }
    }

    private void initializeTangle() {
        switch (configuration.getMainDb()) {
            case "rocksdb": {
                tangle.addPersistenceProvider(new RocksDBPersistenceProvider(
                        configuration.getDbPath(),
                        configuration.getDbLogPath(),
                        configuration.getDbCacheSize()));
                break;
            }
            default: {
                throw new NotImplementedException("No such database type.");
            }
        }
        if (configuration.isZmqEnabled()) {
            tangle.addPersistenceProvider(new ZmqPublishProvider(messageQ));
        }
    }

    private TipSelector createTipSelector(TipSelConfig config) {
        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, snapshotProvider,
                latestMilestoneTracker);
        RatingCalculator ratingCalculator = new CumulativeWeightCalculator(tangle, snapshotProvider);
        TailFinder tailFinder = new TailFinderImpl(tangle);
        Walker walker = new WalkerAlpha(tailFinder, tangle, messageQ, new SecureRandom(), config);
        return new TipSelectorImpl(tangle, snapshotProvider, ledgerService, entryPointSelector, ratingCalculator,
                walker, config);
    }
}
