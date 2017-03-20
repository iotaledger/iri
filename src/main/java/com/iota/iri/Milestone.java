package com.iota.iri;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.service.viewModels.AddressViewModel;
import com.iota.iri.service.ScratchpadViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.utils.Converter;

public class Milestone {

    public static final Hash COORDINATOR = new Hash("KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU");

    public static Hash latestMilestone = Hash.NULL_HASH;
    public static Hash latestSolidSubtangleMilestone = Hash.NULL_HASH;
    
    public static final int MILESTONE_START_INDEX = 10005;

    public static int latestMilestoneIndex = MILESTONE_START_INDEX;
    public static int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private static final Set<byte[]> analyzedMilestoneCandidates = new HashSet<>();
    private static final Set<byte[]> analyzedMilestoneRetryCandidates = new HashSet<>();
    private static final Map<Integer, Hash> milestones = new ConcurrentHashMap<>();

    public static Hash getMilestone(int milestoneIndex) {
        return milestones.get(milestoneIndex);
    }
    
    public static void updateLatestMilestone() throws Exception { // refactor

        final long now = System.currentTimeMillis() / 1000L;

        AddressViewModel coordinator = new AddressViewModel(COORDINATOR.bytes());
        for (final Hash hash : coordinator.getTransactionHashes()) {

            if (analyzedMilestoneCandidates.add(hash.bytes()) || analyzedMilestoneRetryCandidates.remove(hash)) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
                if (transactionViewModel.getCurrentIndex() == 0) {

                    final int index = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET, 15);
                    final long timestamp = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TIMESTAMP_TRINARY_OFFSET, 27);

                    //if ((now - timestamp) < 7200L && index > latestMilestoneIndex) {
                    if (index > latestMilestoneIndex) {

                        final Bundle bundle = new Bundle(transactionViewModel.getBundleHash());
                        if (bundle.getTransactions().size() == 0) {
							// Bundle not available, try again later.
                            analyzedMilestoneRetryCandidates.add(hash.bytes());
                        }
                        else {
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundle.getTransactions()) {

                                if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(),transactionViewModel.getHash())) {

                                    //final TransactionViewModel transactionViewModel2 = StorageTransactions.instance().loadTransaction(transactionViewModel.trunkTransactionPointer);
                                    final TransactionViewModel transactionViewModel2 = transactionViewModel.getTrunkTransaction();
                                    if (transactionViewModel2.getType() == AbstractStorage.FILLED_SLOT
                                            && Arrays.equals(transactionViewModel.getBranchTransactionHash(), transactionViewModel2.getTrunkTransactionHash())) {

                                        final int[] trunkTransactionTrits = new int[TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE];
                                        Converter.getTrits(transactionViewModel.getTrunkTransactionHash(), trunkTransactionTrits);
                                        final int[] signatureFragmentTrits = Arrays.copyOfRange(transactionViewModel.trits(), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);

                                        final int[] hashTrits = ISS.address(ISS.digest(Arrays.copyOf(ISS.normalizedBundle(trunkTransactionTrits), ISS.NUMBER_OF_FRAGMENT_CHUNKS), signatureFragmentTrits));

                                        int indexCopy = index;
                                        for (int i = 0; i < 20; i++) {

                                            final Curl curl = new Curl();
                                            if ((indexCopy & 1) == 0) {
                                                curl.absorb(hashTrits, 0, hashTrits.length);
                                                curl.absorb(transactionViewModel2.trits(), i * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                                            } else {
                                                curl.absorb(transactionViewModel2.trits(), i * Curl.HASH_LENGTH, Curl.HASH_LENGTH);
                                                curl.absorb(hashTrits, 0, hashTrits.length);
                                            }
                                            curl.squeeze(hashTrits, 0, hashTrits.length);

                                            indexCopy >>= 1;
                                        }

                                        if ((new Hash(hashTrits)).equals(COORDINATOR)) {

                                            latestMilestone = new Hash(transactionViewModel.getHash());
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

    public static void updateLatestSolidSubtangleMilestone() throws Exception {

        for (int milestoneIndex = latestMilestoneIndex; milestoneIndex > latestSolidSubtangleMilestoneIndex; milestoneIndex--) {

            final Hash milestone = milestones.get(milestoneIndex);
            if (milestone != null) {

                boolean solid = true;


                	ScratchpadViewModel.instance().clearAnalyzedTransactionsFlags();

                	ScratchpadViewModel.instance().setAnalyzedTransactionFlag(TransactionViewModel.NULL_TRANSACTION_HASH_BYTES);
                    final Queue<byte[]> nonAnalyzedTransactions = new LinkedList<>();
                    nonAnalyzedTransactions.offer(milestone.bytes());
                    byte[] hashBytes;
                    while ((hashBytes = nonAnalyzedTransactions.poll()) != null) {

                        if (ScratchpadViewModel.instance().setAnalyzedTransactionFlag(hashBytes)) {

                            final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashBytes);
                            if (transactionViewModel2.getType() == AbstractStorage.PREFILLED_SLOT) {
                                solid = false;
                                break;

                            } else {
                                nonAnalyzedTransactions.offer(transactionViewModel2.getTrunkTransactionHash());
                                nonAnalyzedTransactions.offer(transactionViewModel2.getBranchTransactionHash());
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
