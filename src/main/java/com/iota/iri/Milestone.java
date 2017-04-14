package com.iota.iri;

import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.service.TipsManager;
import com.iota.iri.service.viewModels.AddressViewModel;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import com.iota.iri.utils.Converter;

public class Milestone {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

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
            final Hash milestone = findMilestone(milestoneIndex);
            if (!TipsManager.checkSolidity(milestone)) {
                break;
            }
            milestoneIndex = milestones.entrySet().stream()
                    .filter(e -> e.getValue().equals(milestone))
                    .findAny()
                    .orElse(new HashMap.SimpleEntry<Integer, Hash>(milestoneIndex, milestone))
                    .getKey();
            if (milestone != null) {
                latestSolidSubtangleMilestone = milestone;
                latestSolidSubtangleMilestoneIndex = milestoneIndex;
            }
        }
    }

    public static Hash findMilestone(int milestoneIndexToLoad) throws Exception {
        AddressViewModel coordinatorAddress = new AddressViewModel(Milestone.instance.coordinatorHash);
        Hash hashToLoad = getMilestone(milestoneIndexToLoad);
        if(hashToLoad == null) {
            int closestGreaterMilestone = latestMilestoneIndex;
            Hash[] hashes = Arrays.stream(coordinatorAddress.getTransactionHashes()).filter(h -> !milestones.keySet().contains(h)).toArray(Hash[]::new);
            for (final Hash hash : hashes) {
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

    public static void reportToSlack(final int milestoneIndex, final int depth, final int nextDepth) {

        try {

            final String request = "token=" + URLEncoder.encode("<botToken>", "UTF-8") + "&channel=" + URLEncoder.encode("#botbox", "UTF-8") + "&text=" + URLEncoder.encode("TESTNET: ", "UTF-8") + "&as_user=true";

            final HttpURLConnection connection = (HttpsURLConnection) (new URL("https://slack.com/api/chat.postMessage")).openConnection();
            ((HttpsURLConnection)connection).setHostnameVerifier((hostname, session) -> true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            OutputStream out = connection.getOutputStream();
            out.write(request.getBytes("UTF-8"));
            out.close();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            InputStream inputStream = connection.getInputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {

                result.write(buffer, 0, length);
            }
            log.info(result.toString("UTF-8"));

        } catch (final Exception e) {

            e.printStackTrace();
        }
    }
}
