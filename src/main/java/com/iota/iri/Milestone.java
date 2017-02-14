package com.iota.iri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.storage.StorageAddresses;
import com.iota.iri.service.storage.StorageScratchpad;
import com.iota.iri.service.storage.StorageTransactions;
import com.iota.iri.utils.Converter;

public class Milestone {

    public static final Hash COORDINATOR = new Hash("KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU");

    public static Hash latestMilestone = Hash.NULL_HASH;
    public static Hash latestSolidSubtangleMilestone = Hash.NULL_HASH;
    
    public static final int MILESTONE_START_INDEX = 10005;

    public static int latestMilestoneIndex = MILESTONE_START_INDEX;
    public static int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private static final Set<Long> analyzedMilestoneCandidates = new HashSet<>();
    private static final Set<Long> analyzedMilestoneRetryCandidates = new HashSet<>();
    private static final Map<Integer, Hash> milestones = new ConcurrentHashMap<>();

    public static Hash getMilestone(int milestoneIndex) {
        return milestones.get(milestoneIndex);
    }
    
    public static void updateLatestMilestone() { // refactor

        final long now = System.currentTimeMillis() / 1000L;
        
        for (final Long pointer : StorageAddresses.instance().addressesOf(COORDINATOR)) {

            if (analyzedMilestoneCandidates.add(pointer) || analyzedMilestoneRetryCandidates.remove(pointer)) {

                final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                if (transaction.currentIndex == 0) {

                    final int index = (int) Converter.longValue(transaction.trits(), Transaction.TAG_TRINARY_OFFSET, 15);
                    final long timestamp = (int) Converter.longValue(transaction.trits(), Transaction.TIMESTAMP_TRINARY_OFFSET, 27);
                   
                    if ((now - timestamp) < 7200L && index > latestMilestoneIndex) {

                        final Bundle bundle = new Bundle(transaction.bundle);
                        if (bundle.getTransactions().size() == 0) {
							// Bundle not available, try again later.
                            analyzedMilestoneRetryCandidates.add(pointer);
                        }
                        else {
                            for (final List<Transaction> bundleTransactions : bundle.getTransactions()) {

                                if (bundleTransactions.get(0).pointer == transaction.pointer) {

                                    final Transaction transaction2 = StorageTransactions.instance().loadTransaction(transaction.trunkTransactionPointer);
                                    if (transaction2.type == AbstractStorage.FILLED_SLOT
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
    }

    public static void updateLatestSolidSubtangleMilestone() {

        for (int milestoneIndex = latestMilestoneIndex; milestoneIndex > latestSolidSubtangleMilestoneIndex; milestoneIndex--) {

            final Hash milestone = milestones.get(milestoneIndex);
            if (milestone != null) {

                boolean solid = true;

                synchronized (StorageScratchpad.instance().getAnalyzedTransactionsFlags()) {

                	StorageScratchpad.instance().clearAnalyzedTransactionsFlags();

                    final Queue<Long> nonAnalyzedTransactions = new LinkedList<>();
                    nonAnalyzedTransactions.offer(StorageTransactions.instance().transactionPointer(milestone.bytes()));
                    Long pointer;
                    while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                        if (StorageScratchpad.instance().setAnalyzedTransactionFlag(pointer)) {

                            final Transaction transaction2 = StorageTransactions.instance().loadTransaction(pointer);
                            if (transaction2.type == AbstractStorage.PREFILLED_SLOT) {
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
