package com.iota.iri;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.iota.iri.controllers.TransactionViewModel.*;

public class TransactionValidator {
    private final Logger log = LoggerFactory.getLogger(TransactionValidator.class);
    private final Tangle tangle;
    private final TipsViewModel tipsViewModel;
    private final TransactionRequester transactionRequester;
    private int minWeightMagnitude = 81;
    private final long snapshotTimestamp;
    private final long snapshotTimestampMs;
    private static final long MAX_TIMESTAMP_FUTURE = 2L * 60L * 60L;
    private static final long MAX_TIMESTAMP_FUTURE_MS = MAX_TIMESTAMP_FUTURE * 1_000L;

    private Thread newSolidThread;

    private final AtomicBoolean useFirst = new AtomicBoolean(true);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final Object cascadeSync = new Object();
    private final Set<Hash> newSolidTransactionsOne = new LinkedHashSet<>();
    private final Set<Hash> newSolidTransactionsTwo = new LinkedHashSet<>();

    TransactionValidator(Tangle tangle, TipsViewModel tipsViewModel, TransactionRequester transactionRequester,
                                SnapshotConfig config) {
        this.tangle = tangle;
        this.tipsViewModel = tipsViewModel;
        this.transactionRequester = transactionRequester;
        this.snapshotTimestamp = config.getSnapshotTime();
        this.snapshotTimestampMs = snapshotTimestamp * 1000;
    }

    public void init(boolean testnet, int mwm) {
        setMwm(testnet, mwm);

        newSolidThread = new Thread(spawnSolidTransactionsPropagation(), "Solid TX cascader");
        newSolidThread.start();
    }

