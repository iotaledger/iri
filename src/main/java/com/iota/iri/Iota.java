package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.*;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.UDPReceiver;
import com.iota.iri.network.replicator.Replicator;
import com.iota.iri.service.TipsSolidifier;
import com.iota.iri.service.tipselection.TipSelector;
import com.iota.iri.service.tipselection.impl.TipSelectorImpl;
import com.iota.iri.storage.*;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Pair;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Created by paul on 5/19/17.
 */
public class Iota {
    private static final Logger log = LoggerFactory.getLogger(Iota.class);

    public final LedgerValidator ledgerValidator;
    public final Milestone milestone;
    public final Tangle tangle;
    public final TransactionValidator transactionValidator;
    public final TipsSolidifier tipsSolidifier;
    public final TransactionRequester transactionRequester;
    public final Node node;
    public final UDPReceiver udpReceiver;
    public final Replicator replicator;
    public final Configuration configuration;
    public final Hash coordinator;
    public final TipsViewModel tipsViewModel;
    public final MessageQ messageQ;
    public final TipSelector tipsSelector;

    public final boolean testnet;
    public final int maxPeers;
    public final int udpPort;
    public final int tcpPort;
    public final int maxTipSearchDepth;

    public Iota(Configuration configuration) throws IOException {
        this.configuration = configuration;
        testnet = configuration.booling(Configuration.DefaultConfSettings.TESTNET);
        maxPeers = configuration.integer(Configuration.DefaultConfSettings.MAX_PEERS);
        udpPort = configuration.integer(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT);
        tcpPort = configuration.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);

        String snapshotFile = configuration.string(Configuration.DefaultConfSettings.SNAPSHOT_FILE);
        String snapshotSigFile = configuration.string(Configuration.DefaultConfSettings.SNAPSHOT_SIGNATURE_FILE);
        Snapshot initialSnapshot = Snapshot.init(snapshotFile, snapshotSigFile, testnet).clone();
        long snapshotTimestamp = configuration.longNum(Configuration.DefaultConfSettings.SNAPSHOT_TIME);
        int milestoneStartIndex = configuration.integer(Configuration.DefaultConfSettings.MILESTONE_START_INDEX);
        int numKeysMilestone = configuration.integer(Configuration.DefaultConfSettings.NUMBER_OF_KEYS_IN_A_MILESTONE);
        double alpha = configuration.doubling(Configuration.DefaultConfSettings.TIPSELECTION_ALPHA.name());

        boolean dontValidateMilestoneSig = configuration.booling(Configuration.DefaultConfSettings
                .DONT_VALIDATE_TESTNET_MILESTONE_SIG);
        int transactionPacketSize = configuration.integer(Configuration.DefaultConfSettings.TRANSACTION_PACKET_SIZE);

