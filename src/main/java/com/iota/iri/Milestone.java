package com.iota.iri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.iota.iri.conf.Configuration;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.service.TipsManager;
import com.iota.iri.service.viewModels.AddressViewModel;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import com.iota.iri.utils.Converter;

public class Milestone {



    public static Hash latestMilestone = Hash.NULL_HASH;
    public static Hash latestSolidSubtangleMilestone = latestMilestone;
    
    public static final int MILESTONE_START_INDEX = 0;

    public static int latestMilestoneIndex = MILESTONE_START_INDEX;
    public static int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private static final Set<Hash> analyzedMilestoneCandidates = new HashSet<>();
    private static final Set<Hash> analyzedMilestoneRetryCandidates = new HashSet<>();
    private static final Map<Integer, Hash> milestones = new ConcurrentHashMap<>();

    private final Hash coordinatorHash;
    private final boolean testnet;

    private Milestone(Hash coordinator, boolean isTestnet) {
        coordinatorHash = coordinator;
        testnet = isTestnet;
    }
    private static Milestone instance = null;

    public static void init(final Hash coordinator, boolean testnet) {
        if(instance == null) {
            instance = new Milestone(coordinator, testnet);
        }
    }
    public static Milestone instance() {
        return instance;
    }

    public static Hash getMilestone(int milestoneIndex) {
        return milestones.get(milestoneIndex);
    }

    public Hash coordinator() {
        return coordinatorHash;
    }
    public void updateLatestMilestone() throws Exception { // refactor

        AddressViewModel coordinator = new AddressViewModel(coordinatorHash);
        for (final Hash hash : coordinator.getTransactionHashes()) {
            if (analyzedMilestoneCandidates.add(hash) || analyzedMilestoneRetryCandidates.remove(hash)) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
                if (transactionViewModel.getCurrentIndex() == 0) {

                    final int index = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET, 15);

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
                                        if(testnet) {
                                            //System.arraycopy(new Hash(hashTrits).bytes(), 0, coordinatorHash.bytes(), 0, coordinatorHash.bytes().length);
                                        }
                                        if (testnet || (new Hash(hashTrits)).equals(coordinatorHash)) {

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
        for (int milestoneIndex = latestSolidSubtangleMilestoneIndex + 1; milestoneIndex <= latestMilestoneIndex; milestoneIndex++) {
            Hash milestone = milestones.get(milestoneIndex);
            if(milestone == null) {
                milestone = findMilestone(milestoneIndex);
            }
            if (milestone != null && TipsManager.checkSolidity(milestone)) {
                latestSolidSubtangleMilestone = milestone;
                latestSolidSubtangleMilestoneIndex = milestoneIndex;
            } else {
                break;
            }
        }
    }

    public static Hash findMilestone(int milestoneIndexToLoad) throws Exception {
        AddressViewModel coordinatorAddress = new AddressViewModel(Milestone.instance.coordinatorHash);
        Hash hashToLoad = getMilestone(milestoneIndexToLoad);
        if(hashToLoad == null) {
            int closestGreaterMilestone = latestMilestoneIndex;
            for (final Hash hash : coordinatorAddress.getTransactionHashes()) {
                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash).getBundle().getTail();
                if(transactionViewModel != null) {
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