    void setMwm(boolean testnet, int mwm) {
        minWeightMagnitude = mwm;

        //lowest allowed MWM encoded in 46 bytes.
        if (!testnet){
            minWeightMagnitude = Math.max(minWeightMagnitude, 13);
        }
    }

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        newSolidThread.join();
    }

    public int getMinWeightMagnitude() {
        return minWeightMagnitude;
    }

    private boolean hasInvalidTimestamp(TransactionViewModel transactionViewModel) {
        if (transactionViewModel.getAttachmentTimestamp() == 0) {
            return transactionViewModel.getTimestamp() < snapshotTimestamp && !Objects.equals(transactionViewModel.getHash(), Hash.NULL_HASH)
                    || transactionViewModel.getTimestamp() > (System.currentTimeMillis() / 1000) + MAX_TIMESTAMP_FUTURE;
        }
        return transactionViewModel.getAttachmentTimestamp() < snapshotTimestampMs
                || transactionViewModel.getAttachmentTimestamp() > System.currentTimeMillis() + MAX_TIMESTAMP_FUTURE_MS;
    }

    public void runValidation(TransactionViewModel transactionViewModel, final int minWeightMagnitude) {
        transactionViewModel.setMetadata();
        transactionViewModel.setAttachmentData();
        if(hasInvalidTimestamp(transactionViewModel)) {
            throw new StaleTimestampException("Invalid transaction timestamp.");
        }
        for (int i = VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE; i < VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE; i++) {
            if (transactionViewModel.trits()[i] != 0) {
                throw new IllegalStateException("Invalid transaction value");
            }
        }

        int weightMagnitude = transactionViewModel.weightMagnitude;
        if(weightMagnitude < minWeightMagnitude) {
            throw new IllegalStateException("Invalid transaction hash");
        }

        if (transactionViewModel.value() != 0 && transactionViewModel.getAddressHash().trits()[Curl.HASH_LENGTH - 1] != 0) {
            throw new IllegalStateException("Invalid transaction address");
        }
    }

    public TransactionViewModel validateTrits(final byte[] trits, int minWeightMagnitude) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(trits, 0, trits.length, SpongeFactory.create(SpongeFactory.Mode.CURLP81)));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
    }

    TransactionViewModel validateBytes(final byte[] bytes, int minWeightMagnitude) {
        return validateBytes(bytes, minWeightMagnitude, SpongeFactory.create(SpongeFactory.Mode.CURLP81));
    }

    TransactionViewModel validateBytes(final byte[] bytes, int minWeightMagnitude, Sponge curl) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(bytes, TransactionViewModel.TRINARY_SIZE, curl));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
    }

    public boolean checkSolidity(Hash hash, boolean milestone) throws Exception {
        if(TransactionViewModel.fromHash(tangle, hash).isSolid()) {
            return true;
        }
        Set<Hash> analyzedHashes = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                final TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hashPointer);
                if(!transaction.isSolid()) {
                    if (transaction.getType() == TransactionViewModel.PREFILLED_SLOT && !hashPointer.equals(Hash.NULL_HASH)) {
                        transactionRequester.requestTransaction(hashPointer, milestone);
                        solid = false;
                        break;
                    } else {
                        nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                    }
                }
            }
        }
        if (solid) {
            TransactionViewModel.updateSolidTransactions(tangle, analyzedHashes);
        }
        analyzedHashes.clear();
        return solid;
    }

    void addSolidTransaction(Hash hash) {
        synchronized (cascadeSync) {
            if (useFirst.get()) {
                newSolidTransactionsOne.add(hash);
            } else {
                newSolidTransactionsTwo.add(hash);
            }
        }
    }

    private Runnable spawnSolidTransactionsPropagation() {
        return () -> {
            while(!shuttingDown.get()) {
                propagateSolidTransactions();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // Ignoring InterruptedException. Do not use Thread.currentThread().interrupt() here.
                    log.error("Thread was interrupted: ", e);
                }
            }
        };
    }

    void propagateSolidTransactions() {
        Set<Hash> newSolidHashes = new HashSet<>();
        useFirst.set(!useFirst.get());
        //synchronized to make sure no one is changing the newSolidTransactions collections during addAll
        synchronized (cascadeSync) {
            if (useFirst.get()) {
                newSolidHashes.addAll(newSolidTransactionsTwo);
                newSolidTransactionsTwo.clear();
            } else {
                newSolidHashes.addAll(newSolidTransactionsOne);
                newSolidTransactionsOne.clear();
            }
        }
        Iterator<Hash> cascadeIterator = newSolidHashes.iterator();
        while(cascadeIterator.hasNext() && !shuttingDown.get()) {
            try {
                Hash hash = cascadeIterator.next();
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                Set<Hash> approvers = transaction.getApprovers(tangle).getHashes();
                for(Hash h: approvers) {
                    TransactionViewModel tx = TransactionViewModel.fromHash(tangle, h);
                    if(quietQuickSetSolid(tx)) {
                        tx.update(tangle, "solid|height");
                        tipsViewModel.setSolid(h);
                        addSolidTransaction(h);
                    }
                }
            } catch (Exception e) {
                log.error("Error while propagating solidity upwards", e);
            }
        }
    }

    public void updateStatus(TransactionViewModel transactionViewModel) throws Exception {
        transactionRequester.clearTransactionRequest(transactionViewModel.getHash());
        if(transactionViewModel.getApprovers(tangle).size() == 0) {
            tipsViewModel.addTipHash(transactionViewModel.getHash());
        }
        tipsViewModel.removeTipHash(transactionViewModel.getTrunkTransactionHash());
        tipsViewModel.removeTipHash(transactionViewModel.getBranchTransactionHash());

        if(quickSetSolid(transactionViewModel)) {
            transactionViewModel.update(tangle, "solid|height");
            tipsViewModel.setSolid(transactionViewModel.getHash());
            addSolidTransaction(transactionViewModel.getHash());
        }
    }

    private boolean quietQuickSetSolid(TransactionViewModel transactionViewModel) {
        try {
            return quickSetSolid(transactionViewModel);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private boolean quickSetSolid(final TransactionViewModel transactionViewModel) throws Exception {
        if(!transactionViewModel.isSolid()) {
            boolean solid = true;
            if (!checkApproovee(transactionViewModel.getTrunkTransaction(tangle))) {
                solid = false;
            }
            if (!checkApproovee(transactionViewModel.getBranchTransaction(tangle))) {
                solid = false;
            }
            if(solid) {
                transactionViewModel.updateSolid(true);
                transactionViewModel.updateHeights(tangle);
                return true;
            }
        }
        return false;
    }

    private boolean checkApproovee(TransactionViewModel approovee) throws Exception {
        if(approovee.getType() == PREFILLED_SLOT) {
            transactionRequester.requestTransaction(approovee.getHash(), false);
            return false;
        }
        if(approovee.getHash().equals(Hash.NULL_HASH)) {
            return true;
        }
        return approovee.isSolid();
    }

    //for testing
    boolean isNewSolidTxSetsEmpty () {
        return newSolidTransactionsOne.isEmpty() && newSolidTransactionsTwo.isEmpty();
    }

    public static class StaleTimestampException extends RuntimeException {
        StaleTimestampException (String message) {
            super(message);
        }
    }
}
