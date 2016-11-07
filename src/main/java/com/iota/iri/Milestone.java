package com.iota.iri;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.Storage;
import com.iota.iri.utils.Converter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Milestone {

    private static Hash COORDINATOR = new Hash("KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU");

    public static Hash latestMilestone = Hash.NULL_HASH, latestSolidSubtangleMilestone = Hash.NULL_HASH;
    public static int latestMilestoneIndex, latestSolidSubtangleMilestoneIndex;

    private static final Set<Long> analyzedMilestoneCandidates = new HashSet<>();
    private static final Map<Integer, Hash> milestones = new ConcurrentHashMap<>();

    public static void updateLatestMilestone() {

        for (final Long pointer : Storage.addressTransactions(Storage.addressPointer(COORDINATOR.bytes()))) {

            if (analyzedMilestoneCandidates.add(pointer)) {

                final Transaction transaction = Storage.loadTransaction(pointer);
                if (transaction.currentIndex == 0) {

                    final int index = (int) Converter.longValue(transaction.trits(), Transaction.TAG_TRINARY_OFFSET, 15);
                    if (index > latestMilestoneIndex) {

                        final Bundle bundle = new Bundle(transaction.bundle);
                        for (final List<Transaction> bundleTransactions : bundle.transactions) {

                            if (bundleTransactions.get(0).pointer == transaction.pointer) {

                                final Transaction transaction2 = Storage.loadTransaction(transaction.trunkTransactionPointer);
                                if (transaction2.type == Storage.FILLED_SLOT
                                        && transaction.branchTransactionPointer == transaction2.trunkTransactionPointer) {

                                    final int[] trunkTransactionTrits = new int[Transaction.TRUNK_TRANSACTION_TRINARY_SIZE];
                                    Converter.getTrits(transaction.trunkTransaction, trunkTransactionTrits);
                                    final int[] signatureFragmentTrits = Arrays.copyOfRange(transaction.trits(), Transaction.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, Transaction.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + Transaction.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);

                                    final int[] hash = ISS.address(ISS.digest(Arrays.copyOf(ISS.normalizedBundle(trunkTransactionTrits), ISS.NUMBER_OF_FRAGMENT_CHUNKS), signatureFragmentTrits));

                                    int indexCopy = index;
                                    for (int i = 0; i < 20; i++) {

                                        final Curl curl = new Curl();
                                        if ((indexCopy & 1) == 0) {

                                            curl.absorb(hash, 0, hash.length);
                                            curl.absorb(transaction2.trits(), i * Curl.HASH_LENGTH, Curl.HASH_LENGTH);

                                        } else {

                                            curl.absorb(transaction2.trits(), i * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                                            curl.absorb(hash, 0, hash.length);
                                        }
                                        curl.squeeze(hash, 0, hash.length);

                                        indexCopy >>= 1;
                                    }

                                    if ((new Hash(hash)).equals(COORDINATOR)) {

                                        latestMilestone = new Hash(transaction.hash, 0, Transaction.HASH_SIZE);
                                        latestMilestoneIndex = index;

                                        milestones.put(latestMilestoneIndex, latestMilestone);
                                    }
                                }

                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public static void updateLatestSolidSubtangleMilestone() {

        for (int milestoneIndex = latestMilestoneIndex; milestoneIndex > latestSolidSubtangleMilestoneIndex; milestoneIndex--) {

            final Hash milestone = milestones.get(milestoneIndex);
            if (milestone != null) {

                boolean solid = true;

                synchronized (Storage.analyzedTransactionsFlags) {

                    Storage.clearAnalyzedTransactionsFlags();

                    final Queue<Long> nonAnalyzedTransactions = new LinkedList<>();
                    nonAnalyzedTransactions.offer(Storage.transactionPointer(milestone.bytes()));
                    Long pointer;
                    while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                        if (Storage.setAnalyzedTransactionFlag(pointer)) {

                            final Transaction transaction2 = Storage.loadTransaction(pointer);
                            if (transaction2.type == Storage.PREFILLED_SLOT) {

                                solid = false;

                                break;

                            } else {
                                nonAnalyzedTransactions.offer(transaction2.trunkTransactionPointer);
                                nonAnalyzedTransactions.offer(transaction2.branchTransactionPointer);
                            }
                        }
                    }
                }

                if (solid) {

                    latestSolidSubtangleMilestone = milestone;
                    latestSolidSubtangleMilestoneIndex = milestoneIndex;

                    return;
                }
            }
        }
    }
}
