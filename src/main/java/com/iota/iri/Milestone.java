package com.iota.iri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

import com.iota.iri.controllers.*;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;

import static com.iota.iri.Milestone.Validity.*;

public class Milestone {

    enum Validity {
        VALID,
        INVALID,
        INCOMPLETE
    }

    private final Logger log = LoggerFactory.getLogger(Milestone.class);
    private final Tangle tangle;
    private final Hash coordinator;
    private final TransactionValidator transactionValidator;
    private final boolean testnet;
    private final MessageQ messageQ;
    public Snapshot latestSnapshot;

    private LedgerValidator ledgerValidator;
    public Hash latestMilestone = Hash.NULL_HASH;
    public Hash latestSolidSubtangleMilestone = latestMilestone;

    public static final int MILESTONE_START_INDEX = 338000;
    private static final int NUMBER_OF_KEYS_IN_A_MILESTONE = 20;

    public int latestMilestoneIndex = MILESTONE_START_INDEX;
    public int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private final Set<Hash> analyzedMilestoneCandidates = new HashSet<>();

    public Milestone(final Tangle tangle,
                     final Hash coordinator,
                     final Snapshot initialSnapshot,
                     final TransactionValidator transactionValidator,
                     final boolean testnet,
                     final MessageQ messageQ
                     ) {
        this.tangle = tangle;
        this.coordinator = coordinator;
        this.latestSnapshot = initialSnapshot;
        this.transactionValidator = transactionValidator;
        this.testnet = testnet;
        this.messageQ = messageQ;
    }

    private boolean shuttingDown;
    private static int RESCAN_INTERVAL = 5000;

