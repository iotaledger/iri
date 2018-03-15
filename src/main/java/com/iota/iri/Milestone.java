package com.iota.iri;

import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.ISS;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.zmq.MessageQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.iota.iri.Milestone.Validity.*;

public class Milestone {

    enum Validity {
        VALID,
        INVALID,
        INCOMPLETE
    }

    public static final int MILESTONE_START_INDEX = 338000;
    private static final int NUMBER_OF_KEYS_IN_A_MILESTONE = 20;
    private static final int RESCAN_INTERVAL = 5000;

    private final Logger log = LoggerFactory.getLogger(Milestone.class);
    private final Set<Hash> analyzedMilestoneCandidates = new HashSet<>();

    private final Tangle tangle;
    private final Hash coordinator;
    private final TransactionValidator transactionValidator;
    private final boolean testnet;
    private final MessageQ messageQ;

    private int latestMilestoneIndex = MILESTONE_START_INDEX;
    private int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private Snapshot latestSnapshot;
    private LedgerValidator ledgerValidator;

    private Hash latestMilestone = Hash.NULL_HASH;
    private Hash latestSolidSubtangleMilestone = getLatestMilestone();

    private volatile boolean shuttingDown;

    public Milestone(final Tangle tangle,
                     final Hash coordinator,
                     final Snapshot initialSnapshot,
                     final TransactionValidator transactionValidator,
                     final boolean testnet,
                     final MessageQ messageQ
    ) {
        this.tangle = tangle;
        this.coordinator = coordinator;
        this.setLatestSnapshot(initialSnapshot);
        this.transactionValidator = transactionValidator;
        this.testnet = testnet;
        this.messageQ = messageQ;
    }


    public Hash getLatestMilestone() {
        return latestMilestone;
    }

    private void setLatestMilestone(Hash latestMilestone) {
        this.latestMilestone = latestMilestone;
    }

    public Hash getLatestSolidSubtangleMilestone() {
        return latestSolidSubtangleMilestone;
    }

    public void setLatestSolidSubtangleMilestone(Hash latestSolidSubtangleMilestone) {
        this.latestSolidSubtangleMilestone = latestSolidSubtangleMilestone;
    }

    public Snapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    private void setLatestSnapshot(Snapshot latestSnapshot) {
        this.latestSnapshot = latestSnapshot;
    }

    public int getLatestMilestoneIndex() {
        return latestMilestoneIndex;
    }

    private void setLatestMilestoneIndex(int latestMilestoneIndex) {
        this.latestMilestoneIndex = latestMilestoneIndex;
    }

    public int getLatestSolidSubtangleMilestoneIndex() {
        return latestSolidSubtangleMilestoneIndex;
    }

    public void setLatestSolidSubtangleMilestoneIndex(int latestSolidSubtangleMilestoneIndex) {
        this.latestSolidSubtangleMilestoneIndex = latestSolidSubtangleMilestoneIndex;
    }

    private void updateLatestSolidSubtangleMilestone() throws Exception {
        MilestoneViewModel latest = MilestoneViewModel.latest(tangle);
        if (latest == null) {
            log.info("MilestoneViewModel.latest == null");
            return;
        }

        MilestoneViewModel milestoneViewModel
                = MilestoneViewModel.findClosestNextMilestone(tangle, getLatestSolidSubtangleMilestoneIndex());

        while (!shuttingDown && milestoneViewModel != null) {
            if (milestoneViewModel.index() > latest.index()) {
                break;
            }
            if (!transactionValidator.checkSolidity(milestoneViewModel.getHash(), true)) {
                break;
            }
            if (milestoneViewModel.index() < getLatestSolidSubtangleMilestoneIndex()) {
                break;
            }
            if (!ledgerValidator.updateSnapshot(milestoneViewModel)) {
                break;
            }

            setLatestSolidSubtangleMilestone(milestoneViewModel.getHash());
            setLatestSolidSubtangleMilestoneIndex(milestoneViewModel.index());

            milestoneViewModel = milestoneViewModel.next(tangle);
        }
    }

