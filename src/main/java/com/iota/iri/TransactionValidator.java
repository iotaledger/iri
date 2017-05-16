package com.iota.iri;

import com.iota.iri.controllers.TransactionRequester;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;

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
    private static final Logger log = LoggerFactory.getLogger(TransactionValidator.class);
    private static final int TESTNET_MIN_WEIGHT_MAGNITUDE = 13;
    private static final int MAINNET_MIN_WEIGHT_MAGNITUDE = 18;
    private static int MIN_WEIGHT_MAGNITUDE = MAINNET_MIN_WEIGHT_MAGNITUDE;

    private static Thread newSolidThread;

    private static final AtomicBoolean useFirst = new AtomicBoolean(true);
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private static final Object cascadeSync = new Object();
    private static final Set<Hash> newSolidTransactionsOne = new LinkedHashSet<>();
    private static final Set<Hash> newSolidTransactionsTwo = new LinkedHashSet<>();

    public static void init(boolean testnet) {
        if(testnet) {
            MIN_WEIGHT_MAGNITUDE = TESTNET_MIN_WEIGHT_MAGNITUDE;
        } else {
            MIN_WEIGHT_MAGNITUDE = MAINNET_MIN_WEIGHT_MAGNITUDE;
        }
        newSolidThread = new Thread(spawnSolidTransactionsCascader(), "Solid TX cascader");
        newSolidThread.start();
    }

    public static void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        newSolidThread.join();
    }

    private static void runValidation(TransactionViewModel transactionViewModel) {
        for (int i = VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE; i < VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE; i++) {
            if (transactionViewModel.trits()[i] != 0) {
                log.error("Transaction trytes: "+Converter.trytes(transactionViewModel.trits()));
                throw new RuntimeException("Invalid transaction value");
            }
        }

        int weightMagnitude = transactionViewModel.getHash().trailingZeros();
        if(weightMagnitude < MIN_WEIGHT_MAGNITUDE) {
            log.error("Hash found: {}", transactionViewModel.getHash());
            log.error("Transaction trytes: "+Converter.trytes(transactionViewModel.trits()));
            throw new RuntimeException("Invalid transaction hash");
        }
    }

    public static TransactionViewModel validate(final int[] trits) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, Hash.calculate(trits, 0, trits.length, new Curl()));
        runValidation(transactionViewModel);
        return transactionViewModel;
    }
    public static TransactionViewModel validate(final byte[] bytes) {
        return validate(bytes, new Curl());

    }

    public static TransactionViewModel validate(final byte[] bytes, Curl curl) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, Hash.calculate(bytes, TransactionViewModel.TRINARY_SIZE, curl));
        runValidation(transactionViewModel);
        return transactionViewModel;
    }

    private static final AtomicInteger nextSubSolidGroup = new AtomicInteger(1);

    public static boolean checkSolidity(Hash hash, boolean milestone) throws Exception {
        if(TransactionViewModel.fromHash(hash).isSolid()) {
            return true;
        }
        Set<Hash> analyzedHashes = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                final TransactionViewModel transaction = TransactionViewModel.fromHash(hashPointer);
                if(!transaction.isSolid()) {
                    if (transaction.getType() == TransactionViewModel.PREFILLED_SLOT && !hashPointer.equals(Hash.NULL_HASH)) {
                        TransactionRequester.instance().requestTransaction(hashPointer, milestone);
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
            TransactionViewModel.updateSolidTransactions(analyzedHashes);
        }
        analyzedHashes.clear();
        return solid;
    }

    public static void addSolidTransaction(Hash hash) {
        synchronized (cascadeSync) {
            if (useFirst.get()) {
                newSolidTransactionsOne.add(hash);
            } else {
                newSolidTransactionsTwo.add(hash);
            }
        }
    }

    private static Runnable spawnSolidTransactionsCascader() {
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
                        TransactionViewModel transaction = TransactionViewModel.fromHash(hash);
                        transaction.getApprovers().getHashes().stream()
                                .map(TransactionViewModel::quietFromHash)
                                .forEach(tx -> {
                                    if(!tx.quietQuickSetSolid() && transaction.isSolid()) {
                                        addSolidTransaction(hash);
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
}
