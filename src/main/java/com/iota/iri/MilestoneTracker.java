package com.iota.iri;

import com.iota.iri.conf.ConsensusConfig;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.ISS;
import com.iota.iri.crypto.ISSInPlace;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.zmq.MessageQ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.iota.iri.MilestoneTracker.Validity.INCOMPLETE;
import static com.iota.iri.MilestoneTracker.Validity.INVALID;
import static com.iota.iri.MilestoneTracker.Validity.VALID;

public class MilestoneTracker {
    /**
     * Available runtime states of the {@link MilestoneTracker}.
     */
    public enum Status {
        INITIALIZING,
        INITIALIZED
    }

    enum Validity {
        VALID,
        INVALID,
        INCOMPLETE
    }

    private final Logger log = LoggerFactory.getLogger(MilestoneTracker.class);
    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final Hash coordinator;
    private final TransactionValidator transactionValidator;
    private final boolean testnet;
    private final MessageQ messageQ;
    private final int numOfKeysInMilestone;
    private final boolean acceptAnyTestnetCoo;
    public Snapshot latestSnapshot;

    private LedgerValidator ledgerValidator;
    public Hash latestMilestone = Hash.NULL_HASH;
    public Hash latestSolidSubtangleMilestone = latestMilestone;

    public int latestMilestoneIndex;
    public int latestSolidSubtangleMilestoneIndex;
    public final int milestoneStartIndex;

    private final Set<Hash> analyzedMilestoneCandidates = new HashSet<>();

    /**
     * The current status of the {@link MilestoneTracker}.
     */
    private Status status = Status.INITIALIZING;

    public MilestoneTracker(Tangle tangle,
                            SnapshotProvider snapshotProvider,
                            TransactionValidator transactionValidator,
                            MessageQ messageQ,
                            Snapshot initialSnapshot, ConsensusConfig config
    ) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionValidator = transactionValidator;
        this.messageQ = messageQ;
        this.latestSnapshot = initialSnapshot;

        //configure
        this.testnet = config.isTestnet();
        this.coordinator = HashFactory.ADDRESS.create(config.getCoordinator());
        this.numOfKeysInMilestone = config.getNumberOfKeysInMilestone();
        this.milestoneStartIndex = config.getMilestoneStartIndex();
        this.latestMilestoneIndex = milestoneStartIndex;
        this.latestSolidSubtangleMilestoneIndex = milestoneStartIndex;
        this.acceptAnyTestnetCoo = config.isDontValidateTestnetMilestoneSig();
    }

    /**
     * This method returns the current status of the {@link MilestoneTracker}.
     *
     * It allows us to determine if all of the "startup" tasks have succeeded.
     *
     * @return the current status of the {@link MilestoneTracker}
     */
    public Status getStatus() {
        return this.status;
    }

    private boolean shuttingDown;
    private static final int RESCAN_INTERVAL = 5000;

    public void init(SpongeFactory.Mode mode, int securityLevel, LedgerValidator ledgerValidator) {
        this.ledgerValidator = ledgerValidator;
        AtomicBoolean ledgerValidatorInitialized = new AtomicBoolean(false);
        (new Thread(() -> {
            log.info("Waiting for Ledger Validator initialization...");
            while(!ledgerValidatorInitialized.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
            log.info("Tracker started.");
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
                                        final Validity valid = validateMilestone(mode, securityLevel, t, getIndex(t));
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
            log.info("Initializing Ledger Validator...");
            try {
                ledgerValidator.init();
                ledgerValidatorInitialized.set(true);
            } catch (Exception e) {
                log.error("Error initializing snapshots. Skipping.", e);
            }
            log.info("Tracker started.");
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

    public int getMilestoneStartIndex() {
        return milestoneStartIndex;
    }

    Validity validateMilestone(SpongeFactory.Mode mode, int securityLevel, TransactionViewModel transactionViewModel, int index) throws Exception {
        if (index < 0 || index >= 0x200000) {
            return INVALID;
        }

        if (MilestoneViewModel.get(tangle, index) != null) {
            // Already validated.
            return VALID;
        }
        final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle, snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());
        if (bundleTransactions.size() == 0) {
            return INCOMPLETE;
        }
        else {
            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {

                final TransactionViewModel tail = bundleTransactionViewModels.get(0);
                if (tail.getHash().equals(transactionViewModel.getHash())) {
                    //the signed transaction - which references the confirmed transactions and contains
                    // the Merkle tree siblings.
                    final TransactionViewModel siblingsTx = bundleTransactionViewModels.get(securityLevel);

                    if (isMilestoneBundleStructureValid(bundleTransactionViewModels, securityLevel)) {
                        //milestones sign the normalized hash of the sibling transaction.
                        byte[] signedHash = ISS.normalizedBundle(siblingsTx.getHash().trits());

                        //validate leaf signature
                        ByteBuffer bb = ByteBuffer.allocate(Curl.HASH_LENGTH * securityLevel);
                        byte[] digest = new byte[Curl.HASH_LENGTH];

                        for (int i = 0; i < securityLevel; i++) {
                            ISSInPlace.digest(mode, signedHash, ISS.NUMBER_OF_FRAGMENT_CHUNKS * i,
                                    bundleTransactionViewModels.get(i).getSignature(), 0, digest);
                            bb.put(digest);
                        }

                        byte[] digests = bb.array();
                        byte[] address = ISS.address(mode, digests);

                        //validate Merkle path
                        byte[] merkleRoot = ISS.getMerkleRoot(mode, address,
                                siblingsTx.trits(), 0, index, numOfKeysInMilestone);
                        if ((testnet && acceptAnyTestnetCoo) || (HashFactory.ADDRESS.create(merkleRoot)).equals(coordinator)) {
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
            for (milestoneViewModel = MilestoneViewModel.findClosestNextMilestone(
                    tangle, latestSolidSubtangleMilestoneIndex, latestMilestoneIndex);
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

    private boolean isMilestoneBundleStructureValid(List<TransactionViewModel> bundleTxs, int securityLevel) {
        TransactionViewModel head = bundleTxs.get(securityLevel);
        return bundleTxs.stream()
                .limit(securityLevel)
                .allMatch(tx ->
                        tx.getBranchTransactionHash().equals(head.getTrunkTransactionHash()));
        //trunks of bundles are checked in Bundle validation - no need to check again.
        //bundleHash equality is checked in BundleValidator.validate() (loadTransactionsFromTangle)
    }
}
