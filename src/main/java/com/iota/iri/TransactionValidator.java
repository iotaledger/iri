package com.iota.iri;

import com.iota.iri.controllers.HashesViewModel;
import com.iota.iri.controllers.LocalSolidityViewModel;
import com.iota.iri.controllers.TransactionRequester;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.Transaction;
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

    public boolean checkSolidity(Hash hash, boolean milestone) throws Exception {
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
        return solid;
    }


    /**
     * Traverses the tangle through "solidity groups", adding non-classified transactions to groups, seeking to the
     * ends of groups, and finally merging groups with their parent groups as they become locally solid.
     * @param hash hash of the transaction from where the search begins
     * @param milestone whether the hash is knowingly referenced by a milestone
     * @return whether the transaction was solid
     * @throws Exception
     */
    public synchronized static boolean checkGroupSolidity(final Hash hash, final boolean milestone) throws Exception {
        if(TransactionViewModel.fromHash(hash).isSolid()) {
            return true;
        }
        TransactionViewModel transaction, trunk, branch;
        final Set<Hash> analyzedHashes = new HashSet<>();
        final Set<Hash> missingHashes = new HashSet<>();
        final Stack<Integer> groupStack;
        groupStack = new Stack<>();
        groupStack.push(Math.abs(getTransactionGroup(hash)));
        final Map<Integer, Queue<Hash>> nonAnalyzedGroups = new HashMap<>();
        nonAnalyzedGroups.put(groupStack.peek(), new LinkedList<>(Collections.singleton(hash)));

        boolean solid = true;
        while(!groupStack.isEmpty()) {
            int group = groupStack.peek();
            final Queue<Hash> nonAnalyzedTransactions = nonAnalyzedGroups.get(group); //new LinkedList<>(Collections.singleton(hash));
            if(nonAnalyzedTransactions != null) {
                LocalSolidityViewModel midNodes = LocalSolidityViewModel.load(new IntegerIndex(group));
                LocalSolidityViewModel  endNodes = LocalSolidityViewModel.load(new IntegerIndex(-group));
                nonAnalyzedTransactions.addAll(endNodes.getHashes());
                Hash pointer;
                while ((pointer = nonAnalyzedTransactions.poll()) != null) {
                    int currentGroup = group;
                    if (analyzedHashes.add(pointer)) {
                        boolean subsolid = true;
                        transaction = TransactionViewModel.fromHash(pointer);
                        trunk = transaction.getTrunkTransaction();
                        branch = transaction.getBranchTransaction();
                        if (!checkApproovee(trunk, group, nonAnalyzedTransactions, nonAnalyzedGroups, groupStack, missingHashes)) {
                            transaction.updateSubSolidGroup(-group);
                            endNodes.addHash(transaction.getHash());
                            endNodes.store();
                            midNodes.store();
                            group = nextSubSolidGroup.incrementAndGet();
                            midNodes = new LocalSolidityViewModel(new IntegerIndex(group));
                            endNodes = new LocalSolidityViewModel(new IntegerIndex(-group));
                            nonAnalyzedGroups.put(group, new LinkedList<>());
                            solid = false;
                            subsolid = false;
                        }
                        if (!checkApproovee(branch, group, nonAnalyzedTransactions, nonAnalyzedGroups, groupStack, missingHashes)) {
                            if (subsolid) {
                                transaction.updateSubSolidGroup(-group);
                                subsolid = false;
                            }
                            endNodes.addHash(transaction.getHash());
                            endNodes.store();
                            midNodes.store();
                            group = nextSubSolidGroup.incrementAndGet();
                            midNodes = new LocalSolidityViewModel(new IntegerIndex(group));
                            endNodes = new LocalSolidityViewModel(new IntegerIndex(-group));
                            nonAnalyzedGroups.put(group, new LinkedList<>());
                            solid = false;
                        }
                        if (subsolid) {
                            transaction.updateSubSolidGroup(trunk.getSubSolidGroup() == currentGroup && branch.getSubSolidGroup() == currentGroup ? currentGroup : -currentGroup);
                            midNodes.addHash(pointer);
                            //if(transaction.getSubSolidGroup() )
                            endNodes.getHashes().remove(pointer);
                        }
                    }
                }
                if (groupStack.peek() == group) {
                    TransactionViewModel.updateSolidTransactions(midNodes.getHashes());
                    midNodes.getHashes().clear();
                    nonAnalyzedGroups.remove(group);
                    groupStack.pop();
                }
                midNodes.store();
                endNodes.store();
            } else {
                groupStack.pop();
            }
        }
        if(!solid) {
            TransactionRequester.instance().requestTransactions(missingHashes, milestone);
        }
        return solid;
    }

    private static int getTransactionGroup(Hash hash) throws Exception {
        TransactionViewModel transaction;
        transaction = TransactionViewModel.fromHash(hash);
        int group = transaction.getSubSolidGroup();
        if(group == 0) {
            group = Math.abs(transaction.getTrunkTransaction().getSubSolidGroup());
            if(group == 0) {
                group = Math.abs(transaction.getBranchTransaction().getSubSolidGroup());
            }
            if(group == 0) {
                group = nextSubSolidGroup.incrementAndGet();
            }
            //transaction.updateSubSolidGroup(-group);
        }
        return group;
    }

    private static boolean checkApproovee(TransactionViewModel approovee,
                                          int group, Queue<Hash> nonAnalyzedTransactions,
                                          Map<Integer, Queue<Hash>> nonAnalyzedGroups, Stack<Integer> groupStack, Set<Hash> missingHashes) throws Exception {
        if(!approovee.getHash().equals(Hash.NULL_HASH)) {
            if (approovee.getType() == PREFILLED_SLOT) {
                missingHashes.add(approovee.getHash());
                return false;
            } else {
                if (approovee.getSubSolidGroup() == 0) {
                    nonAnalyzedTransactions.add(approovee.getHash());
                } else if ( !approovee.isSolid() ) {
                    int subGroup = Math.abs(approovee.getSubSolidGroup());
                    if (subGroup != group) {
                        Queue<Hash> differentGroupQueue = nonAnalyzedGroups.get(subGroup);
                        if (differentGroupQueue == null) {
                            differentGroupQueue = new LinkedList<>();
                            nonAnalyzedGroups.put(group, differentGroupQueue);
                        }
                        differentGroupQueue.add(approovee.getHash());
                        groupStack.push(subGroup);
                    }
                }
            }
        }
        return true;
    }

    /**
     * Given a set of referencing hashes in a group, this method returns all hashes at the end of the group, whether
     * they are negative group (which means they were previously missing transactions), or whether .
     * @param referencingHashes
     * @param group
     * @return
     * @throws Exception
     */
    private static Set<Hash> referencedHashes(Set<Hash> referencingHashes, int group) throws Exception {
        Set<Hash> referencedHashes = new HashSet<>();
        Set<Hash> visitedHashes = new HashSet<>();
        Queue<Hash> nonVisitedHashes = new LinkedList<>(referencingHashes);
        Hash hash;
        TransactionViewModel transaction;
        while ((hash = nonVisitedHashes.poll()) != null) {
            if(visitedHashes.add(hash)) {
                transaction = TransactionViewModel.fromHash(hash);
                if(transaction.getSubSolidGroup() == -group || transaction.getSubSolidGroup() != group) {
                    referencedHashes.add(hash);
                } else {
                    nonVisitedHashes.offer(transaction.getTrunkTransactionHash());
                    nonVisitedHashes.offer(transaction.getBranchTransactionHash());
                }
            }
        }
        visitedHashes.clear();
        return referencedHashes;
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

    public static Runnable spawnSolidTransactionsCascader() {
        return () -> {
            while(!shuttingDown.get()) {
                Set<Hash> newSolidHashes;
                useFirst.set(!useFirst.get());
                synchronized (cascadeSync) {
                    if (useFirst.get()) {
                        newSolidHashes = newSolidTransactionsTwo;
                    } else {
                        newSolidHashes = newSolidTransactionsOne;
                    }
                }
                Iterator<Hash> cascadeIterator = newSolidHashes.iterator();
                Set<Hash> hashesToCascade = new HashSet<>();
                while(cascadeIterator.hasNext() && !shuttingDown.get()) {
                    try {
                        TransactionViewModel.fromHash(cascadeIterator.next()).getApprovers().getHashes().stream()
                                .map(TransactionViewModel::quietFromHash)
                                .filter(TransactionViewModel::quietQuickSetSolid)
                                .map(TransactionViewModel::getHash)
                                .forEach(hashesToCascade::add);
                    } catch (Exception e) {
                        // TODO: Do something, maybe, or do nothing.
                    }
                    if(!cascadeIterator.hasNext()) {
                        newSolidHashes.clear();
                        newSolidHashes = hashesToCascade;
                        cascadeIterator = newSolidHashes.iterator();
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
