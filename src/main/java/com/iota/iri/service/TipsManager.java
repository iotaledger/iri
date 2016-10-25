package com.iota.iri.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.iota.iri.Bundle;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;

public class TipsManager {

    static boolean shuttingDown;

    public static void launch() {

        (new Thread(() -> {

            while (!shuttingDown) {

                try {

                    final int previousLatestMilestoneIndex = Milestone.latestMilestoneIndex;
                    final int previousSolidSubtangleLatestMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;

                    Milestone.updateLatestMilestone();
                    Milestone.updateLatestSolidSubtangleMilestone();

                    if (previousLatestMilestoneIndex != Milestone.latestMilestoneIndex) {

                        System.out.println("Latest milestone has changed from #" + previousLatestMilestoneIndex + " to #" + Milestone.latestMilestoneIndex);
                    }
                    if (previousSolidSubtangleLatestMilestoneIndex != Milestone.latestSolidSubtangleMilestoneIndex) {

                        System.out.println("Latest SOLID SUBTANGLE milestone has changed from #" + previousSolidSubtangleLatestMilestoneIndex + " to #" + Milestone.latestSolidSubtangleMilestoneIndex);
                    }

                    Thread.sleep(5000);

                } catch (final Exception e) {

                    e.printStackTrace();
                }
            }

        }, "Latest Milestone Tracker")).start();
    }

    public static void shutDown() {
        shuttingDown = true;
    }

    static synchronized Hash transactionToApprove(final Hash extraTip, int depth) {

        final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;

        synchronized (Storage.analyzedTransactionsFlags) {

            Storage.clearAnalyzedTransactionsFlags();

            Map<Hash, Long> state = new HashMap<>(Snapshot.initialState);

            {
                int numberOfAnalyzedTransactions = 0;

                final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(Storage.transactionPointer((extraTip == null ? preferableMilestone : extraTip).bytes())));
                Long pointer;
                while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                    if (Storage.setAnalyzedTransactionFlag(pointer)) {

                        numberOfAnalyzedTransactions++;

                        final Transaction transaction = Storage.loadTransaction(pointer);
                        if (transaction.type == Storage.PREFILLED_SLOT) {

                            return null;

                        } else {

                            if (transaction.currentIndex == 0) {

                                boolean validBundle = false;

                                final Bundle bundle = new Bundle(transaction.bundle);
                                for (final List<Transaction> bundleTransactions : bundle.transactions) {

                                    if (bundleTransactions.get(0).pointer == transaction.pointer) {

                                        validBundle = true;

                                        for (final Transaction bundleTransaction : bundleTransactions) {

                                            if (bundleTransaction.value != 0) {

                                                final Hash address = new Hash(bundleTransaction.address);
                                                final Long value = state.get(address);
                                                state.put(address, value == null ? bundleTransaction.value : (value + bundleTransaction.value));
                                            }
                                        }

                                        break;
                                    }
                                }

                                if (!validBundle) {
                                    return null;
                                }
                            }

                            nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                            nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                        }
                    }
                }

                System.out.println("Confirmed transactions = " + numberOfAnalyzedTransactions);
            }

            final Iterator<Map.Entry<Hash, Long>> stateIterator = state.entrySet().iterator();
            while (stateIterator.hasNext()) {

                final Map.Entry<Hash, Long> entry = stateIterator.next();
                if (entry.getValue() <= 0) {

                    if (entry.getValue() < 0) {

                        System.out.println("Ledger inconsistency detected");

                        return null;
                    }

                    stateIterator.remove();
                }
            }

            Storage.saveAnalyzedTransactionsFlags();
            Storage.clearAnalyzedTransactionsFlags();

            final Set<Hash> tailsToAnalyze = new HashSet<>();