    private void solidMilestoneUpdating() throws Exception {
        // part 1 - SOLID MILESTONE UPDATING
        final int previousSolidSubtangleLatestMilestoneIndex = getLatestSolidSubtangleMilestoneIndex();

        if (previousSolidSubtangleLatestMilestoneIndex < getLatestMilestoneIndex()) {
            updateLatestSolidSubtangleMilestone();
        }

        // see if changed in update
        if (previousSolidSubtangleLatestMilestoneIndex != getLatestSolidSubtangleMilestoneIndex()) {

            messageQ.publish("lmsi %d %d", previousSolidSubtangleLatestMilestoneIndex, getLatestSolidSubtangleMilestoneIndex());
            messageQ.publish("lmhs %s", getLatestSolidSubtangleMilestone());
            log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                    + previousSolidSubtangleLatestMilestoneIndex + " to #"
                    + getLatestSolidSubtangleMilestoneIndex());
        }
    }


    private void latestMilestoneUpdating(SpongeFactory.Mode mode) throws Exception {
        // part 2 - LATEST MILESTONE UPDATING
        final int previousLatestMilestoneIndex = getLatestMilestoneIndex();
        Set<Hash> hashes = AddressViewModel.load(tangle, coordinator).getHashes();

        // Update Milestone
        // findFirst new milestones
        for (Hash hash : hashes) {
            if (analyzedMilestoneCandidates.add(hash)) {
                TransactionViewModel tx = TransactionViewModel.fromHash(tangle, hash);

                if (tx.getCurrentIndex() == 0) {
                    switch (validateMilestone(mode, tx, getIndex(tx))) {
                        case VALID:
                            MilestoneViewModel mvm = MilestoneViewModel.latest(tangle);
                            if (mvm != null) {
                                if (mvm.index() > getLatestMilestoneIndex()) {
                                    setLatestMilestone(mvm.getHash());
                                    setLatestMilestoneIndex(mvm.index());
                                }
                            }
                            break;

                        case INCOMPLETE:
                            analyzedMilestoneCandidates.remove(hash);
                            break;

                        case INVALID:
                            log.info("for({}) invalid ... do nothing", hash.hashCode());
                            break;
                    }
                }
            }
        }

        if (previousLatestMilestoneIndex != getLatestMilestoneIndex()) {
            messageQ.publish("lmi %d %d", previousLatestMilestoneIndex, getLatestMilestoneIndex());
            log.info("Latest milestone has changed from #" + previousLatestMilestoneIndex
                    + " to #" + getLatestMilestoneIndex());
        }
    }

    public void init(final SpongeFactory.Mode mode, final LedgerValidator ledgerValidator, final boolean revalidate) throws Exception {
        this.ledgerValidator = ledgerValidator;

        if (revalidate) {
            RuntimeException exc = new RuntimeException("Revalidate feature not implemented yet.");
            log.error("Revalidate feature not implemented yet.", exc);
        }

        Thread thread = new Thread(() -> {
            try {
                ledgerValidator.init();
                log.info("ledger validator initialized");
            } catch (Exception e) {
                log.error("Error initializing snapshots. Skipping.", e);
            }

            int count = 0;
            while (!shuttingDown) {
                count++;
                long scanTime = System.currentTimeMillis();

                try {
                    solidMilestoneUpdating();
                } catch (final Exception e) {
                    log.error("Error during Solid Milestone updating", e);
                }

                try {
                    latestMilestoneUpdating(mode);
                } catch (final Exception e) {
                    log.error("Error during Latest Milestone updating", e);
                }


                long remainder = RESCAN_INTERVAL - (System.currentTimeMillis() - scanTime);

                // we sample the time it takes to run every 10 cycles
                // we only report the time if it is more than 1/2 the RESCAN interval
                if (((count % 10) == 0) && (remainder <= ((long) RESCAN_INTERVAL / 2))) {
                    log.info("Sleep remainder: {} ms.", remainder);
                }

                if (remainder > 1) {
                    try {
                        Thread.sleep(remainder);
                    } catch (InterruptedException e) {
                        log.error("Error during Milestone updating (sleep)", e);
                    }
                }
            }
        }, "Milestone Tracker - Solid and Latest ");

        thread.setDaemon(true);
        thread.start();
    }


    private Validity validateMilestone(SpongeFactory.Mode mode, TransactionViewModel tvm, int index) throws
            Exception {

        if (index < 0 || index >= 0x200000) {
            return INVALID;
        }

        if (MilestoneViewModel.get(tangle, index) != null) {
            // Already validated.
            return VALID;
        }

        final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle, tvm.getHash());
        if (bundleTransactions.size() == 0) {
            return INCOMPLETE;
        }

        for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {

            if (bundleTransactionViewModels.get(0).getHash().equals(tvm.getHash())) {

                final TransactionViewModel tvm2 = tvm.getTrunkTransaction(tangle);
                if (tvm2.getType() == TransactionViewModel.FILLED_SLOT
                        && tvm.getBranchTransactionHash().equals(tvm2.getTrunkTransactionHash())
                        && tvm.getBundleHash().equals(tvm2.getBundleHash())) {

                    int[] trunkTransactionTrits = tvm.getTrunkTransactionHash().trits();
                    int[] normalizedBundleFragmentCopy = Arrays.copyOf(ISS.normalizedBundle(trunkTransactionTrits), ISS.NUMBER_OF_FRAGMENT_CHUNKS);

                    int[] signatureFragmentTrits = Arrays.copyOfRange(tvm.trits(), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
                    int[] issAddress = ISS.address(mode, ISS.digest(mode, normalizedBundleFragmentCopy, signatureFragmentTrits));

                    int[] merkleRoot = ISS.getMerkleRoot(mode, issAddress, tvm2.trits(), 0, index, NUMBER_OF_KEYS_IN_A_MILESTONE);

                    if (testnet || (new Hash(merkleRoot)).equals(coordinator)) {
                        new MilestoneViewModel(index, tvm.getHash()).store(tangle);
                        return VALID;
                    } else {
                        return INVALID;
                    }
                }
            }
        }
        return INVALID;
    }


    private static int getIndex(TransactionViewModel transactionViewModel) {
        return (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET, 15);
    }

    void shutDown() {
        shuttingDown = true;
    }
}
