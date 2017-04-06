package com.iota.iri;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.service.TipsManager;
import com.iota.iri.service.viewModels.AddressViewModel;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import com.iota.iri.utils.Converter;

public class Milestone {

    public static final Hash COORDINATOR = new Hash("XNZBYAST9BETSDNOVQKKTBECYIPMF9IPOZRWUPFQGVH9HJW9NDSQVIPVBWU9YKECRYGDSJXYMZGHZDXCA");

    public static Hash latestMilestone = Hash.NULL_HASH;
    public static Hash latestSolidSubtangleMilestone = latestMilestone;
    
    public static final int MILESTONE_START_INDEX = 0;

    public static int latestMilestoneIndex = MILESTONE_START_INDEX;
    public static int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private static final Set<Hash> analyzedMilestoneCandidates = new HashSet<>();
    private static final Set<Hash> analyzedMilestoneRetryCandidates = new HashSet<>();
    private static final Map<Integer, Hash> milestones = new ConcurrentHashMap<>();

    public static Hash getMilestone(int milestoneIndex) {
        return milestones.get(milestoneIndex);
    }
    
    public static void updateLatestMilestone() throws Exception { // refactor

        final long now = System.currentTimeMillis() / 1000L;

        AddressViewModel coordinator = new AddressViewModel(COORDINATOR);
        for (final Hash hash : coordinator.getTransactionHashes()) {
            if (analyzedMilestoneCandidates.add(hash) || analyzedMilestoneRetryCandidates.remove(hash)) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
                if (transactionViewModel.getCurrentIndex() == 0) {

                    final int index = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET, 15);
                    final long timestamp = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TIMESTAMP_TRINARY_OFFSET, 27);

                    //if ((now - timestamp) < 7200L && index > latestMilestoneIndex) {
                    if (index > latestMilestoneIndex) {

                        final BundleValidator bundleValidator = new BundleValidator(BundleViewModel.fromHash(transactionViewModel.getBundleHash()));
                        if (bundleValidator.getTransactions().size() == 0) {
							// BundleValidator not available, try again later.
                            analyzedMilestoneRetryCandidates.add(hash);
                        }
                        else {
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleValidator.getTransactions()) {

                                //if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(),transactionViewModel.getHash())) {
                                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                                    //final TransactionViewModel transactionViewModel2 = StorageTransactions.instance().loadTransaction(transactionViewModel.trunkTransactionPointer);
                                    final TransactionViewModel transactionViewModel2 = transactionViewModel.getTrunkTransaction();
                                    if (transactionViewModel2.getType() == TransactionViewModel.FILLED_SLOT
                                            && transactionViewModel.getBranchTransactionHash().equals(transactionViewModel2.getTrunkTransactionHash())) {

                                        final int[] trunkTransactionTrits = new int[TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE];
                                        Converter.getTrits(transactionViewModel.getTrunkTransactionHash().bytes(), trunkTransactionTrits);
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

                                            latestMilestone = transactionViewModel.getHash();
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
            if (milestone != null && TipsManager.checkSolidity(milestone)) {
                latestSolidSubtangleMilestone = milestone;
                latestSolidSubtangleMilestoneIndex = milestoneIndex;
                break;
            }
        }
    }

    public static Hash findMilestone(int milestoneIndexToLoad) throws Exception {
        AddressViewModel coordinatorAddress = new AddressViewModel(Milestone.COORDINATOR);
        Hash hashToLoad = getMilestone(milestoneIndexToLoad);
        if(hashToLoad == null) {
            int closestGreaterMilestone = latestMilestoneIndex;
            for (final Hash hash : coordinatorAddress.getTransactionHashes()) {
                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
                if (transactionViewModel.getCurrentIndex() == 0) {
                    int milestoneIndex = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET,
                            15);
                    milestones.put(milestoneIndex, transactionViewModel.getHash());
                    if (milestoneIndex >= milestoneIndexToLoad && milestoneIndex < closestGreaterMilestone) {
                        closestGreaterMilestone = milestoneIndex;
                        hashToLoad = transactionViewModel.getHash();
                    }
                    if (milestoneIndex == milestoneIndexToLoad) {
                        return transactionViewModel.getHash();
                    }
                }
            }
        }
        return hashToLoad;
    }
}
