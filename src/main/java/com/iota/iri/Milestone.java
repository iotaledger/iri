package com.iota.iri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.BundleViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionRequester;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.service.TipsManager;
import com.iota.iri.utils.Converter;

public class Milestone {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    public static Hash latestMilestone = Hash.NULL_HASH;
    public static Hash latestSolidSubtangleMilestone = latestMilestone;
    
    public static final int MILESTONE_START_INDEX = 0;
    private static final int NUMBER_OF_KEYS_IN_A_MILESTONE = 20;

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

    private static boolean shuttingDown;
    private static int RESCAN_INTERVAL = 5000;
    private static int RESCAN_TX_TO_REQUEST_INTERVAL = 60000;

    private long nextRescanTxToRequestTime = System.currentTimeMillis() + RESCAN_TX_TO_REQUEST_INTERVAL;
    
    public static void init(final Hash coordinator, boolean testnet) {
        if (instance == null) {
            instance = new Milestone(coordinator, testnet);
        }
    }

    public void init() {
        (new Thread(() -> {

            while (!shuttingDown) {
                long scanTime = System.currentTimeMillis();
    
                if (scanTime > nextRescanTxToRequestTime) {                    
                    try {
                        TransactionRequester.instance().rescanTransactionsToRequest();
                    } catch (ExecutionException | InterruptedException e) {
                        log.error("Could not execute request rescan. ");
                    }
                    nextRescanTxToRequestTime = System.currentTimeMillis() + RESCAN_TX_TO_REQUEST_INTERVAL;
                }
                
                try {
                    final int previousLatestMilestoneIndex = Milestone.latestMilestoneIndex;
                    final int previousSolidSubtangleLatestMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;

                    Milestone.instance().updateLatestMilestone();

                    if (previousLatestMilestoneIndex != Milestone.latestMilestoneIndex) {

                        log.info("Latest milestone has changed from #" + previousLatestMilestoneIndex
                                + " to #" + Milestone.latestMilestoneIndex);
                    }

                    if(Milestone.latestSolidSubtangleMilestoneIndex < Milestone.latestMilestoneIndex) {
                        Milestone.updateLatestSolidSubtangleMilestone();
                    }

                    if (previousSolidSubtangleLatestMilestoneIndex != Milestone.latestSolidSubtangleMilestoneIndex) {
                        LedgerValidator.updateSnapshot(MilestoneViewModel.get(Milestone.latestSolidSubtangleMilestoneIndex));

                        log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                                + previousSolidSubtangleLatestMilestoneIndex + " to #"
                                + Milestone.latestSolidSubtangleMilestoneIndex);
                    }

                    Thread.sleep(RESCAN_INTERVAL);

                } catch (final Exception e) {
                    log.error("Error during TipsManager Milestone updating", e);
                }
            }
        }, "Latest Milestone Tracker")).start();
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

                    if (index > latestMilestoneIndex && validateMilestone(transactionViewModel, index)) {
                        latestMilestone = transactionViewModel.getHash();
                        latestMilestoneIndex = index;
                    }
                }
            }
        }
    }

    private boolean validateMilestone(TransactionViewModel transactionViewModel, int index) throws Exception {
        
        if (milestones.get(index) != null) {
            // Already validated.
            return true;
        }
        final BundleValidator bundleValidator = new BundleValidator(BundleViewModel.fromHash(transactionViewModel.getBundleHash()));
        if (bundleValidator.getTransactions().size() == 0) {
            return false;
        }
        else {
            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleValidator.getTransactions()) {

                //if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(),transactionViewModel.getHash())) {
                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                    //final TransactionViewModel transactionViewModel2 = StorageTransactions.instance().loadTransaction(transactionViewModel.trunkTransactionPointer);
                    final TransactionViewModel transactionViewModel2 = transactionViewModel.getTrunkTransaction();
                    if (transactionViewModel2.getType() == TransactionViewModel.FILLED_SLOT
                            && transactionViewModel.getBranchTransactionHash().equals(transactionViewModel2.getTrunkTransactionHash())) {

                        final int[] trunkTransactionTrits = transactionViewModel.getTrunkTransactionHash().trits();
                        final int[] signatureFragmentTrits = Arrays.copyOfRange(transactionViewModel.trits(), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);

                        final int[] merkleRoot = ISS.getMerkleRoot(ISS.address(ISS.digest(
                                Arrays.copyOf(ISS.normalizedBundle(trunkTransactionTrits),
                                        ISS.NUMBER_OF_FRAGMENT_CHUNKS),
                                signatureFragmentTrits)),
                                transactionViewModel2.trits(), 0, index, NUMBER_OF_KEYS_IN_A_MILESTONE);
                        if (testnet || (new Hash(merkleRoot)).equals(coordinatorHash)) {
                            milestones.put(index, transactionViewModel.getHash());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void updateLatestSolidSubtangleMilestone() throws Exception {
        for (int milestoneIndex = latestSolidSubtangleMilestoneIndex; milestoneIndex++ < latestMilestoneIndex;) {
            final Map.Entry<Integer, Hash> milestone = findMilestone(milestoneIndex);
            if (milestone.getKey() <= 0) {
                log.info("Could not find milestone greater than or equal to {}", milestoneIndex);
                break;
            }
            milestoneIndex = milestone.getKey();
            if (TransactionRequester.instance().checkSolidity(milestone.getValue(), true)) {
                latestSolidSubtangleMilestone = milestone.getValue();
                latestSolidSubtangleMilestoneIndex = milestoneIndex;
                new MilestoneViewModel(milestoneIndex, milestone.getValue()).store();
            }
        }
    }

    private static int getIndex(TransactionViewModel transactionViewModel) {
        return (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET, 15);
    }

    public static Map.Entry<Integer, Hash> findMilestone(int milestoneIndexToLoad) throws Exception {
        Map.Entry<Integer, Hash> output;
        AddressViewModel coordinatorAddress = new AddressViewModel(Milestone.instance.coordinatorHash);
        Hash hashToLoad = getMilestone(milestoneIndexToLoad);
        int index;
        if(hashToLoad == null) {
            Arrays.stream(coordinatorAddress.getTransactionHashes())
                    .parallel()
                    .map(TransactionViewModel::quietFromHash)
                    .forEach(t -> {
                        try {
                            Milestone.instance().validateMilestone(t, getIndex(t));
                        } catch (Exception e) {
                            log.error("Could not validate milestone. {}", t.getHash());
                        }
                    });
            index = milestones.keySet()
                            .stream()
                            .filter(e -> e.compareTo(milestoneIndexToLoad ) >= 0)
                            .sorted()
                            .findFirst()
                            .orElse(-1);
            output = new AbstractMap.SimpleEntry<>(index, index != -1 ? milestones.get(index) : Hash.NULL_HASH);
        } else {
            output = new AbstractMap.SimpleEntry<>(milestoneIndexToLoad, hashToLoad);
        }
        return output;
    }

    public void shutDown() {
        shuttingDown = true;
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
