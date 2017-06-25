package com.iota.iri;

import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Milestone {

    private final Logger log = LoggerFactory.getLogger(Milestone.class);
    private final Tangle tangle;
    private final Hash coordinator;
    private final TransactionValidator transactionValidator;
    private final boolean testnet;

    private LedgerValidator ledgerValidator;
    public Hash latestMilestone = Hash.NULL_HASH;
    public Hash latestSolidSubtangleMilestone = latestMilestone;

    public static final int MILESTONE_START_INDEX = 62000;
    private static final int NUMBER_OF_KEYS_IN_A_MILESTONE = 20;

    public int latestMilestoneIndex = MILESTONE_START_INDEX;
    public int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private final Set<Hash> analyzedMilestoneCandidates = new HashSet<>();

    public Milestone(final Tangle tangle,
              final Hash coordinator,
              final TransactionValidator transactionValidator,
              final boolean testnet
              ) {
        this.tangle = tangle;
        this.coordinator = coordinator;
        this.transactionValidator = transactionValidator;
        this.testnet = testnet;
    }

    private boolean shuttingDown;
    private static int RESCAN_INTERVAL = 5000;

    public void init(final LedgerValidator ledgerValidator, final boolean revalidate) {
        this.ledgerValidator = ledgerValidator;
        (new Thread(() -> {
            while (!shuttingDown) {
                long scanTime = System.currentTimeMillis();

                try {
                    final int previousLatestMilestoneIndex = latestMilestoneIndex;

                    updateLatestMilestone();

                    if (previousLatestMilestoneIndex != latestMilestoneIndex) {

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
                ledgerValidator.init(revalidate);
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

    void updateLatestMilestone() throws Exception { // refactor
        findNewMilestones();
        MilestoneViewModel milestoneViewModel = MilestoneViewModel.latest(tangle);
        if(milestoneViewModel != null && milestoneViewModel.index() > latestMilestoneIndex) {
            latestMilestone = milestoneViewModel.getHash();
            latestMilestoneIndex = milestoneViewModel.index();
        }
    }

    private boolean validateMilestone(TransactionViewModel transactionViewModel, int index) throws Exception {

        if (MilestoneViewModel.get(tangle, index) != null) {
            // Already validated.
            return true;
        }
        final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle, transactionViewModel.getBundleHash());
        if (bundleTransactions.size() == 0) {
            return false;
        }
        else {
            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {

                //if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(),transactionViewModel.getHash())) {
                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                    //final TransactionViewModel transactionViewModel2 = StorageTransactions.instance().loadTransaction(transactionViewModel.trunkTransactionPointer);
                    final TransactionViewModel transactionViewModel2 = transactionViewModel.getTrunkTransaction(tangle);
                    if (transactionViewModel2.getType() == TransactionViewModel.FILLED_SLOT
                            && transactionViewModel.getBranchTransactionHash().equals(transactionViewModel2.getTrunkTransactionHash())) {

                        final int[] trunkTransactionTrits = transactionViewModel.getTrunkTransactionHash().trits();
                        final int[] signatureFragmentTrits = Arrays.copyOfRange(transactionViewModel.trits(), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);

                        final int[] merkleRoot = ISS.getMerkleRoot(ISS.address(ISS.digest(
                                Arrays.copyOf(ISS.normalizedBundle(trunkTransactionTrits),
                                        ISS.NUMBER_OF_FRAGMENT_CHUNKS),
                                signatureFragmentTrits)),
                                transactionViewModel2.trits(), 0, index, NUMBER_OF_KEYS_IN_A_MILESTONE);
                        if (testnet || (new Hash(merkleRoot)).equals(coordinator)) {
                            new MilestoneViewModel(index, transactionViewModel.getHash()).store(tangle);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    void updateLatestSolidSubtangleMilestone() throws Exception {
        MilestoneViewModel milestoneViewModel;
        MilestoneViewModel latest = MilestoneViewModel.latest(tangle);
        int lookAhead = 0;
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
                    lookAhead++;
                    if (lookAhead>=10) {
                        break;
                    }
                }
            }
        }
    }

    static int getIndex(TransactionViewModel transactionViewModel) {
        return (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET, 15);
    }

    private void findNewMilestones() throws Exception {
        AddressViewModel.load(tangle, coordinator).getHashes().stream()
                .filter(analyzedMilestoneCandidates::add)
                .map(hash -> TransactionViewModel.quietFromHash(tangle, hash))
                .filter(t -> t.getCurrentIndex() == 0)
                .forEach(t -> {
                    try {
                        if(!validateMilestone(t, getIndex(t))) {
                            analyzedMilestoneCandidates.remove(t.getHash());
                        }
                    } catch (Exception e) {
                        analyzedMilestoneCandidates.remove(t.getHash());
                        log.error("Could not validate milestone: ", t.getHash());
                    }
                });
    }

    void shutDown() {
        shuttingDown = true;
    }

}