    public void init(final SpongeFactory.Mode mode, final LedgerValidator ledgerValidator, final boolean revalidate) throws Exception {
        this.ledgerValidator = ledgerValidator;
        AtomicBoolean ledgerValidatorInitialized = new AtomicBoolean(false);
        (new Thread(() -> {
            while(!ledgerValidatorInitialized.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
            while (!shuttingDown) {
                long scanTime = System.currentTimeMillis();

                try {
                    final int previousLatestMilestoneIndex = latestMilestoneIndex;
                    Set<Hash> hashes = AddressViewModel.load(tangle, coordinator).getHashes();
                    { // Update Milestone
                        { // find new milestones
                            for(Hash hash: hashes) {
                                if(analyzedMilestoneCandidates.add(hash)) {
                                    TransactionViewModel t = TransactionViewModel.fromHash(tangle, hash);
                                    if (t.getCurrentIndex() == 0) {
                                        final Validity valid = validateMilestone(mode, t, getIndex(t));
                                        switch (valid) {
                                            case VALID:
                                                MilestoneViewModel milestoneViewModel = MilestoneViewModel.latest(tangle);
                                                if (milestoneViewModel != null && milestoneViewModel.index() > latestMilestoneIndex) {
                                                    latestMilestone = milestoneViewModel.getHash();
                                                    latestMilestoneIndex = milestoneViewModel.index();
                                                }
                                                break;
                                            case INCOMPLETE:
                                                analyzedMilestoneCandidates.remove(t.getHash());
                                                break;
                                            case INVALID:
                                                //Do nothing
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (previousLatestMilestoneIndex != latestMilestoneIndex) {

                        messageQ.publish("lmi %d %d", previousLatestMilestoneIndex, latestMilestoneIndex);
                        log.info("Latest milestone has changed from #" + previousLatestMilestoneIndex
                                + " to #" + latestMilestoneIndex);
                    }

                    Thread.sleep(Math.max(1, RESCAN_INTERVAL - (System.currentTimeMillis() - scanTime)));

                } catch (final Exception e) {
                    log.error("Error during Latest Milestone updating", e);
                }
            }
        }, "Latest Milestone Tracker")).start();

        (new Thread(() -> {

            try {
                ledgerValidator.init();
                ledgerValidatorInitialized.set(true);
            } catch (Exception e) {
                log.error("Error initializing snapshots. Skipping.", e);
            }
            while (!shuttingDown) {
                long scanTime = System.currentTimeMillis();

                try {
                    final int previousSolidSubtangleLatestMilestoneIndex = latestSolidSubtangleMilestoneIndex;

                    if(latestSolidSubtangleMilestoneIndex < latestMilestoneIndex) {
                        updateLatestSolidSubtangleMilestone();
                    }

                    if (previousSolidSubtangleLatestMilestoneIndex != latestSolidSubtangleMilestoneIndex) {

                        messageQ.publish("lmsi %d %d", previousSolidSubtangleLatestMilestoneIndex, latestSolidSubtangleMilestoneIndex);
                        messageQ.publish("lmhs %s", latestSolidSubtangleMilestone);
                        log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                                + previousSolidSubtangleLatestMilestoneIndex + " to #"
                                + latestSolidSubtangleMilestoneIndex);
                    }

                    Thread.sleep(Math.max(1, RESCAN_INTERVAL - (System.currentTimeMillis() - scanTime)));

                } catch (final Exception e) {
                    log.error("Error during Solid Milestone updating", e);
                }
            }
        }, "Solid Milestone Tracker")).start();


    }

    private Validity validateMilestone(SpongeFactory.Mode mode, TransactionViewModel transactionViewModel, int index) throws Exception {
        if (index < 0 || index >= 0x200000) {
            return INVALID;
        }

        if (MilestoneViewModel.get(tangle, index) != null) {
            // Already validated.
            return VALID;
        }
        final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle, transactionViewModel.getHash());
        if (bundleTransactions.size() == 0) {
            return INCOMPLETE;
        }
        else {
            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {

                //if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(),transactionViewModel.getHash())) {
                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                    //final TransactionViewModel transactionViewModel2 = StorageTransactions.instance().loadTransaction(transactionViewModel.trunkTransactionPointer);
                    final TransactionViewModel transactionViewModel2 = transactionViewModel.getTrunkTransaction(tangle);
                    if (transactionViewModel2.getType() == TransactionViewModel.FILLED_SLOT
                            && transactionViewModel.getBranchTransactionHash().equals(transactionViewModel2.getTrunkTransactionHash())
                            && transactionViewModel.getBundleHash().equals(transactionViewModel2.getBundleHash())) {

                        final int[] trunkTransactionTrits = transactionViewModel.getTrunkTransactionHash().trits();
                        final int[] signatureFragmentTrits = Arrays.copyOfRange(transactionViewModel.trits(), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);

                        final int[] merkleRoot = ISS.getMerkleRoot(mode, ISS.address(mode, ISS.digest(mode,
                                Arrays.copyOf(ISS.normalizedBundle(trunkTransactionTrits),
                                        ISS.NUMBER_OF_FRAGMENT_CHUNKS),
                                signatureFragmentTrits)),
                                transactionViewModel2.trits(), 0, index, NUMBER_OF_KEYS_IN_A_MILESTONE);
                        if (testnet || (new Hash(merkleRoot)).equals(coordinator)) {
                            new MilestoneViewModel(index, transactionViewModel.getHash()).store(tangle);
                            return VALID;
                        } else {
                            return INVALID;
                        }
                    }
                }
            }
        }
        return INVALID;
    }

    void updateLatestSolidSubtangleMilestone() throws Exception {
        MilestoneViewModel milestoneViewModel;
        MilestoneViewModel latest = MilestoneViewModel.latest(tangle);
        if (latest != null) {
            for (milestoneViewModel = MilestoneViewModel.findClosestNextMilestone(tangle, latestSolidSubtangleMilestoneIndex);
                 milestoneViewModel != null && milestoneViewModel.index() <= latest.index() && !shuttingDown;
                 milestoneViewModel = milestoneViewModel.next(tangle)) {
                if (transactionValidator.checkSolidity(milestoneViewModel.getHash(), true) &&
                        milestoneViewModel.index() >= latestSolidSubtangleMilestoneIndex &&
                        ledgerValidator.updateSnapshot(milestoneViewModel)) {
                    latestSolidSubtangleMilestone = milestoneViewModel.getHash();
                    latestSolidSubtangleMilestoneIndex = milestoneViewModel.index();
                } else {
                    break;
                }
            }
        }
    }

    static int getIndex(TransactionViewModel transactionViewModel) {
        return (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET, 15);
    }

    void shutDown() {
        shuttingDown = true;
    }

    public void reportToSlack(final int milestoneIndex, final int depth, final int nextDepth) {

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
