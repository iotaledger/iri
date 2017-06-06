package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.*;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Node;
import com.iota.iri.network.UDPReceiver;
import com.iota.iri.network.replicator.Replicator;
import com.iota.iri.network.replicator.ReplicatorSinkPool;
import com.iota.iri.network.replicator.ReplicatorSourcePool;
import com.iota.iri.service.API;
import com.iota.iri.service.TipsManager;
import com.iota.iri.storage.FileExportProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by paul on 5/19/17.
 */
public class Iota {
    private static final Logger log = LoggerFactory.getLogger(Iota.class);

    public static final Hash MAINNET_COORDINATOR = new Hash("KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU");
    public static final Hash TESTNET_COORDINATOR = new Hash("XNZBYAST9BETSDNOVQKKTBECYIPMF9IPOZRWUPFQGVH9HJW9NDSQVIPVBWU9YKECRYGDSJXYMZGHZDXCA");

    public final LedgerValidator ledgerValidator;
    public final Milestone milestone;
    public final Snapshot latestSnapshot;
    public final Tangle tangle;
    public final TransactionValidator transactionValidator;
    public final TipsManager tipsManager;
    public final TransactionRequester transactionRequester;
    public final Node node;
    public final UDPReceiver udpReceiver;
    public final Replicator replicator;
    public final Configuration configuration;
    public final Hash coordinator;
    public final TipsViewModel tipsViewModel;

    public final boolean testnet;
    public final int maxPeers;
    public final int udpPort;
    public final int tcpPort;

    public Iota(Configuration configuration) {
        this.configuration = configuration;
        testnet = configuration.booling(Configuration.DefaultConfSettings.TESTNET);
        maxPeers = configuration.integer(Configuration.DefaultConfSettings.MAX_PEERS);
        udpPort = configuration.integer(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT);
        tcpPort = configuration.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);
        if(testnet) {
            String coordinatorTrytes = configuration.string(Configuration.DefaultConfSettings.COORDINATOR);
            if(coordinatorTrytes != null) {
                coordinator = new Hash(coordinatorTrytes);
            } else {
                coordinator = TESTNET_COORDINATOR;
            }
        } else {
            coordinator = MAINNET_COORDINATOR;
        }
        tangle = new Tangle();
        tipsViewModel = new TipsViewModel();
        transactionRequester = new TransactionRequester(tangle);
        transactionValidator = new TransactionValidator(tangle, tipsViewModel, transactionRequester);
        latestSnapshot = new Snapshot(Snapshot.initialSnapshot);
        milestone =  new Milestone(tangle, coordinator, transactionValidator, testnet);
        node = new Node(configuration, tangle, transactionValidator, transactionRequester, tipsViewModel, milestone);
        replicator = new Replicator(node, tcpPort, maxPeers, testnet);
        udpReceiver = new UDPReceiver(udpPort, node);
        ledgerValidator = new LedgerValidator(tangle, milestone, transactionRequester);
        tipsManager = new TipsManager(tangle, ledgerValidator, transactionValidator, tipsViewModel, milestone);
    }

    public void init() throws Exception {
        initializeTangle();
        tangle.init();

        if (configuration.booling(Configuration.DefaultConfSettings.RESCAN_DB)){
            rescan_db();
        }

        milestone.init(ledgerValidator, configuration.booling(Configuration.DefaultConfSettings.REVALIDATE));
        transactionValidator.init(testnet);
        tipsManager.init();
        transactionRequester.init(configuration.doubling(Configuration.DefaultConfSettings.P_REMOVE_REQUEST.name()));
        udpReceiver.init();
        replicator.init();
        node.init();
    }

    private void rescan_db() throws Exception {
        int counter = 0;
        //delete all Address , Bundle , Approvee & Tag
        AddressViewModel add = AddressViewModel.first(tangle);
        while (add != null) {
            if (++counter % 10000 == 0) {
                log.info("Clearing cache: {} Addresses", counter);
            }
            AddressViewModel NextAdd = add.next(tangle);
            add.delete(tangle);
            add = NextAdd;
        }
        counter = 0;
        BundleViewModel bn = BundleViewModel.first(tangle);
        while (bn != null) {
            if (++counter % 10000 == 0) {
                log.info("Clearing cache: {} Bundles", counter);
            }
            BundleViewModel NextBn = bn.next(tangle);
            bn.delete(tangle);
            bn = NextBn;
        }
        counter = 0;
        ApproveeViewModel app = ApproveeViewModel.first(tangle);
        while (app != null) {
            if (++counter % 10000 == 0) {
                log.info("Clearing cache: {} Approvees", counter);
            }
            ApproveeViewModel NextApp = app.next(tangle);
            app.delete(tangle);
            app = NextApp;
        }
        counter = 0;
        TagViewModel tag = TagViewModel.first(tangle);
        while (tag != null) {
            if (++counter % 10000 == 0) {
                log.info("Clearing cache: {} Tags", counter);
            }
            TagViewModel NextTag = tag.next(tangle);
            tag.delete(tangle);
            tag = NextTag;
        }

        //rescan all tx & refill the columns
        TransactionViewModel tx = TransactionViewModel.first(tangle);
        counter = 0;
        while (tx != null) {
            if (++counter % 10000 == 0) {
                log.info("Rescanned {} Transactions", counter);
            }
            tangle.saveBatch(tx.getSaveBatch());
            tx = tx.next(tangle);
        }
    }

    public void shutdown() throws Exception {
        milestone.shutDown();
        tipsManager.shutdown();
        node.shutdown();
        udpReceiver.shutdown();
        replicator.shutdown();
        transactionValidator.shutdown();
        tangle.shutdown();
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
                        configuration.string(Configuration.DefaultConfSettings.DB_LOG_PATH) ));
                break;
            }
            default: {
                throw new NotImplementedException("No such database type.");
            }
        }
        if (configuration.booling(Configuration.DefaultConfSettings.EXPORT)) {
            tangle.addPersistenceProvider(new FileExportProvider());
        }
    }
}
