package com.iota.iri;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.service.viewModels.AddressViewModel;
import com.iota.iri.service.ScratchpadViewModel;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.utils.Converter;

public class Milestone {

    public static final Hash COORDINATOR = new Hash("KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU");

    public static BigInteger latestMilestone = TransactionViewModel.PADDED_NULL_HASH;//BigInteger.ZERO;//
    public static BigInteger latestSolidSubtangleMilestone = latestMilestone;
    
    public static final int MILESTONE_START_INDEX = 10005;

    public static int latestMilestoneIndex = MILESTONE_START_INDEX;
    public static int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private static final Set<BigInteger> analyzedMilestoneCandidates = new HashSet<>();
    private static final Set<BigInteger> analyzedMilestoneRetryCandidates = new HashSet<>();
    private static final Map<Integer, BigInteger> milestones = new ConcurrentHashMap<>();

    public static BigInteger getMilestone(int milestoneIndex) {
        return milestones.get(milestoneIndex);
    }
    
    public static void updateLatestMilestone() throws Exception { // refactor

        final long now = System.currentTimeMillis() / 1000L;

        AddressViewModel coordinator = new AddressViewModel(COORDINATOR);
        for (final BigInteger hash : coordinator.getTransactionHashes()) {
            if (analyzedMilestoneCandidates.add(hash) || analyzedMilestoneRetryCandidates.remove(hash)) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
                if (transactionViewModel.getCurrentIndex() == 0) {

                    final int index = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET, 15);
                    final long timestamp = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TIMESTAMP_TRINARY_OFFSET, 27);

                    //if ((now - timestamp) < 7200L && index > latestMilestoneIndex) {
                    if (index > latestMilestoneIndex) {

                        final BundleViewModel bundle = BundleViewModel.fromHash(transactionViewModel.getBundleHash());
                        if (bundle.getTransactions().size() == 0) {
							// Bundle not available, try again later.
                            analyzedMilestoneRetryCandidates.add(hash);
                        }
                        else {
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundle.getTransactions()) {

                                //if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(),transactionViewModel.getHash())) {
                                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                                    //final TransactionViewModel transactionViewModel2 = StorageTransactions.instance().loadTransaction(transactionViewModel.trunkTransactionPointer);
                                    final TransactionViewModel transactionViewModel2 = transactionViewModel.getTrunkTransaction();
                                    if (transactionViewModel2.getType() == AbstractStorage.FILLED_SLOT
                                            && transactionViewModel.getBranchTransactionPointer().equals(transactionViewModel2.getTrunkTransactionPointer())) {

                                        final int[] trunkTransactionTrits = new int[TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE];
                                        Converter.getTrits(Hash.padHash(transactionViewModel.getTrunkTransactionPointer()), trunkTransactionTrits);
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

            final BigInteger milestone = milestones.get(milestoneIndex);
            if (milestone != null) {

                boolean solid = true;


                	ScratchpadViewModel.instance().clearAnalyzedTransactionsFlags();

                	ScratchpadViewModel.instance().setAnalyzedTransactionFlag(TransactionViewModel.PADDED_NULL_HASH);
                    final Queue<BigInteger> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(milestone));
                    BigInteger hashPointer, trunkInteger, branchInteger;
                    while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
                        if (ScratchpadViewModel.instance().setAnalyzedTransactionFlag(hashPointer)) {
                            final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashPointer);
                            if (transactionViewModel2.getType() == AbstractStorage.PREFILLED_SLOT && !hashPointer.equals(TransactionViewModel.PADDED_NULL_HASH)) {
                                ScratchpadViewModel.instance().requestTransaction(hashPointer);
                                solid = false;
                                break;

                            } else {
                                trunkInteger = transactionViewModel2.getTrunkTransactionPointer();
                                branchInteger = transactionViewModel2.getBranchTransactionPointer();
                                nonAnalyzedTransactions.offer(trunkInteger);
                                nonAnalyzedTransactions.offer(branchInteger);
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
