package com.iota.iri;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.TransactionRequesterWorker;
import com.iota.iri.network.UDPReceiver;
import com.iota.iri.network.replicator.Replicator;
import com.iota.iri.service.TipsSolidifier;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.milestone.LatestSolidMilestoneTracker;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.milestone.SeenMilestonesRetriever;
import com.iota.iri.service.snapshot.LocalSnapshotManager;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesService;
import com.iota.iri.service.tipselection.TipSelector;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Pair;

import java.util.List;

import com.iota.iri.zmq.ZmqMessageQueueProvider;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *     the necessary proof-of-work in order to cast their vote of confirmation/approval upon those tips.
 * </p>
 * <p>
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

    private final SpentAddressesProvider spentAddressesProvider;

    private final SpentAddressesService spentAddressesService;

    private final SnapshotProvider snapshotProvider;

    private final SnapshotService snapshotService;

    private final LocalSnapshotManager localSnapshotManager;

    private final MilestoneService milestoneService;

    private final LatestMilestoneTracker latestMilestoneTracker;

    private final LatestSolidMilestoneTracker latestSolidMilestoneTracker;

    private final SeenMilestonesRetriever seenMilestonesRetriever;

    private final LedgerService ledgerService;

    private final TransactionPruner transactionPruner;

    private final MilestoneSolidifier milestoneSolidifier;

    private final TransactionRequesterWorker transactionRequesterWorker;

    private final BundleValidator bundleValidator;

    private final Tangle tangle;
    private final TransactionValidator transactionValidator;
    private final TipsSolidifier tipsSolidifier;
    private final TransactionRequester transactionRequester;
    public final Node node; // used in test
    private final UDPReceiver udpReceiver;
    private final Replicator replicator;
    private final IotaConfig configuration;
    private final TipsViewModel tipsViewModel;
    private final TipSelector tipsSelector;

    /**
     * Initializes the latest snapshot and then creates all services needed to run an IOTA node.
     *
     * @param configuration Information about how this node will be configured.
     *
     */
    public Iota(IotaConfig configuration, SpentAddressesProvider spentAddressesProvider, SpentAddressesService spentAddressesService, SnapshotProvider snapshotProvider, SnapshotService snapshotService, LocalSnapshotManager localSnapshotManager, MilestoneService milestoneService, LatestMilestoneTracker latestMilestoneTracker, LatestSolidMilestoneTracker latestSolidMilestoneTracker, SeenMilestonesRetriever seenMilestonesRetriever, LedgerService ledgerService, TransactionPruner transactionPruner, MilestoneSolidifier milestoneSolidifier, TransactionRequesterWorker transactionRequesterWorker, BundleValidator bundleValidator, Tangle tangle, TransactionValidator transactionValidator, TipsSolidifier tipsSolidifier, TransactionRequester transactionRequester, Node node, UDPReceiver udpReceiver, Replicator replicator, TipsViewModel tipsViewModel, TipSelector tipsSelector) {
        this.configuration = configuration;

        this.ledgerService = ledgerService;
        this.spentAddressesProvider = spentAddressesProvider;
        this.spentAddressesService = spentAddressesService;
        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
        this.localSnapshotManager = localSnapshotManager;
        this.milestoneService = milestoneService;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.latestSolidMilestoneTracker = latestSolidMilestoneTracker;
        this.seenMilestonesRetriever = seenMilestonesRetriever;
        this.milestoneSolidifier = milestoneSolidifier;
        this.transactionPruner = transactionPruner;
        this.transactionRequesterWorker = transactionRequesterWorker;
        // legacy classes
        this.bundleValidator = bundleValidator;
        this.tangle = tangle;
        this.tipsViewModel = tipsViewModel;
        this.transactionRequester = transactionRequester;
        this.transactionValidator = transactionValidator;
        this.node = node;
        this.replicator = replicator;
        this.udpReceiver = udpReceiver;
        this.tipsSolidifier = tipsSolidifier;
        this.tipsSelector = tipsSelector;
    }

    private void initDependencies() throws SnapshotException, SpentAddressesException {
        //snapshot provider must be initialized first
        //because we check whether spent addresses data exists
        snapshotProvider.init();
        spentAddressesProvider.init();
        latestMilestoneTracker.init();
        seenMilestonesRetriever.init();
        if (transactionPruner != null) {
            transactionPruner.init();
        }
    }

    /**
     * <p>
     * Initializes the latest snapshot and
     * adds all database providers, and starts initialization of our services.
     * According to the {@link IotaConfig}, data is optionally cleared, reprocessed and reverified.
     * </p>
     * After this function, incoming and outbound transaction processing has started.
     *
     * @throws SnapshotException If the Snapshot fails to initialize.
     *                           This can happen if the snapshot signature is invalid or the file cannot be read.
     * @throws Exception If along the way a service fails to initialize.
     *                   Most common cause is a file read or database error.
     */
    public void init() throws Exception {
        initDependencies(); // remainder of injectDependencies method (contained init code)

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

        transactionValidator.init();
        tipsSolidifier.init();
        transactionRequester.init();
        udpReceiver.init();
        replicator.init();
        node.init();

        latestMilestoneTracker.start();
        latestSolidMilestoneTracker.start();
        seenMilestonesRetriever.start();
        milestoneSolidifier.start();
        transactionRequesterWorker.start();

        if (localSnapshotManager != null) {
            localSnapshotManager.start(latestMilestoneTracker);
        }
        if (transactionPruner != null) {
            transactionPruner.start();
        }
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
        // shutdown in reverse starting order (to not break any dependencies)
        transactionRequesterWorker.shutdown();
        milestoneSolidifier.shutdown();
        seenMilestonesRetriever.shutdown();
        latestSolidMilestoneTracker.shutdown();
        latestMilestoneTracker.shutdown();

        if (transactionPruner != null) {
            transactionPruner.shutdown();
        }
        if (localSnapshotManager != null) {
            localSnapshotManager.shutdown();
        }

        tipsSolidifier.shutdown();
        node.shutdown();
        udpReceiver.shutdown();
        replicator.shutdown();
        transactionValidator.shutdown();
        tangle.shutdown();

        // free the resources of the snapshot provider last because all other instances need it
        snapshotProvider.shutdown();
    }

    private void initializeTangle() {
        switch (configuration.getMainDb()) {
            case "rocksdb": {
                tangle.addPersistenceProvider(new RocksDBPersistenceProvider(
                        configuration.getDbPath(),
                        configuration.getDbLogPath(),
                        configuration.getDbCacheSize(),
                        Tangle.COLUMN_FAMILIES,
                        Tangle.METADATA_COLUMN_FAMILY)
                );
                break;
            }
            default: {
                throw new NotImplementedException("No such database type.");
            }
        }
        if (configuration.isZmqEnabled()) {
            tangle.addMessageQueueProvider(new ZmqMessageQueueProvider(configuration));
        }
    }

}
