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
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
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
 * Created by paul on 5/19/17.
 */
public class Iota {
    private static final Logger log = LoggerFactory.getLogger(Iota.class);

    public final LedgerValidator ledgerValidator;
    public final MilestoneTracker milestoneTracker;
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

    public Iota(IotaConfig configuration) throws IOException {
        this.configuration = configuration;
        Snapshot initialSnapshot = Snapshot.init(configuration).clone();
        tangle = new Tangle();
        messageQ = MessageQ.createWith(configuration);
        tipsViewModel = new TipsViewModel();
        transactionRequester = new TransactionRequester(tangle, messageQ);
        transactionValidator = new TransactionValidator(tangle, tipsViewModel, transactionRequester,
                configuration);
        milestoneTracker = new MilestoneTracker(tangle, transactionValidator, messageQ, initialSnapshot, configuration);
        node = new Node(tangle, transactionValidator, transactionRequester, tipsViewModel, milestoneTracker, messageQ,
                configuration);
        replicator = new Replicator(node, configuration);
        udpReceiver = new UDPReceiver(node, configuration);
        ledgerValidator = new LedgerValidator(tangle, milestoneTracker, transactionRequester, messageQ);
        tipsSolidifier = new TipsSolidifier(tangle, transactionValidator, tipsViewModel);
        tipsSelector = createTipSelector(configuration);
    }

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
        milestoneTracker.init(SpongeFactory.Mode.CURLP27, 1, ledgerValidator);
        transactionValidator.init(configuration.isTestnet(), configuration.getMwm());
        tipsSolidifier.init();
        transactionRequester.init(configuration.getpRemoveRequest());
        udpReceiver.init();
        replicator.init();
        node.init();
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

    public void shutdown() throws Exception {
        milestoneTracker.shutDown();
        tipsSolidifier.shutdown();
        node.shutdown();
        udpReceiver.shutdown();
        replicator.shutdown();
        transactionValidator.shutdown();
        tangle.shutdown();
        messageQ.shutdown();
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
        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, milestoneTracker);
        RatingCalculator ratingCalculator = new CumulativeWeightCalculator(tangle);
        TailFinder tailFinder = new TailFinderImpl(tangle);
        Walker walker = new WalkerAlpha(tailFinder, tangle, messageQ, new SecureRandom(), config);
        return new TipSelectorImpl(tangle, ledgerValidator, entryPointSelector, ratingCalculator,
                walker, milestoneTracker, config);
    }
}