            Hash tip = preferableMilestone;
            if (extraTip != null) {

                Transaction transaction = Storage.loadTransaction(Storage.transactionPointer(tip.bytes()));
                while (depth-- > 0 && !tip.equals(Hash.NULL_HASH)) {

                    tip = new Hash(transaction.hash, 0, Transaction.HASH_SIZE);
                    do {

                        transaction = Storage.loadTransaction(transaction.trunkTransactionPointer);

                    } while (transaction.currentIndex != 0);
                }
            }
            final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(Storage.transactionPointer(tip.bytes())));
            Long pointer;
            while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                if (Storage.setAnalyzedTransactionFlag(pointer)) {

                    final Transaction transaction = Storage.loadTransaction(pointer);

                    if (transaction.currentIndex == 0) {
                        tailsToAnalyze.add(new Hash(transaction.hash, 0, Transaction.HASH_SIZE));
                    }

                    for (final Long approverPointer : Storage.approveeTransactions(Storage.approveePointer(transaction.hash))) {
                        nonAnalyzedTransactions.offer(approverPointer);
                    }
                }
            }

            if (extraTip != null) {

                Storage.loadAnalyzedTransactionsFlags();

                final Iterator<Hash> tailsToAnalyzeIterator = tailsToAnalyze.iterator();
                while (tailsToAnalyzeIterator.hasNext()) {

                    final Transaction tail = Storage.loadTransaction(tailsToAnalyzeIterator.next().bytes());
                    if (Storage.analyzedTransactionFlag(tail.pointer)) {

                        tailsToAnalyzeIterator.remove();
                    }
                }
            }

            System.out.println(tailsToAnalyze.size() + " tails need to be analyzed");
            Hash bestTip = preferableMilestone;
            int bestRating = 0;
            for (final Hash tail : tailsToAnalyze) {

                Storage.loadAnalyzedTransactionsFlags();

                Set<Hash> extraTransactions = new HashSet<>();

                nonAnalyzedTransactions.clear();
                nonAnalyzedTransactions.offer(Storage.transactionPointer(tail.bytes()));
                while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                    if (Storage.setAnalyzedTransactionFlag(pointer)) {

                        final Transaction transaction = Storage.loadTransaction(pointer);
                        if (transaction.type == Storage.PREFILLED_SLOT) {

                            extraTransactions = null;

                            break;

                        } else {

                            extraTransactions.add(new Hash(transaction.hash, 0, Transaction.HASH_SIZE));

                            nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                            nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                        }
                    }
                }

                if (extraTransactions != null) {

                    Set<Hash> extraTransactionsCopy = new HashSet<>(extraTransactions);

                    for (final Hash extraTransaction : extraTransactions) {

                        final Transaction transaction = Storage.loadTransaction(extraTransaction.bytes());
                        if (transaction.currentIndex == 0) {

                            final Bundle bundle = new Bundle(transaction.bundle);
                            for (final List<Transaction> bundleTransactions : bundle.transactions) {

                                if (Arrays.equals(bundleTransactions.get(0).hash, transaction.hash)) {

                                    for (final Transaction bundleTransaction : bundleTransactions) {

                                        if (!extraTransactionsCopy.remove(new Hash(bundleTransaction.hash, 0, Transaction.HASH_SIZE))) {

                                            extraTransactionsCopy = null;

                                            break;
                                        }
                                    }

                                    break;
                                }
                            }
                        }

                        if (extraTransactionsCopy == null) {

                            break;
                        }
                    }

                    if (extraTransactionsCopy != null && extraTransactionsCopy.isEmpty()) {

                        final Map<Hash, Long> stateCopy = new HashMap<>(state);

                        for (final Hash extraTransaction : extraTransactions) {

                            final Transaction transaction = Storage.loadTransaction(extraTransaction.bytes());
                            if (transaction.value != 0) {
                                final Hash address = new Hash(transaction.address);
                                final Long value = stateCopy.get(address);
                                stateCopy.put(address, value == null ? transaction.value : (value + transaction.value));
                            }
                        }

                        for (final long value : stateCopy.values()) {

                            if (value < 0) {
                                extraTransactions = null;
                                break;
                            }
                        }

                        if (extraTransactions != null) {

                            if (extraTransactions.size() > bestRating) {
                                bestTip = tail;
                                bestRating = extraTransactions.size();
                            }
                        }
                    }
                }
            }
            System.out.println(bestRating + " extra transactions approved");
            return bestTip;
        }
    }
}