        maxTipSearchDepth = configuration.integer(Configuration.DefaultConfSettings.MAX_DEPTH);
        if(testnet) {
            String coordinatorTrytes = configuration.string(Configuration.DefaultConfSettings.COORDINATOR);
            if(StringUtils.isNotEmpty(coordinatorTrytes)) {
                coordinator = new Hash(coordinatorTrytes);
            } else {
                log.warn("No coordinator address given for testnet. Defaulting to "
                        + Configuration.TESTNET_COORDINATOR_ADDRESS);
                coordinator = new Hash(Configuration.TESTNET_COORDINATOR_ADDRESS);
            }
        } else {
            coordinator = new Hash(Configuration.MAINNET_COORDINATOR_ADDRESS);
        }
        tangle = new Tangle();
        messageQ = new MessageQ(configuration.integer(Configuration.DefaultConfSettings.ZMQ_PORT),
                configuration.string(Configuration.DefaultConfSettings.ZMQ_IPC),
                configuration.integer(Configuration.DefaultConfSettings.ZMQ_THREADS),
                configuration.booling(Configuration.DefaultConfSettings.ZMQ_ENABLED)
                );
        tipsViewModel = new TipsViewModel();
        transactionRequester = new TransactionRequester(tangle, messageQ);
        transactionValidator = new TransactionValidator(tangle, tipsViewModel, transactionRequester, messageQ,
                snapshotTimestamp);
        milestone = new Milestone(tangle, coordinator, initialSnapshot, transactionValidator, testnet, messageQ,
                numKeysMilestone, milestoneStartIndex, dontValidateMilestoneSig);
        node = new Node(configuration, tangle, transactionValidator, transactionRequester, tipsViewModel, milestone, messageQ);
        replicator = new Replicator(node, tcpPort, maxPeers, testnet, transactionPacketSize);
        udpReceiver = new UDPReceiver(udpPort, node, configuration.integer(Configuration.DefaultConfSettings.TRANSACTION_PACKET_SIZE));
        ledgerValidator = new LedgerValidator(tangle, milestone, transactionRequester, messageQ);
        tipsSolidifier = new TipsSolidifier(tangle, transactionValidator, tipsViewModel);
        tipsSelector = new TipSelectorImpl(tangle, ledgerValidator, transactionValidator, milestone, maxTipSearchDepth, messageQ, testnet, milestoneStartIndex, alpha);
    }

    public void init() throws Exception {
        initializeTangle();
        tangle.init();

        if (configuration.booling(Configuration.DefaultConfSettings.RESCAN_DB)){
            rescan_db();
        }
        boolean revalidate = configuration.booling(Configuration.DefaultConfSettings.REVALIDATE);

        if (revalidate) {
            tangle.clearColumn(com.iota.iri.model.Milestone.class);
            tangle.clearColumn(com.iota.iri.model.StateDiff.class);
            tangle.clearMetadata(com.iota.iri.model.Transaction.class);
        }
        milestone.init(SpongeFactory.Mode.CURLP27, ledgerValidator, revalidate);
        transactionValidator.init(testnet, configuration.integer(Configuration.DefaultConfSettings.MWM));
        tipsSolidifier.init();
        transactionRequester.init(configuration.doubling(Configuration.DefaultConfSettings.P_REMOVE_REQUEST.name()));
        udpReceiver.init();
        replicator.init();
        node.init();
    }

    private void rescan_db() throws Exception {
        //delete all transaction indexes
        tangle.clearColumn(com.iota.iri.model.Address.class);
        tangle.clearColumn(com.iota.iri.model.Bundle.class);
        tangle.clearColumn(com.iota.iri.model.Approvee.class);
        tangle.clearColumn(com.iota.iri.model.ObsoleteTag.class);
        tangle.clearColumn(com.iota.iri.model.Tag.class);
        tangle.clearColumn(com.iota.iri.model.Milestone.class);
        tangle.clearColumn(com.iota.iri.model.StateDiff.class);
        tangle.clearMetadata(com.iota.iri.model.Transaction.class);

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

    public void shutdown() throws Exception {
        milestone.shutDown();
        tipsSolidifier.shutdown();
        node.shutdown();
        udpReceiver.shutdown();
        replicator.shutdown();
        transactionValidator.shutdown();
        tangle.shutdown();
        messageQ.shutdown();
    }

    private void initializeTangle() {
        String dbPath = configuration.string(Configuration.DefaultConfSettings.DB_PATH);
        if (testnet) {
            if (dbPath.isEmpty() || dbPath.equals("mainnetdb")) {
                // testnetusers must not use mainnetdb, overwrite it unless an explicit name is set.
                configuration.put(Configuration.DefaultConfSettings.DB_PATH.name(), "testnetdb");
                configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
            }
        } else {
            if (dbPath.isEmpty() || dbPath.equals("testnetdb")) {
                // mainnetusers must not use testnetdb, overwrite it unless an explicit name is set.
                configuration.put(Configuration.DefaultConfSettings.DB_PATH.name(), "mainnetdb");
                configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH.name(), "mainnetdb.log");
            }
        }
        switch (configuration.string(Configuration.DefaultConfSettings.MAIN_DB)) {
            case "rocksdb": {
                tangle.addPersistenceProvider(new RocksDBPersistenceProvider(
                        configuration.string(Configuration.DefaultConfSettings.DB_PATH),
                        configuration.string(Configuration.DefaultConfSettings.DB_LOG_PATH),
                        configuration.integer(Configuration.DefaultConfSettings.DB_CACHE_SIZE)));
                break;
            }
            default: {
                throw new NotImplementedException("No such database type.");
            }
        }
        if (configuration.booling(Configuration.DefaultConfSettings.EXPORT)) {
            tangle.addPersistenceProvider(new FileExportProvider());
        }
        if (configuration.booling(Configuration.DefaultConfSettings.ZMQ_ENABLED)) {
            tangle.addPersistenceProvider(new ZmqPublishProvider(messageQ));
        }
    }
}
