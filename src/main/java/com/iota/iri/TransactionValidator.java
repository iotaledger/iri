package com.iota.iri;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.storage.Tangle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.iota.iri.controllers.TransactionViewModel.*;

/**
 * Created by paul on 4/17/17.
 */
public class TransactionValidator {
    private final Logger log = LoggerFactory.getLogger(TransactionValidator.class);
    private final Tangle tangle;
    private final TipsViewModel tipsViewModel;
    private final TransactionRequester transactionRequester;
    private final MessageQ messageQ;
    private int MIN_WEIGHT_MAGNITUDE = 81;

    private Thread newSolidThread;

    private final AtomicBoolean useFirst = new AtomicBoolean(true);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final Object cascadeSync = new Object();
    private final Set<Hash> newSolidTransactionsOne = new LinkedHashSet<>();
    private final Set<Hash> newSolidTransactionsTwo = new LinkedHashSet<>();

    public TransactionValidator(Tangle tangle, TipsViewModel tipsViewModel, TransactionRequester transactionRequester, MessageQ messageQ) {
        this.tangle = tangle;
        this.tipsViewModel = tipsViewModel;
        this.transactionRequester = transactionRequester;
        this.messageQ = messageQ;
    }

    public void init(boolean testnet,int MAINNET_MWM, int TESTNET_MWM) {
        if(testnet) {
            MIN_WEIGHT_MAGNITUDE = TESTNET_MWM;
        } else {
            MIN_WEIGHT_MAGNITUDE = MAINNET_MWM;
        }
        //lowest allowed MWM encoded in 46 bytes.
        if (MIN_WEIGHT_MAGNITUDE<13){
            MIN_WEIGHT_MAGNITUDE = 13;
        }

        newSolidThread = new Thread(spawnSolidTransactionsPropagation(), "Solid TX cascader");
        newSolidThread.start();
    }

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        newSolidThread.join();
    }

    public int getMinWeightMagnitude() {
        return MIN_WEIGHT_MAGNITUDE;
    }

    private static void runValidation(TransactionViewModel transactionViewModel, final int minWeightMagnitude) {
        transactionViewModel.setMetadata();
        if(transactionViewModel.getTimestamp() < 1497031200 && !transactionViewModel.getHash().equals(Hash.NULL_HASH)) {
            throw new RuntimeException("Invalid transaction timestamp.");
        }

        for (int i = VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE; i < VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE; i++) {
            if (transactionViewModel.trits()[i] != 0) {
                //log.error("Transaction trytes: "+Converter.trytes(transactionViewModel.trits()));
                throw new RuntimeException("Invalid transaction value");
            }
        }

        int weightMagnitude = transactionViewModel.getHash().trailingZeros();
        if(weightMagnitude < minWeightMagnitude) {
            /*
            log.error("Hash found: {}", transactionViewModel.getHash());
            log.error("Transaction trytes: "+Converter.trytes(transactionViewModel.trits()));
            */
            throw new RuntimeException("Invalid transaction hash");
        }
    }

    public static TransactionViewModel validate(final int[] trits, int minWeightMagnitude) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, Hash.calculate(trits, 0, trits.length, new Curl()));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
    }
    public static TransactionViewModel validate(final byte[] bytes, int minWeightMagnitude) {
        return validate(bytes, minWeightMagnitude, new Curl());

    }

    public static TransactionViewModel validate(final byte[] bytes, int minWeightMagnitude, Curl curl) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, Hash.calculate(bytes, TransactionViewModel.TRINARY_SIZE, curl));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
    }

    private final AtomicInteger nextSubSolidGroup = new AtomicInteger(1);

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
                        if (solid) {
                            nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                            nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                        }
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

    public void addSolidTransaction(Hash hash) {
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
                Set<Hash> newSolidHashes = new HashSet<>();
                useFirst.set(!useFirst.get());
                synchronized (cascadeSync) {
                    if (useFirst.get()) {
                        newSolidHashes.addAll(newSolidTransactionsTwo);
                    } else {
                        newSolidHashes.addAll(newSolidTransactionsOne);
                    }
                }
                Iterator<Hash> cascadeIterator = newSolidHashes.iterator();
                Set<Hash> hashesToCascade = new HashSet<>();
                while(cascadeIterator.hasNext() && !shuttingDown.get()) {
                    try {
                        Hash hash = cascadeIterator.next();
                        TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                        transaction.getApprovers(tangle).getHashes().stream()
                                .map(h -> TransactionViewModel.quietFromHash(tangle, h))
                                .forEach(tx -> {
                                    if(quietQuickSetSolid(tx)) {
                                        try {
                                            tx.update(tangle, "solid");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        if (transaction.isSolid()) {
                                            addSolidTransaction(hash);
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        log.error("Some error", e);
                        // TODO: Do something, maybe, or do nothing.
                    }
                }
                synchronized (cascadeSync) {
                    if (useFirst.get()) {
                        newSolidTransactionsTwo.clear();
                    } else {
                        newSolidTransactionsOne.clear();
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    public void updateStatus(TransactionViewModel transactionViewModel) throws Exception {
        transactionRequester.clearTransactionRequest(transactionViewModel.getHash());
        if(transactionViewModel.getApprovers(tangle).size() == 0) {
            tipsViewModel.addTipHash(transactionViewModel.getHash());
        }
        tipsViewModel.removeTipHash(transactionViewModel.getTrunkTransactionHash());
        tipsViewModel.removeTipHash(transactionViewModel.getBranchTransactionHash());

        if(quickSetSolid(transactionViewModel)) {
            addSolidTransaction(transactionViewModel.getHash());
        }
    }

    public boolean quietQuickSetSolid(TransactionViewModel transactionViewModel) {
        try {
            return quickSetSolid(transactionViewModel);
        } catch (Exception e) {
            e.printStackTrace();
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
        //return isSolid();
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

}
